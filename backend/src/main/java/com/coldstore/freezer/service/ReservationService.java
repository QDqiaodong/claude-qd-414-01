package com.coldstore.freezer.service;

import com.coldstore.freezer.dto.BizException;
import com.coldstore.freezer.dto.ReservationReq;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.entity.DefrostWindow;
import com.coldstore.freezer.entity.Reservation;
import com.coldstore.freezer.repository.CellRepository;
import com.coldstore.freezer.repository.DefrostWindowRepository;
import com.coldstore.freezer.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final CellRepository cellRepository;
    private final DefrostWindowRepository defrostWindowRepository;
    private final CapacityService capacityService;

    public ReservationService(ReservationRepository reservationRepository,
                              CellRepository cellRepository,
                              DefrostWindowRepository defrostWindowRepository,
                              CapacityService capacityService) {
        this.reservationRepository = reservationRepository;
        this.cellRepository = cellRepository;
        this.defrostWindowRepository = defrostWindowRepository;
        this.capacityService = capacityService;
    }

    /** 开立预占：只登记为「待确认」，不占剩余箱数；库间软删后不许再开 */
    @Transactional
    public Reservation create(ReservationReq req) {
        if (req.getCellId() == null) {
            throw new BizException("归属库间不能为空");
        }
        Cell cell = cellRepository.findById(req.getCellId()).orElse(null);
        if (cell == null) {
            throw new BizException("库间不存在或已软删，不能开新预占");
        }
        if (req.getCargo() == null || req.getCargo().isBlank()) {
            throw new BizException("货品名不能为空");
        }
        if (req.getQty() == null || req.getQty() <= 0) {
            throw new BizException("预占箱数必须大于 0");
        }
        if (req.getPlanDate() == null) {
            throw new BizException("计划入库日不能为空");
        }
        Reservation r = new Reservation();
        r.setCellId(cell.getId());
        r.setCargo(req.getCargo().trim());
        r.setQty(req.getQty());
        r.setPlanDate(req.getPlanDate());
        r.setStatus("待确认");
        return reservationRepository.save(r);
    }

    @Transactional(readOnly = true)
    public List<Reservation> list(Long cellId) {
        if (cellId == null) {
            return reservationRepository.findAllByOrderByIdDesc();
        }
        return reservationRepository.findByCellIdOrderByIdDesc(cellId);
    }

    @Transactional(readOnly = true)
    public Reservation get(Long id) {
        return reservationRepository.findById(id)
                .orElseThrow(() -> new BizException("预占不存在"));
    }

    /**
     * 确认预占：库间行上锁串行化争抢（READ_COMMITTED 保证等锁后能看到对方已提交的预占）。
     * 进行中的化霜占窗直接按剩余 0，不能确认；
     * 剩余箱数不够时当场驳回并说清当时还剩多少；
     * 预占开立后被一扇化霜占窗盖过（哪怕已经结束），这张草稿必须作废重开。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation confirm(Long id) {
        Reservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new BizException("预占不存在"));
        if (!"待确认".equals(r.getStatus())) {
            throw new BizException("只有「待确认」的预占可以确认，当前为「" + r.getStatus() + "」");
        }
        Cell cell = cellRepository.findActiveByIdForUpdate(r.getCellId()).orElse(null);
        if (cell == null) {
            throw new BizException("库间已软删，预占不能再确认");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!defrostWindowRepository.findOngoing(cell.getId(), now).isEmpty()) {
            throw new BizException("库间「" + cell.getCode() + "」化霜占窗进行中，剩余可收箱数按 0，预占不能确认");
        }
        List<DefrostWindow> covering = defrostWindowRepository.findOccurredSince(
                cell.getId(), r.getCreatedAt(), now);
        if (!covering.isEmpty()) {
            throw new BizException("该预占开立后库间「" + cell.getCode()
                    + "」已经历化霜占窗，旧预占不能确认，请重开一张新预占");
        }
        int remaining = capacityService.remaining(cell);
        if (r.getQty() > remaining) {
            throw new BizException(String.format(
                    "库间「%s」当前剩余可收 %d 箱，本预占要 %d 箱，确认被驳回",
                    cell.getCode(), remaining, r.getQty()));
        }
        r.setStatus("已确认");
        r.setConfirmedAt(now);
        return reservationRepository.save(r);
    }

    /**
     * 已确认预占是否仍可用于顶单：
     * 确认之后若有一扇化霜占窗开始/结束过，旧预占即被盖过，
     * 窗口结束也不偷偷恢复成可用，值班必须重开新预占。
     */
    private void assertStillUsable(Reservation r, Cell cell) {
        List<DefrostWindow> later = defrostWindowRepository.findOccurredSince(
                cell.getId(), r.getConfirmedAt(), LocalDateTime.now());
        if (!later.isEmpty()) {
            throw new BizException("该预占确认后库间「" + cell.getCode()
                    + "」已做过化霜，窗口结束旧预占也不自动恢复，须重开一张新预占");
        }
    }

    private void assertMatches(Reservation r, Long cellId, String cargo, Integer qty) {
        if (!r.getCellId().equals(cellId) || !r.getCargo().equals(cargo) || !r.getQty().equals(qty)) {
            throw new BizException("预占 #"+ r.getId() + "（库间/货品/箱数）与本批次对不上，不能入库");
        }
    }

    /**
     * 取一张能顶该待入批次的已确认预占并行级锁住（核销在同事务随后完成）。
     * 指定 reservationId 时按指定的来，否则自动挑库间/货品/箱数对得上的最早一张；
     * 已核销或正被另一笔入库核销的预占在这里拿不到可用状态，绝不会一顶二。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation pickForBatch(Long batchCellId, String cargo, Integer qty, Long reservationId) {
        Cell cell = cellRepository.findById(batchCellId)
                .orElseThrow(() -> new BizException("库间不存在或已删除，不能入库"));
        if (reservationId != null) {
            Reservation r = lockAndValidate(reservationId, cell, batchCellId, cargo, qty);
            return r;
        }
        List<Reservation> usable = reservationRepository.findUsable(batchCellId, cargo, qty);
        for (Reservation candidate : usable) {
            try {
                return lockAndValidate(candidate.getId(), cell, batchCellId, cargo, qty);
            } catch (BizException e) {
                // 被别的批次抢先核销 / 被化霜盖过的，跳过继续找下一张
            }
        }
        throw new BizException("没有库间、货品名、箱数都对得上的可用预占，待入批次不得入库（请先开立并确认预占）");
    }

    /** 锁预占行，再以锁到的最新状态做全部校验 */
    private Reservation lockAndValidate(Long id, Cell cell, Long cellId, String cargo, Integer qty) {
        Reservation r = reservationRepository.findLockedById(id)
                .orElseThrow(() -> new BizException("指定的预占不存在，不能顶本次入库"));
        if (!"已确认".equals(r.getStatus())) {
            throw new BizException("预占 #" + id + "为「" + r.getStatus() + "」，不是可用的已确认预占");
        }
        assertMatches(r, cellId, cargo, qty);
        assertStillUsable(r, cell);
        return r;
    }

    /** 核销：预占已用于某笔批次入库，不能再顶第二笔 */
    @Transactional
    public void writeOff(Reservation r, Long batchId) {
        if (!"已确认".equals(r.getStatus())) {
            throw new BizException("只有「已确认」的预占可以核销");
        }
        r.setStatus("已核销");
        r.setWrittenOffAt(LocalDateTime.now());
        r.setBatchId(batchId);
        reservationRepository.save(r);
    }

    @Transactional
    public void delete(Long id) {
        Reservation r = reservationRepository.findById(id)
                .orElseThrow(() -> new BizException("预占不存在"));
        if ("已核销".equals(r.getStatus())) {
            throw new BizException("已核销预占需留痕，不能删除");
        }
        reservationRepository.deleteById(id);
    }
}
