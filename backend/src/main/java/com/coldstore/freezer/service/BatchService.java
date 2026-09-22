package com.coldstore.freezer.service;

import com.coldstore.freezer.dto.BatchReq;
import com.coldstore.freezer.dto.BizException;
import com.coldstore.freezer.entity.Batch;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.entity.Reservation;
import com.coldstore.freezer.repository.BatchRepository;
import com.coldstore.freezer.repository.CellRepository;
import com.coldstore.freezer.repository.InspectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BatchService {

    private final BatchRepository batchRepository;
    private final CellRepository cellRepository;
    private final ReservationService reservationService;
    private final CapacityService capacityService;
    private final InspectionRepository inspectionRepository;

    public BatchService(BatchRepository batchRepository,
                        CellRepository cellRepository,
                        ReservationService reservationService,
                        CapacityService capacityService,
                        InspectionRepository inspectionRepository) {
        this.batchRepository = batchRepository;
        this.cellRepository = cellRepository;
        this.reservationService = reservationService;
        this.capacityService = capacityService;
        this.inspectionRepository = inspectionRepository;
    }

    private void assertCellValid(Long cellId) {
        if (cellId == null) {
            throw new BizException("归属库间不能为空");
        }
        Cell cell = cellRepository.findById(cellId).orElse(null);
        if (cell == null) {
            throw new BizException("归属库间不存在或已删除");
        }
    }

    /**
     * 开立待入批次：待入箱数计入容量承诺下限。
     * 同样先锁库间行，与容量变更、预占确认走同一串行点，
     * 保证「容量下调」不会和「新待入批次」互相覆盖出新容量小于承诺量的结果。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Batch create(BatchReq req) {
        if (req.getCellId() == null) {
            throw new BizException("归属库间不能为空");
        }
        Cell cell = cellRepository.findActiveByIdForUpdate(req.getCellId()).orElse(null);
        if (cell == null) {
            throw new BizException("归属库间不存在或已删除");
        }
        if (req.getCargo() == null || req.getCargo().isBlank()) {
            throw new BizException("货品名不能为空");
        }
        if (req.getQty() == null || req.getQty() <= 0) {
            throw new BizException("入库必须填写数量");
        }
        Batch batch = new Batch();
        batch.setCellId(cell.getId());
        batch.setCargo(req.getCargo());
        batch.setQty(req.getQty());
        batch.setBatchDate(req.getBatchDate() == null ? LocalDate.now() : req.getBatchDate());
        batch.setStatus("待入");
        return batchRepository.save(batch);
    }

    public List<Batch> list() {
        return batchRepository.findAll();
    }

    public Batch get(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new BizException("入库批次不存在"));
    }

    @Transactional
    public Batch update(Long id, BatchReq req) {
        Batch batch = get(id);
        if (req.getCellId() != null && !req.getCellId().equals(batch.getCellId())) {
            assertCellValid(req.getCellId());
            batch.setCellId(req.getCellId());
        }
        if (req.getCargo() != null) {
            batch.setCargo(req.getCargo());
        }
        if (req.getQty() != null) {
            if (req.getQty() <= 0) {
                throw new BizException("数量必须大于 0");
            }
            batch.setQty(req.getQty());
        }
        if (req.getBatchDate() != null) {
            batch.setBatchDate(req.getBatchDate());
        }
        return batchRepository.save(batch);
    }

    /**
     * 入库：待入 -> 在库。
     * 不再是「待入即可入库」：
     * 1) 库间化霜占窗进行中，剩余按 0，一律拒收；
     * 2) 必须有一张库间、货品名、箱数都对得上的「已确认未核销」预占顶用，
     *    可在请求体里带 reservationId 指定，否则自动挑最早一张；
     * 3) 入库成功立刻核销该预占，核销过的不能再顶第二笔。
     *
     * 与「撤销删除」并发时：先对批次行加写锁，状态校验、状态翻转、预占核销
     * 全部在同一把锁后的最新状态上完成。撤销若先拿到锁并提交，这里等锁后
     * 查不到批次，按「批次不存在」驳回，预占不会被核销；入库若先拿到锁，
     * 撤销等锁后看到的已是「在库」，照样驳回。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Batch stockIn(Long id, Long reservationId) {
        Batch batch = batchRepository.findLockedById(id)
                .orElseThrow(() -> new BizException("入库批次不存在或已撤销删除"));
        if (!"待入".equals(batch.getStatus())) {
            throw new BizException("只有「待入」状态可以入库，当前为「" + batch.getStatus() + "」");
        }
        if (capacityService.isDefrostOngoing(batch.getCellId())) {
            throw new BizException("该库间化霜占窗进行中，剩余可收箱数按 0，本批次不能入库");
        }
        Reservation reservation = reservationService.pickForBatch(
                batch.getCellId(), batch.getCargo().trim(), batch.getQty(), reservationId);
        batch.setStatus("在库");
        Batch saved = batchRepository.save(batch);
        reservationService.writeOff(reservation, saved.getId());
        return saved;
    }

    /**
     * 出库：在库 -> 已出。
     * 不再是「在库就能出」：该库间只要还有未闭环的异常巡检单，
     * 这间里在库批次一律不得出库——想先拉走再补单也不行，出库口当场拦住。
     * 按 cellId 直查巡检表，库间已软删也照样拦，直到异常闭环。
     * 同样对批次行加锁，与撤销/入库走同一串行点，等锁后按最新状态判定。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Batch stockOut(Long id) {
        Batch batch = batchRepository.findLockedById(id)
                .orElseThrow(() -> new BizException("入库批次不存在"));
        if (!"在库".equals(batch.getStatus())) {
            throw new BizException("只有「在库」状态可以出库，当前为「" + batch.getStatus() + "」");
        }
        if (inspectionRepository.existsByCellIdAndResultAndClosed(batch.getCellId(), "异常", 0)) {
            throw new BizException("该库间还有未闭环的异常巡检单，在库批次一律不得出库，请先完成处置并闭环巡检单");
        }
        batch.setStatus("已出");
        return batchRepository.save(batch);
    }

    /**
     * 撤销删除：只有真正还没入库的「待入」批次可以撤销。
     * 「在库」货已在库里，必须先按正常流程出库，直接删掉会让台账消失而货还占着库位、
     * 剩余可收箱数虚涨；「已出」是出库历史追溯，必须永久保留。
     *
     * 这里不是「删之前读一次状态」：先对批次行加写锁，在锁到的最新状态上判定再删。
     * 与入库并发时二者在同一把行锁上串行——
     * 入库先拿锁提交：批次已是「在库」，本方法等锁后按在库驳回，批次与核销都保留；
     * 撤销先拿锁提交：批次物理消失，后到的入库等锁后查不到批次，直接驳回，
     * 那张已确认预占不会被核销，仍是可用于下一笔待入批次的可用状态。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id) {
        Batch batch = batchRepository.findLockedById(id)
                .orElseThrow(() -> new BizException("入库批次不存在或已撤销删除"));
        if ("在库".equals(batch.getStatus())) {
            throw new BizException("批次已入库（在库），不能直接删除：请先按正常流程出库，出库前台账与容量必须保留");
        }
        if ("已出".equals(batch.getStatus())) {
            throw new BizException("批次已出库，出库历史必须保留追溯，不能删除");
        }
        batchRepository.delete(batch);
        batchRepository.flush();
    }
}
