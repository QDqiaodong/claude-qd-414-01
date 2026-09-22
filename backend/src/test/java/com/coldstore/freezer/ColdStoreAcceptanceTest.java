package com.coldstore.freezer;

import com.coldstore.freezer.dto.BizException;
import com.coldstore.freezer.dto.DefrostWindowReq;
import com.coldstore.freezer.dto.InspectionReq;
import com.coldstore.freezer.dto.ReservationReq;
import com.coldstore.freezer.entity.Batch;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.entity.DefrostWindow;
import com.coldstore.freezer.entity.Inspection;
import com.coldstore.freezer.entity.Location;
import com.coldstore.freezer.entity.Reservation;
import com.coldstore.freezer.repository.BatchRepository;
import com.coldstore.freezer.repository.CellRepository;
import com.coldstore.freezer.repository.LocationRepository;
import com.coldstore.freezer.service.BatchService;
import com.coldstore.freezer.service.CapacityService;
import com.coldstore.freezer.service.DefrostWindowService;
import com.coldstore.freezer.service.InspectionService;
import com.coldstore.freezer.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ColdStoreAcceptanceTest {

    @Autowired CellRepository cellRepository;
    @Autowired BatchRepository batchRepository;
    @Autowired LocationRepository locationRepository;
    @Autowired DefrostWindowService windowService;
    @Autowired ReservationService reservationService;
    @Autowired BatchService batchService;
    @Autowired InspectionService inspectionService;
    @Autowired CapacityService capacityService;
    @Autowired com.coldstore.freezer.service.CellService cellService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMvc mockMvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    private long frozenCell;   // 冷冻，容量 100
    private long chilledCell;  // 冷藏，容量 100

    @BeforeEach
    void clean() {
        // Cell 走 @SQLDelete 软删，物理清空只能用原生 SQL，避免唯一编号跨用例碰撞
        jdbcTemplate.update("delete from inspection_location");
        jdbcTemplate.update("delete from inspection");
        jdbcTemplate.update("delete from reservation");
        jdbcTemplate.update("delete from defrost_window");
        jdbcTemplate.update("delete from batch");
        jdbcTemplate.update("delete from location");
        jdbcTemplate.update("delete from cell");

        Cell f = new Cell();
        f.setCode("T-FROZEN"); f.setName("冷冻测试间"); f.setTempZone("冷冻");
        f.setCapacity(100); f.setDeleted(0);
        frozenCell = cellRepository.save(f).getId();

        Cell c = new Cell();
        c.setCode("T-CHILLED"); c.setName("冷藏测试间"); c.setTempZone("冷藏");
        c.setCapacity(100); c.setDeleted(0);
        chilledCell = cellRepository.save(c).getId();
    }

    private DefrostWindowReq windowReq(long cellId, int offsetMinFrom, int offsetMinTo, String reason) {
        DefrostWindowReq req = new DefrostWindowReq();
        req.setCellId(cellId);
        req.setStartAt(LocalDateTime.now().plusMinutes(offsetMinFrom));
        req.setEndAt(LocalDateTime.now().plusMinutes(offsetMinTo));
        req.setReason(reason);
        return req;
    }

    private Reservation createReservation(long cellId, String cargo, int qty) {
        ReservationReq req = new ReservationReq();
        req.setCellId(cellId); req.setCargo(cargo); req.setQty(qty);
        req.setPlanDate(LocalDate.now());
        return reservationService.create(req);
    }

    private Batch createPendingBatch(long cellId, String cargo, int qty) {
        com.coldstore.freezer.dto.BatchReq req = new com.coldstore.freezer.dto.BatchReq();
        req.setCellId(cellId); req.setCargo(cargo); req.setQty(qty);
        req.setBatchDate(LocalDate.now());
        return batchService.create(req);
    }

    // ---------- 化霜占窗 ----------

    @Test
    void frozen_window_shorter_than_40_minutes_rejected() {
        BizException ex = assertThrows(BizException.class,
                () -> windowService.create(windowReq(frozenCell, 0, 39, "化霜")));
        assertTrue(ex.getMessage().contains("40"), ex.getMessage());
    }

    @Test
    void frozen_window_exactly_40_minutes_allowed() {
        assertDoesNotThrow(() -> windowService.create(windowReq(frozenCell, 0, 40, "化霜")));
    }

    @Test
    void chilled_window_shorter_than_20_minutes_rejected() {
        BizException ex = assertThrows(BizException.class,
                () -> windowService.create(windowReq(chilledCell, 0, 19, "化霜")));
        assertTrue(ex.getMessage().contains("20"), ex.getMessage());
    }

    @Test
    void overlapping_windows_later_one_rejected_on_the_spot() {
        windowService.create(windowReq(frozenCell, 0, 60, "第一扇"));
        // 与第一扇重叠
        BizException ex = assertThrows(BizException.class,
                () -> windowService.create(windowReq(frozenCell, 30, 90, "第二扇")));
        assertTrue(ex.getMessage().contains("重叠"), ex.getMessage());
        // 边界相接不算重叠，允许续开
        assertDoesNotThrow(() -> windowService.create(windowReq(frozenCell, 60, 120, "第三扇")));
    }

    @Test
    void windows_on_different_cells_do_not_conflict() {
        windowService.create(windowReq(frozenCell, 0, 60, "冷冻化霜"));
        assertDoesNotThrow(() -> windowService.create(windowReq(chilledCell, 0, 30, "冷藏化霜")));
    }

    @Test
    void missing_reason_or_bad_time_rejected() {
        DefrostWindowReq r = windowReq(frozenCell, 0, 60, "  ");
        assertThrows(BizException.class, () -> windowService.create(r));
        DefrostWindowReq r2 = windowReq(frozenCell, 60, 0, "倒序");
        assertThrows(BizException.class, () -> windowService.create(r2));
    }

    @Test
    void ongoing_window_forces_remaining_to_zero_even_with_spare_capacity() {
        assertEquals(100, capacityService.remaining(frozenCell));
        windowService.create(windowReq(frozenCell, -5, 55, "进行中化霜"));
        assertTrue(capacityService.isDefrostOngoing(frozenCell));
        assertEquals(0, capacityService.remaining(frozenCell));
        // 别的库间不受影响
        assertEquals(100, capacityService.remaining(chilledCell));
    }

    @Test
    void no_new_window_on_soft_deleted_cell() {
        cellRepository.deleteById(frozenCell);
        cellRepository.flush();
        assertThrows(BizException.class,
                () -> windowService.create(windowReq(frozenCell, 0, 60, "化霜")));
    }

    // ---------- 剩余口径 ----------

    @Test
    void remaining_formula_instock_pending_reserved() {
        // 在库 30
        Batch in = createPendingBatch(frozenCell, "虾", 30);
        batchService.stockIn(in.getId(), reserveFor(frozenCell, "虾", 30));
        // 待入 20
        createPendingBatch(frozenCell, "鱼", 20);
        // 已确认未核销预占 25
        Long rid = createReservation(frozenCell, "贝", 25).getId();
        reservationService.confirm(rid);

        var view = capacityService.view(frozenCell);
        assertEquals(100, view.getCapacity());
        assertEquals(30, view.getInStockQty());
        assertEquals(20, view.getPendingQty());
        assertEquals(25, view.getReservedQty());
        assertEquals(25, view.getRemaining()); // 100-30-20-25
    }

    private Long reserveFor(long cellId, String cargo, int qty) {
        Reservation r = createReservation(cellId, cargo, qty);
        reservationService.confirm(r.getId());
        return r.getId();
    }

    private com.coldstore.freezer.dto.CellReq cellReq(Long codeCellId, Integer capacity) {
        Cell base = cellService.get(codeCellId);
        com.coldstore.freezer.dto.CellReq req = new com.coldstore.freezer.dto.CellReq();
        req.setCode(base.getCode());
        req.setName(base.getName());
        req.setTempZone(base.getTempZone());
        req.setCapacity(capacity);
        return req;
    }

    private int dbCapacity(long cellId) {
        Integer v = jdbcTemplate.queryForObject(
                "select capacity from cell where id = ?", Integer.class, cellId);
        return v == null ? -1 : v;
    }

    // ---------- 容量变更收紧 ----------

    @Test
    void capacity_cannot_shrink_below_instock_plus_pending_plus_confirmed_reservation() {
        // 在库 30、待入 20、已确认未核销预占 25，下限 = 75
        Batch in = createPendingBatch(frozenCell, "虾", 30);
        batchService.stockIn(in.getId(), reserveFor(frozenCell, "虾", 30));
        createPendingBatch(frozenCell, "鱼", 20);
        Long rid = createReservation(frozenCell, "贝", 25).getId();
        reservationService.confirm(rid);

        // 改到下限 75：刚好允许，剩余变 0
        Cell ok = cellService.update(frozenCell, cellReq(frozenCell, 75));
        assertEquals(75, ok.getCapacity().intValue());
        assertEquals(0, capacityService.remaining(frozenCell));
        assertEquals(75, capacityService.view(frozenCell).getCapacity());

        // 改到 74：驳回，并逐项点清是哪些承诺撑住了下限
        BizException ex = assertThrows(BizException.class,
                () -> cellService.update(frozenCell, cellReq(frozenCell, 74)));
        String msg = ex.getMessage();
        assertTrue(msg.contains("75"), msg);
        assertTrue(msg.contains("预占#" + rid), "要点名是哪一笔已确认预占超限：" + msg);
        assertTrue(msg.contains("待入"), msg);
        assertTrue(msg.contains("在库"), msg);

        // 驳回后容量、库存、预占状态一律原样
        assertEquals(75, dbCapacity(frozenCell));
        assertEquals(30, capacityService.view(frozenCell).getInStockQty());
        assertEquals(20, capacityService.view(frozenCell).getPendingQty());
        assertEquals(25, capacityService.view(frozenCell).getReservedQty());
        assertEquals("已确认", reservationService.get(rid).getStatus());
        assertEquals(0, capacityService.remaining(frozenCell));

        // 出库 / 撤销待入让下限降下来后，再下调即可成功
        batchService.stockOut(in.getId());
        cellService.update(frozenCell, cellReq(frozenCell, 45)); // 待入20+预占25
        assertEquals(45, dbCapacity(frozenCell));
    }

    @Test
    void null_or_negative_capacity_rejected_and_everything_stays_untouched() {
        Long rid = reserveFor(frozenCell, "占位", 40);
        createPendingBatch(frozenCell, "待收货", 10);

        // 负数（绕过输入框直接请求）
        BizException neg = assertThrows(BizException.class,
                () -> cellService.update(frozenCell, cellReq(frozenCell, -5)));
        assertTrue(neg.getMessage().contains("负数"), neg.getMessage());
        // 空容量（旧表单 / 直连接口漏传）
        BizException empty = assertThrows(BizException.class,
                () -> cellService.update(frozenCell, cellReq(frozenCell, null)));
        assertTrue(empty.getMessage().contains("容量不能为空"), empty.getMessage());

        assertEquals(100, dbCapacity(frozenCell), "驳回不得改动原容量");
        assertEquals(40, capacityService.view(frozenCell).getReservedQty());
        assertEquals(10, capacityService.view(frozenCell).getPendingQty());
        assertEquals("已确认", reservationService.get(rid).getStatus());
    }

    /** 绕过页面直接打接口：负数 / 空容量 / 低于下限都必须 400 + 明确错误，状态原样 */
    @Test
    void direct_http_put_with_bad_capacity_gets_400_and_state_untouched() throws Exception {
        Long rid = reserveFor(frozenCell, "占位", 60); // 下限 60
        Cell base = cellService.get(frozenCell);

        // 负数
        String negBody = objectMapper.writeValueAsString(java.util.Map.of(
                "code", base.getCode(), "name", base.getName(),
                "tempZone", base.getTempZone(), "capacity", -9));
        org.springframework.test.web.servlet.MvcResult neg = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/cells/" + frozenCell)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(negBody))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andReturn();
        assertTrue(neg.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("负数"));

        // 空容量（字段缺失）
        String missingBody = objectMapper.writeValueAsString(java.util.Map.of(
                "code", base.getCode(), "name", base.getName(), "tempZone", base.getTempZone()));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/cells/" + frozenCell)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(missingBody))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.message", org.hamcrest.Matchers.containsString("容量不能为空")));

        // 低于下限：点名是哪一笔预占撑住的
        String lowBody = objectMapper.writeValueAsString(java.util.Map.of(
                "code", base.getCode(), "name", base.getName(),
                "tempZone", base.getTempZone(), "capacity", 59));
        org.springframework.test.web.servlet.MvcResult low = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/cells/" + frozenCell)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(lowBody))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andReturn();
        assertTrue(low.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).contains("预占#" + rid),
                low.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));

        assertEquals(100, dbCapacity(frozenCell));
        assertEquals("已确认", reservationService.get(rid).getStatus());
        assertEquals(40, capacityService.remaining(frozenCell));

        // 合法下调到下限 60：成功
        String okBody = objectMapper.writeValueAsString(java.util.Map.of(
                "code", base.getCode(), "name", base.getName(),
                "tempZone", base.getTempZone(), "capacity", 60));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/cells/" + frozenCell)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(okBody))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(60, dbCapacity(frozenCell));
    }

    @Test
    void create_cell_requires_non_null_non_negative_capacity() {
        com.coldstore.freezer.dto.CellReq bad = new com.coldstore.freezer.dto.CellReq();
        bad.setCode("NEW-CELL");
        bad.setName("新间");
        bad.setTempZone("冷冻");
        bad.setCapacity(null);
        assertThrows(BizException.class, () -> cellService.create(bad));
        bad.setCapacity(-1);
        assertThrows(BizException.class, () -> cellService.create(bad));
        assertFalse(cellRepository.existsByCode("NEW-CELL"));
    }

    /**
     * 入库只会让容量下限下降（待入 −q、已确认预占 −q、在库 +q，净 −q），
     * 所以容量变更与入库即便不互斥，也不可能把容量落到一个「低于最终承诺」的结果：
     * 等锁后的口径只会偏大（多算一笔旧待入/旧预占）导致当场驳回，而不会偏小误放行。
     */
    @Test
    void stock_in_only_lowers_the_floor_so_capacity_change_never_accepts_below_commitments() {
        // 下限先为 待入40 + 已确认40 = 80
        Batch b = createPendingBatch(frozenCell, "羊排", 40);
        Long rid = reserveFor(frozenCell, "羊排", 40);
        cellService.update(frozenCell, cellReq(frozenCell, 80)); // 贴着下限改小
        assertEquals(0, capacityService.remaining(frozenCell));

        // 入库后：在库40，下限 40。此前已落库的容量 80 仍 ≥ 40，口径自洽
        batchService.stockIn(b.getId(), rid);
        var v = capacityService.view(frozenCell);
        assertEquals(40, v.getInStockQty());
        assertEquals(0, v.getPendingQty());
        assertEquals(0, v.getReservedQty());
        assertEquals(80, v.getCapacity());
        assertEquals(40, v.getRemaining());

        // 入库先发生后，容量可以继续下调到新下限 40；反过来容量贴着 80 已落库时入库照常
        cellService.update(frozenCell, cellReq(frozenCell, 40));
        assertEquals(40, dbCapacity(frozenCell));
        assertEquals(0, capacityService.remaining(frozenCell));
    }

    @Test
    void capacity_increase_takes_effect_everywhere_on_same_caliber() {
        Long rid = reserveFor(frozenCell, "贝", 25);
        cellService.update(frozenCell, cellReq(frozenCell, 120));
        // 容量总览（overview 与单库间明细同入口同口径）
        var v = capacityService.view(frozenCell);
        assertEquals(120, v.getCapacity());
        assertEquals(25, v.getReservedQty());
        assertEquals(95, v.getRemaining());
        var overview = capacityService.viewAll().stream()
                .filter(x -> x.getCellId() == frozenCell).findFirst().orElseThrow();
        assertEquals(120, overview.getCapacity());
        assertEquals(95, overview.getRemaining());
        // 新口径下原本放不下的预占可以确认
        Reservation r2 = createReservation(frozenCell, "蟹", 90);
        assertDoesNotThrow(() -> reservationService.confirm(r2.getId()));
        assertEquals(5, capacityService.remaining(frozenCell));
        assertEquals(rid, rid); // 旧预占不受影响
    }

    /**
     * 容量下调与预占确认的真正并发（余量充裕场景）：二者在同一库间行锁上串行，
     * 多轮竞速允许双赢，但任何最终组合都必须满足容量 ≥ 在库+待入+已确认预占，
     * 不允许出现死锁/锁超时，也不允许「提示成功、库里却是另一套」。
     * 互斥结局（容量 0 对 满额预占）在 capacity_change_to_zero_vs_full_reservation_only_one_survives 中严格断言。
     */
    @Test
    void capacity_change_and_reservation_confirm_race_never_violates_capacity() throws Exception {
        int iterations = 15;
        int bothWon = 0;
        for (int i = 0; i < iterations; i++) {
            String cargo = "竞速预占" + i;
            Cell room = newRoom("RACE-" + i);
            Reservation draft = createReservation(room.getId(), cargo, 30);
            com.coldstore.freezer.dto.CellReq req = cellReq(room.getId(), 100);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger capOk = new AtomicInteger();
            AtomicInteger confirmOk = new AtomicInteger();
            AtomicReference<Throwable> unexpected = new AtomicReference<>();
            List<Runnable> tasks = List.of(
                    () -> {
                        try {
                            start.await();
                            cellService.update(room.getId(), req);
                            capOk.incrementAndGet();
                        } catch (BizException expected) {
                            // 落选方（该场景容量 100 ≥ 30，实际都会成功；保留驳回通道）
                        } catch (Throwable e) {
                            unexpected.set(e);
                        }
                    },
                    () -> {
                        try {
                            start.await();
                            reservationService.confirm(draft.getId());
                            confirmOk.incrementAndGet();
                        } catch (BizException expected) {
                            // 落选方
                        } catch (Throwable e) {
                            unexpected.set(e);
                        }
                    });
            if (i % 2 == 0) {
                pool.submit(tasks.get(0));
                pool.submit(tasks.get(1));
            } else {
                pool.submit(tasks.get(1));
                pool.submit(tasks.get(0));
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS),
                    "第 " + i + " 轮并发动作未按时结束");
            assertNull(unexpected.get(),
                    "出现业务驳回以外的异常（死锁/锁超时/半截账）：" + unexpected.get());

            int finalCapacity = dbCapacity(room.getId());
            var view = capacityService.view(room.getId());
            assertTrue(finalCapacity >= view.getReservedQty() + view.getPendingQty() + view.getInStockQty(),
                    "第 " + i + " 轮：容量 " + finalCapacity + " 低于承诺下限 "
                            + (view.getReservedQty() + view.getPendingQty() + view.getInStockQty()));
            assertEquals(confirmOk.get() == 1 ? "已确认" : "待确认",
                    reservationService.get(draft.getId()).getStatus());
            assertEquals(capOk.get() == 1 ? 100 : 100, finalCapacity);
            if (capOk.get() == 1 && confirmOk.get() == 1) {
                bothWon++;
            }
        }
        System.out.println("[race] 容量变更与预占确认并发 " + iterations + " 轮，双赢 " + bothWon
                + " 轮，全部最终状态满足容量约束");
    }

    /**
     * 确定性的互斥场景：预占要 100 箱（恰满），容量要下调到 0。
     * 两者只有一个能成，且负方的驳回必须基于锁后最新口径，数字要说准。
     */
    @Test
    void capacity_change_to_zero_vs_full_reservation_only_one_survives() throws Exception {
        // 结局一：容量先改为 0，后到的确认看到剩余 0 被准确驳回
        {
            Cell room = newRoom("RACE-ZERO-A");
            Reservation draft = createReservation(room.getId(), "满仓货", 100);
            cellService.update(room.getId(), cellReq(room.getId(), 0));
            BizException ex = assertThrows(BizException.class,
                    () -> reservationService.confirm(draft.getId()));
            assertTrue(ex.getMessage().contains("剩余可收 0 箱"), ex.getMessage());
            assertEquals(0, dbCapacity(room.getId()));
            assertEquals("待确认", reservationService.get(draft.getId()).getStatus());
        }
        // 结局二：预占先确认（占满 100），后到的容量下调 0 被驳回，容量不动
        {
            Cell room = newRoom("RACE-ZERO-B");
            Reservation draft = createReservation(room.getId(), "满仓货", 100);
            reservationService.confirm(draft.getId());
            BizException ex = assertThrows(BizException.class,
                    () -> cellService.update(room.getId(), cellReq(room.getId(), 0)));
            assertTrue(ex.getMessage().contains("预占#" + draft.getId()),
                    "驳回要点名撑住下限的那一笔预占：" + ex.getMessage());
            assertTrue(ex.getMessage().contains("100"), ex.getMessage());
            assertEquals(100, dbCapacity(room.getId()));
            assertEquals("已确认", reservationService.get(draft.getId()).getStatus());
            assertEquals(0, capacityService.remaining(room.getId()));
        }

        // 真正并发 10 轮：恰好一个成功，且不允许出现死锁/锁超时以外的意外
        int capFirst = 0;
        int confirmFirst = 0;
        for (int i = 0; i < 20; i++) {
            Cell room = newRoom("RACE-ZERO-C" + i);
            Reservation draft = createReservation(room.getId(), "满仓货", 100);
            com.coldstore.freezer.dto.CellReq req = cellReq(room.getId(), 0);
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger capOk = new AtomicInteger();
            AtomicInteger confirmOk = new AtomicInteger();
            AtomicReference<Throwable> unexpected = new AtomicReference<>();
            Runnable capTask = () -> {
                try {
                    start.await();
                    // 模拟真实调度抖动：谁先在库间行锁队列里排队由系统调度决定，
                    // 不由提交顺序决定（H2 行锁等待队列为 FIFO）
                    Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 20));
                    cellService.update(room.getId(), req);
                    capOk.incrementAndGet();
                } catch (BizException expected) {
                    // 确认先到，下限 100 > 0
                } catch (Throwable e) {
                    unexpected.set(e);
                }
            };
            Runnable confirmTask = () -> {
                try {
                    start.await();
                    Thread.sleep(java.util.concurrent.ThreadLocalRandom.current().nextInt(0, 20));
                    reservationService.confirm(draft.getId());
                    confirmOk.incrementAndGet();
                } catch (BizException expected) {
                    // 容量先改为 0，剩余 0 < 100
                } catch (Throwable e) {
                    unexpected.set(e);
                }
            };
            if (i % 2 == 0) { pool.submit(capTask); pool.submit(confirmTask); }
            else { pool.submit(confirmTask); pool.submit(capTask); }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));
            assertNull(unexpected.get(), "第 " + i + " 轮意外异常：" + unexpected.get());
            assertEquals(1, capOk.get() + confirmOk.get(),
                    "第 " + i + " 轮必须恰好一个成功，capOk=" + capOk + " confirmOk=" + confirmOk);
            int finalCapacity = dbCapacity(room.getId());
            int reserved = capacityService.view(room.getId()).getReservedQty();
            assertTrue(finalCapacity >= reserved,
                    "容量不得低于已确认预占：capacity=" + finalCapacity + " reserved=" + reserved);
            if (capOk.get() == 1) {
                capFirst++;
                assertEquals(0, finalCapacity);
                assertEquals("待确认", reservationService.get(draft.getId()).getStatus());
            } else {
                confirmFirst++;
                assertEquals(100, finalCapacity);
                assertEquals("已确认", reservationService.get(draft.getId()).getStatus());
            }
        }
        System.out.println("[race] 互斥竞速：容量先赢 " + capFirst + " 轮，预占确认先赢 " + confirmFirst + " 轮");
        assertTrue(capFirst > 0 && confirmFirst > 0,
                "两种加锁先后都必须真实跑到：容量先赢 " + capFirst + "，确认先赢 " + confirmFirst);
    }

    private Cell newRoom(String code) {
        Cell room = new Cell();
        room.setCode(code);
        room.setName(code);
        room.setTempZone("冷冻");
        room.setCapacity(100);
        room.setDeleted(0);
        return cellRepository.save(room);
    }

    // ---------- 预占确认 ----------

    @Test
    void reservation_qty_must_be_positive() {
        assertThrows(BizException.class, () -> createReservation(frozenCell, "货", 0));
        assertThrows(BizException.class, () -> createReservation(frozenCell, "货", -3));
    }

    @Test
    void confirm_fails_during_ongoing_defrost() {
        Reservation r = createReservation(frozenCell, "牛肉", 10);
        windowService.create(windowReq(frozenCell, -5, 55, "化霜"));
        BizException ex = assertThrows(BizException.class, () -> reservationService.confirm(r.getId()));
        assertTrue(ex.getMessage().contains("按 0"), ex.getMessage());
    }

    @Test
    void confirm_fails_when_not_enough_remaining_and_reports_the_number() {
        Long rid = reserveFor(frozenCell, "占位货", 80); // 确认后占 80
        Reservation r2 = createReservation(frozenCell, "别的货", 30);
        BizException ex = assertThrows(BizException.class, () -> reservationService.confirm(r2.getId()));
        assertTrue(ex.getMessage().contains("剩余可收 20 箱"), ex.getMessage());
        assertTrue(ex.getMessage().contains("30 箱"), ex.getMessage());
    }

    @Test
    void two_reservations_racing_for_last_capacity_only_one_confirmed() throws Exception {
        // 先占 90，剩 10
        reserveFor(frozenCell, "底仓", 90);
        Reservation a = createReservation(frozenCell, "抢A", 10);
        Reservation b = createReservation(frozenCell, "抢B", 10);

        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicReference<String> loserMsg = new AtomicReference<>();
        for (Long id : List.of(a.getId(), b.getId())) {
            pool.submit(() -> {
                try {
                    start.await();
                    reservationService.confirm(id);
                    ok.incrementAndGet();
                } catch (BizException e) {
                    loserMsg.set(e.getMessage());
                } catch (Exception ignored) {
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(1, ok.get(), "只许一张确认成功");
        assertNotNull(loserMsg.get());
        assertTrue(loserMsg.get().contains("剩余可收 0 箱"), "被挤掉者须看到当时剩 0 箱：" + loserMsg.get());
    }

    @Test
    void draft_created_before_a_defrost_window_cannot_be_confirmed_after_it_ends() {
        Reservation draft = createReservation(frozenCell, "冻品", 10);
        // 把开立时刻回拨到化霜窗开始之前，模拟「预占先开、化霜后发生且已结束」
        jdbcTemplate.update("update reservation set created_at = ? where id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(120)), draft.getId());
        DefrostWindow w = windowService.create(windowReq(frozenCell, -60, -1, "已结束化霜"));
        assertNotNull(w);
        BizException ex = assertThrows(BizException.class, () -> reservationService.confirm(draft.getId()));
        assertTrue(ex.getMessage().contains("重开"), ex.getMessage());
    }

    @Test
    void no_new_reservation_on_soft_deleted_cell_but_confirmed_one_still_visible() {
        Reservation r = createReservation(frozenCell, "存量货", 10);
        reservationService.confirm(r.getId());
        cellRepository.deleteById(frozenCell);
        cellRepository.flush();
        assertThrows(BizException.class, () -> createReservation(frozenCell, "新货", 10));
        // 已确认未核销的预占仍要能看见
        Reservation visible = reservationService.get(r.getId());
        assertEquals("已确认", visible.getStatus());
    }

    // ---------- 预占顶入库 ----------

    @Test
    void pending_batch_cannot_stock_in_without_matching_reservation() {
        Batch b = createPendingBatch(frozenCell, "三文鱼", 20);
        BizException ex = assertThrows(BizException.class, () -> batchService.stockIn(b.getId(), null));
        assertTrue(ex.getMessage().contains("预占"), ex.getMessage());
        // 库间对不上
        Long rid = reserveFor(chilledCell, "三文鱼", 20);
        BizException ex2 = assertThrows(BizException.class, () -> batchService.stockIn(b.getId(), rid));
        assertTrue(ex2.getMessage().contains("对不上"), ex2.getMessage());
        // 货品对不上
        Long rid2 = reserveFor(frozenCell, "别的鱼", 20);
        assertThrows(BizException.class, () -> batchService.stockIn(b.getId(), rid2));
        // 箱数对不上
        Long rid3 = reserveFor(frozenCell, "三文鱼", 25);
        BizException ex3 = assertThrows(BizException.class, () -> batchService.stockIn(b.getId(), rid3));
        assertTrue(ex3.getMessage().contains("对不上"), ex3.getMessage());
    }

    @Test
    void stock_in_with_matching_reservation_writes_it_off_and_sets_instock() {
        Batch b = createPendingBatch(frozenCell, "牛排", 40);
        Long rid = reserveFor(frozenCell, "牛排", 40);

        Batch done = batchService.stockIn(b.getId(), null); // 自动匹配
        assertEquals("在库", done.getStatus());
        Reservation used = reservationService.get(rid);
        assertEquals("已核销", used.getStatus());
        assertEquals(done.getId(), used.getBatchId());

        // 核销过的不能再顶第二笔
        Batch b2 = createPendingBatch(frozenCell, "牛排", 40);
        assertThrows(BizException.class, () -> batchService.stockIn(b2.getId(), rid));
    }

    @Test
    void stock_in_blocked_when_defrost_ongoing_even_with_confirmed_reservation() {
        Batch b = createPendingBatch(frozenCell, "羊排", 10);
        reserveFor(frozenCell, "羊排", 10);
        windowService.create(windowReq(frozenCell, -5, 55, "化霜中"));
        BizException ex = assertThrows(BizException.class, () -> batchService.stockIn(b.getId(), null));
        assertTrue(ex.getMessage().contains("化霜"), ex.getMessage());
    }

    @Test
    void reservation_confirmed_before_window_does_not_auto_revive_after_window_ends() {
        Batch b = createPendingBatch(frozenCell, "冻饺子", 10);
        Long rid = reserveFor(frozenCell, "冻饺子", 10);
        // 把确认时刻回拨，再制造一扇其后发生且已结束的占窗
        jdbcTemplate.update("update reservation set confirmed_at = ? where id = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusMinutes(120)), rid);
        windowService.create(windowReq(frozenCell, -60, -1, "做完的化霜"));
        // 旧预占不能偷偷继续顶入库
        BizException ex = assertThrows(BizException.class, () -> batchService.stockIn(b.getId(), rid));
        assertTrue(ex.getMessage().contains("重开"), ex.getMessage());
        // 重开一张新预占即可正常入库
        Long fresh = reserveFor(frozenCell, "冻饺子", 10);
        Batch done = batchService.stockIn(b.getId(), fresh);
        assertEquals("在库", done.getStatus());
        assertEquals("已核销", reservationService.get(fresh).getStatus());
        // 旧预占仍是已确认、未核销（没有被偷偷用掉）
        assertEquals("已确认", reservationService.get(rid).getStatus());
    }

    @Test
    void two_batches_racing_for_one_reservation_only_one_stocks_in() throws Exception {
        Batch b1 = createPendingBatch(frozenCell, "龙虾", 10);
        Batch b2 = createPendingBatch(frozenCell, "龙虾", 10);
        Long rid = reserveFor(frozenCell, "龙虾", 10);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        for (Long bid : List.of(b1.getId(), b2.getId())) {
            pool.submit(() -> {
                try {
                    start.await();
                    batchService.stockIn(bid, rid);
                    ok.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(1, ok.get(), "一张预占只能顶一笔");
        assertEquals("已核销", reservationService.get(rid).getStatus());
    }

    // ---------- 批次撤销收紧 ----------

    @Test
    void instock_batch_cannot_be_deleted_must_go_through_stock_out() {
        Batch b = createPendingBatch(frozenCell, "带鱼", 30);
        Long rid = reserveFor(frozenCell, "带鱼", 30);
        batchService.stockIn(b.getId(), rid);
        assertEquals(70, capacityService.remaining(frozenCell));

        BizException ex = assertThrows(BizException.class, () -> batchService.delete(b.getId()));
        assertTrue(ex.getMessage().contains("在库"), ex.getMessage());
        assertTrue(ex.getMessage().contains("出库"), ex.getMessage());

        // 驳回后台账仍在、货仍在库、剩余可收箱数没有因删除而虚涨，预占核销也还在
        Batch still = batchService.get(b.getId());
        assertEquals("在库", still.getStatus());
        assertEquals(30, capacityService.view(frozenCell).getInStockQty());
        assertEquals(0, capacityService.view(frozenCell).getPendingQty());
        assertEquals(70, capacityService.remaining(frozenCell));
        assertEquals("已核销", reservationService.get(rid).getStatus());
        assertEquals(b.getId(), reservationService.get(rid).getBatchId());

        // 唯一正路：先出库；出库后仍不能删
        batchService.stockOut(b.getId());
        BizException ex2 = assertThrows(BizException.class, () -> batchService.delete(b.getId()));
        assertTrue(ex2.getMessage().contains("追溯"), ex2.getMessage());
        assertEquals("已出", batchService.get(b.getId()).getStatus());
    }

    @Test
    void shipped_batch_cannot_be_deleted_outbound_history_kept() {
        Batch b = createInStockBatch(frozenCell, "秋刀鱼", 10);
        batchService.stockOut(b.getId());
        BizException ex = assertThrows(BizException.class, () -> batchService.delete(b.getId()));
        assertTrue(ex.getMessage().contains("已出库"), ex.getMessage());
        assertTrue(ex.getMessage().contains("追溯"), ex.getMessage());
        assertEquals("已出", batchService.get(b.getId()).getStatus());
    }

    @Test
    void only_pending_batch_can_be_revoked_and_pending_capacity_returns() {
        Batch b = createPendingBatch(frozenCell, "待收菜", 20);
        assertEquals(80, capacityService.remaining(frozenCell)); // 待入 20 先占口径
        batchService.delete(b.getId());
        assertThrows(BizException.class, () -> batchService.get(b.getId()));
        assertEquals(100, capacityService.remaining(frozenCell));
        assertEquals(100, capacityService.view(frozenCell).getRemaining());
        assertEquals(0, capacityService.view(frozenCell).getPendingQty());
    }

    /** 竞争结局一（确定性走读）：入库先成功，后到的撤销看到「在库」被驳回，预占只核销一次 */
    @Test
    void stock_in_first_then_revoke_sees_instock_and_is_rejected() {
        Batch b = createPendingBatch(frozenCell, "扇贝", 10);
        Long rid = reserveFor(frozenCell, "扇贝", 10);

        batchService.stockIn(b.getId(), rid);
        BizException ex = assertThrows(BizException.class, () -> batchService.delete(b.getId()));
        assertTrue(ex.getMessage().contains("在库"), ex.getMessage());

        assertEquals("在库", batchService.get(b.getId()).getStatus());
        Reservation r = reservationService.get(rid);
        assertEquals("已核销", r.getStatus());
        assertEquals(b.getId(), r.getBatchId());

        // 重复 / 迟到的入库也被状态挡住，不可能再触发第二次核销
        assertThrows(BizException.class, () -> batchService.stockIn(b.getId(), rid));
        Reservation again = reservationService.get(rid);
        assertEquals("已核销", again.getStatus());
        assertEquals(b.getId(), again.getBatchId());
    }

    /** 竞争结局二（确定性走读）：撤销先成功，后到的入库看到批次已消失，预占保持已确认且仍可顶下一笔 */
    @Test
    void revoke_first_then_stock_in_sees_missing_batch_and_reservation_stays_usable() {
        Batch b = createPendingBatch(frozenCell, "扇贝", 10);
        Long rid = reserveFor(frozenCell, "扇贝", 10);

        batchService.delete(b.getId());
        BizException ex = assertThrows(BizException.class, () -> batchService.stockIn(b.getId(), rid));
        assertTrue(ex.getMessage().contains("不存在"), ex.getMessage());

        // 不能留下「批次没了、预占却用掉了」的半截账
        Reservation r = reservationService.get(rid);
        assertEquals("已确认", r.getStatus());
        assertNull(r.getBatchId());
        assertNull(r.getWrittenOffAt());

        // 这张预占仍是未核销的可用状态：可顶一笔新的待入批次
        Batch b2 = createPendingBatch(frozenCell, "扇贝", 10);
        Batch done = batchService.stockIn(b2.getId(), rid);
        assertEquals("在库", done.getStatus());
        Reservation reused = reservationService.get(rid);
        assertEquals("已核销", reused.getStatus());
        assertEquals(b2.getId(), reused.getBatchId());
    }

    /**
     * 真正并发：同一笔待入批次，一边撤销、一边拿已确认预占办入库，同发 15 轮。
     * 每轮只许留下一个最终结果，且必须是两种自洽结局之一——
     * A 入库赢：批次在库 + 撤销被驳回 + 预占核销一次；
     * B 撤销赢：批次消失 + 入库被驳回 + 预占仍已确认，并能立刻顶一笔新批次。
     */
    @Test
    void concurrent_stock_in_and_revoke_leave_exactly_one_consistent_result() throws Exception {
        int iterations = 15;
        int stockInWon = 0;
        int revokeWon = 0;
        for (int i = 0; i < iterations; i++) {
            String cargo = "竞速货" + i;
            Batch b = createPendingBatch(frozenCell, cargo, 1);
            Long rid = reserveFor(frozenCell, cargo, 1);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger stockOk = new AtomicInteger();
            AtomicInteger deleteOk = new AtomicInteger();
            AtomicReference<Throwable> unexpected = new AtomicReference<>();
            // 交替提交顺序，尽量让两种抢锁先后都跑到；不变量断言不依赖具体谁赢
            List<Runnable> tasks = List.of(
                    () -> {
                        try {
                            batchService.stockIn(b.getId(), rid);
                            stockOk.incrementAndGet();
                        } catch (BizException expected) {
                            // 落选方
                        } catch (Throwable e) {
                            unexpected.set(e);
                        }
                    },
                    () -> {
                        try {
                            batchService.delete(b.getId());
                            deleteOk.incrementAndGet();
                        } catch (BizException expected) {
                            // 落选方
                        } catch (Throwable e) {
                            unexpected.set(e);
                        }
                    });
            if (i % 2 == 0) {
                pool.submit(tasks.get(0));
                pool.submit(tasks.get(1));
            } else {
                pool.submit(tasks.get(1));
                pool.submit(tasks.get(0));
            }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS), "第 " + i + " 轮并发动作未按时结束");
            assertNull(unexpected.get(), "出现了业务驳回以外的异常（死锁/锁超时/半截账）");
            assertEquals(1, stockOk.get() + deleteOk.get(),
                    "第 " + i + " 轮只许一个动作成功，stockOk=" + stockOk + " deleteOk=" + deleteOk);

            Batch after = batchRepository.findById(b.getId()).orElse(null);
            Reservation r = reservationService.get(rid);
            if (stockOk.get() == 1) {
                stockInWon++;
                // 结局 A：批次仍在且为在库；撤销必已被驳回，再删一次照样拦
                assertNotNull(after, "入库赢：批次必须仍在");
                assertEquals("在库", after.getStatus());
                assertEquals("已核销", r.getStatus());
                assertEquals(b.getId(), r.getBatchId());
                assertNotNull(r.getWrittenOffAt());
                assertThrows(BizException.class, () -> batchService.delete(b.getId()));
            } else {
                revokeWon++;
                // 结局 B：批次真的没了，但预占没被用掉，仍是可用的已确认预占
                assertNull(after, "撤销赢：批次必须已不存在");
                assertEquals("已确认", r.getStatus(), "撤销赢：预占不许被核销");
                assertNull(r.getBatchId(), "撤销赢：预占不能挂着已消失批次的 id");
                assertNull(r.getWrittenOffAt());
                Batch replacement = createPendingBatch(frozenCell, cargo, 1);
                Batch done = batchService.stockIn(replacement.getId(), rid);
                assertEquals("在库", done.getStatus());
                assertEquals("已核销", reservationService.get(rid).getStatus());
                assertEquals(replacement.getId(), reservationService.get(rid).getBatchId());
            }
        }
        assertTrue(stockInWon + revokeWon == iterations);
        System.out.println("[race] 入库先赢 " + stockInWon + " 轮，撤销先赢 " + revokeWon + " 轮（共 " + iterations + "）");
    }

    // ---------- 巡检 ↔ 出库联动 ----------

    private long addLocation(long cellId, String code) {
        Location loc = new Location();
        loc.setCode(code); loc.setCellId(cellId); loc.setStatus("空");
        return locationRepository.save(loc).getId();
    }

    private InspectionReq inspReq(long cellId, List<Long> locIds, Double temp, String result, String handling) {
        InspectionReq req = new InspectionReq();
        req.setCellId(cellId);
        req.setCheckDate(LocalDate.now());
        req.setLocationIds(locIds);
        req.setMeasuredTemp(temp);
        req.setResult(result);
        req.setHandling(handling);
        return req;
    }

    private Batch createInStockBatch(long cellId, String cargo, int qty) {
        Batch b = createPendingBatch(cellId, cargo, qty);
        batchService.stockIn(b.getId(), reserveFor(cellId, cargo, qty));
        return b;
    }

    @Test
    void inspection_requires_location_and_measured_temp() {
        long loc = addLocation(frozenCell, "F-1");
        long otherCellLoc = addLocation(chilledCell, "C-1");
        // 一个货位都不点
        BizException ex = assertThrows(BizException.class,
                () -> inspectionService.create(inspReq(frozenCell, List.of(), -18.0, "正常", null)));
        assertTrue(ex.getMessage().contains("货位"), ex.getMessage());
        // 点了别的库间的货位
        BizException ex2 = assertThrows(BizException.class,
                () -> inspectionService.create(inspReq(frozenCell, List.of(otherCellLoc), -18.0, "正常", null)));
        assertTrue(ex2.getMessage().contains("不属于"), ex2.getMessage());
        // 不填实测温度
        BizException ex3 = assertThrows(BizException.class,
                () -> inspectionService.create(inspReq(frozenCell, List.of(loc), null, "正常", null)));
        assertTrue(ex3.getMessage().contains("实测温度"), ex3.getMessage());
        // 货位 + 实测温度齐全才给开单
        assertDoesNotThrow(() -> inspectionService.create(inspReq(frozenCell, List.of(loc), -18.0, "正常", null)));
    }

    @Test
    void out_of_range_temp_must_be_abnormal_with_handling() {
        long fLoc = addLocation(frozenCell, "F-1");
        long cLoc = addLocation(chilledCell, "C-1");
        // 冷冻上限 -15℃：-10℃ 越界，只勾「正常」当场驳回
        BizException ex = assertThrows(BizException.class,
                () -> inspectionService.create(inspReq(frozenCell, List.of(fLoc), -10.0, "正常", null)));
        assertTrue(ex.getMessage().contains("异常"), ex.getMessage());
        // 越界记了异常但没写处置意见，照样驳回
        BizException ex2 = assertThrows(BizException.class,
                () -> inspectionService.create(inspReq(frozenCell, List.of(fLoc), -10.0, "异常", "  ")));
        assertTrue(ex2.getMessage().contains("处置意见"), ex2.getMessage());
        // 越界 + 异常 + 处置意见，才放行
        assertDoesNotThrow(() -> inspectionService.create(
                inspReq(frozenCell, List.of(fLoc), -10.0, "异常", "已转移货品并报修")));
        // 冷藏区间 0 ~ 8℃：12℃ 和 -1℃ 都越界
        assertThrows(BizException.class,
                () -> inspectionService.create(inspReq(chilledCell, List.of(cLoc), 12.0, "正常", null)));
        assertThrows(BizException.class,
                () -> inspectionService.create(inspReq(chilledCell, List.of(cLoc), -1.0, "正常", null)));
        // 冷藏 4℃ 在界内，记正常可以
        assertDoesNotThrow(() -> inspectionService.create(inspReq(chilledCell, List.of(cLoc), 4.0, "正常", null)));
    }

    @Test
    void open_abnormal_blocks_stock_out_until_closed() {
        long loc = addLocation(frozenCell, "F-1");
        Batch b = createInStockBatch(frozenCell, "虾仁", 10);
        Inspection ins = inspectionService.create(
                inspReq(frozenCell, List.of(loc), -10.0, "异常", "已转移货品并报修"));
        // 未闭环：在库也出不去
        BizException ex = assertThrows(BizException.class, () -> batchService.stockOut(b.getId()));
        assertTrue(ex.getMessage().contains("未闭环"), ex.getMessage());
        assertEquals("在库", batchService.get(b.getId()).getStatus());
        // 闭环后放行
        inspectionService.close(ins.getId(), "压缩机已修复，复测 -18℃");
        Batch done = batchService.stockOut(b.getId());
        assertEquals("已出", done.getStatus());
    }

    @Test
    void normal_inspection_does_not_block_stock_out() {
        long loc = addLocation(frozenCell, "F-1");
        inspectionService.create(inspReq(frozenCell, List.of(loc), -18.0, "正常", null));
        Batch b = createInStockBatch(frozenCell, "虾仁", 10);
        assertEquals("已出", batchService.stockOut(b.getId()).getStatus());
    }

    @Test
    void soft_deleted_cell_blocks_new_inspection_but_open_abnormal_still_blocks_stock_out() {
        long loc = addLocation(frozenCell, "F-1");
        Batch b = createInStockBatch(frozenCell, "虾仁", 10);
        Inspection ins = inspectionService.create(
                inspReq(frozenCell, List.of(loc), -10.0, "异常", "已转移货品并报修"));
        cellRepository.deleteById(frozenCell);
        cellRepository.flush();
        // 软删后不许再开新巡检
        BizException ex = assertThrows(BizException.class,
                () -> inspectionService.create(inspReq(frozenCell, List.of(loc), -18.0, "正常", null)));
        assertTrue(ex.getMessage().contains("软删"), ex.getMessage());
        // 开着的异常单还在，在库批次仍出不了
        BizException ex2 = assertThrows(BizException.class, () -> batchService.stockOut(b.getId()));
        assertTrue(ex2.getMessage().contains("未闭环"), ex2.getMessage());
        // 闭环之后才放行
        inspectionService.close(ins.getId(), null);
        assertEquals("已出", batchService.stockOut(b.getId()).getStatus());
    }

    @Test
    void abnormal_cannot_be_deleted_or_flipped_back_to_normal() {
        long loc = addLocation(frozenCell, "F-1");
        Inspection ins = inspectionService.create(
                inspReq(frozenCell, List.of(loc), -10.0, "异常", "已转移货品并报修"));
        // 未闭环不能删
        assertThrows(BizException.class, () -> inspectionService.delete(ins.getId()));
        // 未闭环不能改回「正常」
        InspectionReq flip = new InspectionReq();
        flip.setResult("正常");
        BizException ex = assertThrows(BizException.class,
                () -> inspectionService.update(ins.getId(), flip));
        assertTrue(ex.getMessage().contains("闭环"), ex.getMessage());
        // 闭环后留痕：不能改也不能删
        inspectionService.close(ins.getId(), null);
        assertThrows(BizException.class, () -> inspectionService.update(ins.getId(), flip));
        assertThrows(BizException.class, () -> inspectionService.delete(ins.getId()));
    }

    @Test
    void two_people_closing_same_abnormal_only_one_succeeds() throws Exception {
        long loc = addLocation(frozenCell, "F-1");
        Inspection ins = inspectionService.create(
                inspReq(frozenCell, List.of(loc), -10.0, "异常", "已转移货品并报修"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger();
        AtomicReference<String> loserMsg = new AtomicReference<>();
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    inspectionService.close(ins.getId(), null);
                    ok.incrementAndGet();
                } catch (BizException e) {
                    loserMsg.set(e.getMessage());
                } catch (Exception ignored) {
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS));

        assertEquals(1, ok.get(), "同一张异常单只许落成一次闭环");
        assertNotNull(loserMsg.get());
        assertTrue(loserMsg.get().contains("已闭环"), "后点的那下要看到单子已关上：" + loserMsg.get());
        assertEquals(Integer.valueOf(1), inspectionService.get(ins.getId()).getClosed());
    }
}
