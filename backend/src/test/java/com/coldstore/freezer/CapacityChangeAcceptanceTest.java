package com.coldstore.freezer;

import com.coldstore.freezer.dto.CellReq;
import com.coldstore.freezer.dto.ReservationReq;
import com.coldstore.freezer.entity.Batch;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.entity.Reservation;
import com.coldstore.freezer.repository.BatchRepository;
import com.coldstore.freezer.repository.CellRepository;
import com.coldstore.freezer.service.BatchService;
import com.coldstore.freezer.service.CapacityService;
import com.coldstore.freezer.service.CellService;
import com.coldstore.freezer.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 容量变更收紧的验收：
 * 1) 新容量不得小于「在库 + 待入 + 已确认未核销预占」，驳回时三项数字与原容量都要说清；
 * 2) 容量变更与预占确认在同一库间行锁上串行，只许留下一个不违约结果，两种先后顺序都走读；
 * 3) 真正并发下不变量始终成立；
 * 4) 绕过页面直连（空体/坏 JSON/负数/空容量）一律明确 400，数据原样不动；
 * 5) 保存成功后容量总览、预占页、批次页共用的唯一口径立即换成新容量。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CapacityChangeAcceptanceTest {

    @Autowired CellService cellService;
    @Autowired CellRepository cellRepository;
    @Autowired BatchRepository batchRepository;
    @Autowired ReservationService reservationService;
    @Autowired BatchService batchService;
    @Autowired CapacityService capacityService;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired MockMvc mockMvc;

    private long cellId;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("delete from inspection_location");
        jdbcTemplate.update("delete from inspection");
        jdbcTemplate.update("delete from reservation");
        jdbcTemplate.update("delete from defrost_window");
        jdbcTemplate.update("delete from batch");
        jdbcTemplate.update("delete from location");
        jdbcTemplate.update("delete from cell");

        Cell c = new Cell();
        c.setCode("C-CAP"); c.setName("容量变更测试间"); c.setTempZone("冷冻");
        c.setCapacity(100); c.setDeleted(0);
        cellId = cellRepository.save(c).getId();
    }

    private CellReq reqFor(Integer capacity) {
        CellReq req = new CellReq();
        req.setCapacity(capacity);
        return req;
    }

    private Reservation createReservation(String cargo, int qty) {
        ReservationReq req = new ReservationReq();
        req.setCellId(cellId); req.setCargo(cargo); req.setQty(qty);
        req.setPlanDate(LocalDate.now());
        return reservationService.create(req);
    }

    private Batch createPending(String cargo, int qty) {
        com.coldstore.freezer.dto.BatchReq req = new com.coldstore.freezer.dto.BatchReq();
        req.setCellId(cellId); req.setCargo(cargo); req.setQty(qty);
        req.setBatchDate(LocalDate.now());
        return batchService.create(req);
    }

    private long reserveConfirmed(String cargo, int qty) {
        Reservation r = createReservation(cargo, qty);
        reservationService.confirm(r.getId());
        return r.getId();
    }

    private int capacityInDb() {
        return jdbcTemplate.queryForObject(
                "select capacity from cell where id = ?", Integer.class, cellId);
    }

    // ---------- 下限口径 ----------

    @Test
    void capacity_cannot_drop_below_instock_plus_pending_plus_confirmed_reservation() {
        Batch b = createPending("虾", 30);
        batchService.stockIn(b.getId(), reserveConfirmed("虾", 30)); // 在库 30
        createPending("鱼", 20);                                  // 待入 20
        reserveConfirmed("贝", 25);                               // 已确认未核销 25
        // 承诺下限 = 30 + 20 + 25 = 75

        var before = capacityService.view(cellId);
        assertEquals(75, before.getInStockQty() + before.getPendingQty() + before.getReservedQty());

        BizException ex = assertThrows(BizException.class,
                () -> cellService.update(cellId, reqFor(74)));
        assertTrue(ex.getMessage().contains("74"), ex.getMessage());
        assertTrue(ex.getMessage().contains("75"), ex.getMessage());
        assertTrue(ex.getMessage().contains("已在库 30"), ex.getMessage());
        assertTrue(ex.getMessage().contains("待入 20"), ex.getMessage());
        assertTrue(ex.getMessage().contains("预占 25"), ex.getMessage());
        assertTrue(ex.getMessage().contains("原容量 100"), ex.getMessage());

        // 驳回后容量、库存、预占状态原样不动
        assertEquals(100, capacityInDb());
        assertEquals("在库", batchRepository.findById(b.getId()).orElseThrow().getStatus());
        assertEquals(100, capacityService.view(cellId).getCapacity());
        assertEquals(25, capacityService.view(cellId).getReservedQty());

        // 恰好等于下限可以；再小一箱都不行
        assertDoesNotThrow(() -> cellService.update(cellId, reqFor(75)));
        assertEquals(75, capacityInDb());
        BizException ex2 = assertThrows(BizException.class,
                () -> cellService.update(cellId, reqFor(74)));
        assertTrue(ex2.getMessage().contains("原容量 75"), ex2.getMessage());
    }

    @Test
    void each_component_alone_holds_the_floor() {
        reserveConfirmed("仅预占", 40);
        BizException e1 = assertThrows(BizException.class,
                () -> cellService.update(cellId, reqFor(39)));
        assertTrue(e1.getMessage().contains("40"), e1.getMessage());

        Batch p = createPending("仅待入", 30);
        // 40 预占 + 30 待入 = 70
        assertThrows(BizException.class, () -> cellService.update(cellId, reqFor(69)));
        assertDoesNotThrow(() -> cellService.update(cellId, reqFor(70)));

        // 待入批次撤销后下限回落，可以继续下调
        batchService.delete(p.getId());
        assertDoesNotThrow(() -> cellService.update(cellId, reqFor(40)));
        assertEquals(40, capacityInDb());
    }

    @Test
    void raising_capacity_takes_effect_everywhere_via_single_source() {
        reserveConfirmed("占位", 90);
        assertEquals(10, capacityService.view(cellId).getRemaining());
        cellService.update(cellId, reqFor(200));
        // 容量总览（预占页/批次页都读它）立刻是同一新口径
        var view = capacityService.view(cellId);
        assertEquals(200, view.getCapacity());
        assertEquals(110, view.getRemaining());
        assertEquals(110, capacityService.remaining(cellId));
        assertEquals(110, capacityService.viewAll().stream()
                .filter(v -> v.getCellId() == cellId).findFirst().orElseThrow().getRemaining());
    }

    // ---------- 输入与绕过页面直连 ----------

    @Test
    void negative_null_empty_or_garbage_capacity_rejected_and_nothing_changes() throws Exception {
        createPending("底货", 10); // 让下限非 0，证明负数不是被下限巧合拦住

        assertThrows(BizException.class, () -> cellService.update(cellId, reqFor(-1)));
        assertThrows(BizException.class, () -> cellService.update(cellId, reqFor(null)));
        assertThrows(BizException.class, () -> cellService.create(reqFor(-5)));
        assertEquals(100, capacityInDb());

        // 绕过页面直连：负数
        mockMvc.perform(put("/api/cells/" + cellId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":-1}"))
                .andExpect(status().isBadRequest());
        // 空容量（JSON null 显式给出）
        mockMvc.perform(put("/api/cells/" + cellId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":null}"))
                .andExpect(status().isBadRequest());
        // 空请求体
        mockMvc.perform(put("/api/cells/" + cellId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
        // 坏 JSON / 非数字容量
        mockMvc.perform(put("/api/cells/" + cellId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{capacity:abc}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/cells/" + cellId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capacity\":\"abc\"}"))
                .andExpect(status().isBadRequest());

        // 全部尝试之后：原容量、库存、预占口径不变
        assertEquals(100, capacityInDb());
        assertEquals(10, capacityService.view(cellId).getPendingQty());
    }

    // ---------- 容量变更 ↔ 预占确认：两种先后顺序（确定性走读） ----------

    @Test
    void capacity_change_committed_first_later_confirmation_sees_new_remaining_and_is_rejected() {
        // 容量 100、无任何承诺；一张 60 的预占刚开立（待确认不占剩余、也不计容量下限）。
        // 库管在旧详情页上下调容量到 50：下限为 0，容量变更先提交落库。
        Reservation r = createReservation("新预占", 60);
        assertDoesNotThrow(() -> cellService.update(cellId, reqFor(50)));
        assertEquals(50, capacityInDb());

        // 另一名值班员后到的确认必须看到新容量口径：剩余 = 50 - 0 = 50，要 60 箱，准确驳回
        BizException ex = assertThrows(BizException.class,
                () -> reservationService.confirm(r.getId()));
        assertTrue(ex.getMessage().contains("剩余可收 50 箱"), ex.getMessage());
        assertTrue(ex.getMessage().contains("60 箱"), ex.getMessage());
        // 不能出现「页面提示成功、数据库容量却被覆盖」：容量仍是 50，预占仍是待确认
        assertEquals(50, capacityInDb());
        assertEquals("待确认", reservationService.get(r.getId()).getStatus());
        assertEquals(0, capacityService.view(cellId).getReservedQty());
        assertEquals(50, capacityService.view(cellId).getRemaining());
    }

    @Test
    void confirmation_committed_first_later_capacity_change_counts_it_in_floor_and_is_rejected() {
        // 容量 100，剩余 100；一张 60 的待确认预占先确认成功
        Reservation r = createReservation("抢先确认", 60);
        reservationService.confirm(r.getId());

        // 库管旧页面上看到的还是「容量 100、无预占」，想下调到 50：
        // 等锁后必须把刚确认的 60 计入下限，50 < 60 被驳回
        BizException ex = assertThrows(BizException.class,
                () -> cellService.update(cellId, reqFor(50)));
        assertTrue(ex.getMessage().contains("50"), ex.getMessage());
        assertTrue(ex.getMessage().contains("60"), ex.getMessage());
        assertTrue(ex.getMessage().contains("预占 60"), ex.getMessage());

        // 不能出现「预占已确认而容量被改到承诺量以下」
        assertEquals(100, capacityInDb());
        assertEquals("已确认", reservationService.get(r.getId()).getStatus());
        assertEquals(60, capacityService.view(cellId).getReservedQty());
        assertEquals(40, capacityService.view(cellId).getRemaining());

        // 下调到 60（新下限）则放行
        assertDoesNotThrow(() -> cellService.update(cellId, reqFor(60)));
        assertEquals(60, capacityInDb());
        assertEquals(0, capacityService.view(cellId).getRemaining());
    }

    // ---------- 真正并发 ----------

    /**
     * 容量下调与预占确认同发多轮：
     * 每轮初始容量 100、一张 60 的待确认预占；容量下调目标 50（确认先成则必被驳回）。
     * 不变量（不依赖谁先拿锁）：
     *   - 若容量变成 50：预占必须仍是待确认（确认看到剩余 0 被驳回）；
     *   - 若预占变成已确认：容量必须仍是 100（下调因下限 60 被驳回）；
     *   - 容量永远 ≥ 在库+待入+已确认预占；不允许死锁/锁超时等业务驳回以外的异常。
     */
    @Test
    void concurrent_capacity_change_and_confirmation_leave_one_consistent_result() throws Exception {
        int iterations = 20;
        int changeWon = 0;
        int confirmWon = 0;
        for (int i = 0; i < iterations; i++) {
            Reservation r = createReservation("竞速预占" + i, 60);
            Long rid = r.getId();

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger changeOk = new AtomicInteger();
            AtomicInteger confirmOk = new AtomicInteger();
            AtomicReference<Throwable> unexpected = new AtomicReference<>();
            Runnable change = () -> {
                try {
                    start.await();
                    cellService.update(cellId, reqFor(50));
                    changeOk.incrementAndGet();
                } catch (com.coldstore.freezer.dto.BizException expected) {
                    // 落选方：容量低于承诺下限
                } catch (Throwable t) {
                    unexpected.set(t);
                }
            };
            Runnable confirm = () -> {
                try {
                    start.await();
                    reservationService.confirm(rid);
                    confirmOk.incrementAndGet();
                } catch (com.coldstore.freezer.dto.BizException expected) {
                    // 落选方：新剩余不足
                } catch (Throwable t) {
                    unexpected.set(t);
                }
            };
            if (i % 2 == 0) { pool.submit(change); pool.submit(confirm); }
            else { pool.submit(confirm); pool.submit(change); }
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS),
                    "第 " + i + " 轮未按时结束（疑似死锁/锁等待）");
            assertNull(unexpected.get(), "出现业务驳回以外的异常：" + unexpected.get());
            assertEquals(1, changeOk.get() + confirmOk.get(),
                    "第 " + i + " 轮只许一个动作成功 change=" + changeOk + " confirm=" + confirmOk);

            int cap = capacityInDb();
            String status = reservationService.get(rid).getStatus();
            var v = capacityService.view(cellId);
            int floor = v.getInStockQty() + v.getPendingQty() + v.getReservedQty();
            assertTrue(cap >= floor, "容量不得低于承诺下限：cap=" + cap + " floor=" + floor);

            if (changeOk.get() == 1) {
                changeWon++;
                assertEquals(50, cap, "容量变更赢：容量必须已是 50");
                assertEquals("待确认", status, "容量变更赢：后到确认必须被驳回，预占仍待确认");
            } else {
                confirmWon++;
                assertEquals(100, cap, "确认赢：容量变更必须被驳回，容量仍是 100");
                assertEquals("已确认", status, "确认赢：预占必须已确认");
            }

            // 复位到下一轮初始状态：把容量调回 100（本轮预占若已确认占 60，下限 60，100 合法）；
            // 已确认的预占物理删掉，待确认的也删掉，保证下一轮承诺口径归零
            if ("已确认".equals(status)) {
                jdbcTemplate.update("update reservation set status = '待确认', confirmed_at = null where id = ?", rid);
            }
            jdbcTemplate.update("delete from reservation where id = ?", rid);
            cellService.update(cellId, reqFor(100));
        }
        assertTrue(changeWon + confirmWon == iterations);
        System.out.println("[race] 容量变更先赢 " + changeWon + " 轮，预占确认先赢 "
                + confirmWon + " 轮（共 " + iterations + "）");
    }
}
