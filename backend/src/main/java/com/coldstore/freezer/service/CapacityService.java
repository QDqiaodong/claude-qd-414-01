package com.coldstore.freezer.service;

import com.coldstore.freezer.dto.BizException;
import com.coldstore.freezer.dto.CapacityCommitment;
import com.coldstore.freezer.dto.CapacityView;
import com.coldstore.freezer.entity.Batch;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.entity.Reservation;
import com.coldstore.freezer.repository.BatchRepository;
import com.coldstore.freezer.repository.CellRepository;
import com.coldstore.freezer.repository.DefrostWindowRepository;
import com.coldstore.freezer.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 剩余可收箱数统一口径（唯一计算入口，货位热力和容量数字都不能绕过它）：
 *   剩余 = 容量 - 已在库箱数 - 仍待入箱数 - 已确认未核销预占箱数
 * 库间存在进行中的化霜占窗时，剩余直接按 0。
 */
@Service
public class CapacityService {

    private final CellRepository cellRepository;
    private final BatchRepository batchRepository;
    private final ReservationRepository reservationRepository;
    private final DefrostWindowRepository defrostWindowRepository;

    public CapacityService(CellRepository cellRepository,
                           BatchRepository batchRepository,
                           ReservationRepository reservationRepository,
                           DefrostWindowRepository defrostWindowRepository) {
        this.cellRepository = cellRepository;
        this.batchRepository = batchRepository;
        this.reservationRepository = reservationRepository;
        this.defrostWindowRepository = defrostWindowRepository;
    }

    /** 库间当前是否有进行中的化霜占窗 */
    public boolean isDefrostOngoing(Long cellId) {
        LocalDateTime now = LocalDateTime.now();
        return !defrostWindowRepository.findOngoing(cellId, now).isEmpty();
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }

    /** 按口径计算一个未软删库间的剩余可收箱数；化霜进行中一律 0 */
    public int remaining(Cell cell) {
        if (isDefrostOngoing(cell.getId())) {
            return 0;
        }
        int capacity = cell.getCapacity() == null ? 0 : cell.getCapacity();
        int inStock = nz(batchRepository.sumInStockQtyByCellId(cell.getId()));
        int pending = nz(batchRepository.sumPendingQtyByCellId(cell.getId()));
        int reserved = nz(reservationRepository.sumConfirmedQtyByCellId(cell.getId()));
        return Math.max(0, capacity - inStock - pending - reserved);
    }

    public int remaining(Long cellId) {
        Cell cell = cellRepository.findById(cellId)
                .orElseThrow(() -> new BizException("库间不存在或已删除"));
        return remaining(cell);
    }

    /**
     * 容量变更下限的最新构成：已在库 + 仍待入 + 已确认未核销预占。
     * 必须在拿到库间行锁之后调用（READ_COMMITTED），等锁期间对方提交的预占确认、
     * 批次入库/撤销都会在这里读到，绝不能用调用方打开旧页面时的旧口径判断。
     */
    public CapacityCommitment commitment(Cell lockedCell) {
        Long cellId = lockedCell.getId();
        int inStock = nz(batchRepository.sumInStockQtyByCellId(cellId));
        int pending = nz(batchRepository.sumPendingQtyByCellId(cellId));
        int reserved = nz(reservationRepository.sumConfirmedQtyByCellId(cellId));
        List<Batch> inStockBatches = batchRepository.findByCellIdAndStatus(cellId, "在库");
        List<Batch> pendingBatches = batchRepository.findByCellIdAndStatus(cellId, "待入");
        List<Reservation> confirmed = reservationRepository.findByCellIdAndStatusOrderByIdAsc(cellId, "已确认");
        return new CapacityCommitment(inStock, pending, reserved,
                inStockBatches, pendingBatches, confirmed);
    }

    @Transactional(readOnly = true)
    public CapacityView view(Cell cell) {
        int capacity = cell.getCapacity() == null ? 0 : cell.getCapacity();
        int inStock = nz(batchRepository.sumInStockQtyByCellId(cell.getId()));
        int pending = nz(batchRepository.sumPendingQtyByCellId(cell.getId()));
        int reserved = nz(reservationRepository.sumConfirmedQtyByCellId(cell.getId()));
        boolean ongoing = isDefrostOngoing(cell.getId());
        int remaining = ongoing ? 0 : Math.max(0, capacity - inStock - pending - reserved);
        return new CapacityView(cell.getId(), cell.getCode(), cell.getName(), cell.getTempZone(),
                capacity, inStock, pending, reserved, ongoing, remaining);
    }

    @Transactional(readOnly = true)
    public CapacityView view(Long cellId) {
        Cell cell = cellRepository.findById(cellId)
                .orElseThrow(() -> new BizException("库间不存在或已删除"));
        return view(cell);
    }

    @Transactional(readOnly = true)
    public List<CapacityView> viewAll() {
        return cellRepository.findAll().stream().map(this::view).toList();
    }
}
