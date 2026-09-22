package com.coldstore.freezer.service;

import com.coldstore.freezer.dto.BizException;
import com.coldstore.freezer.dto.CapacityView;
import com.coldstore.freezer.dto.CellReq;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.repository.CellRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CellService {

    private final CellRepository cellRepository;
    private final CapacityService capacityService;

    public CellService(CellRepository cellRepository, CapacityService capacityService) {
        this.cellRepository = cellRepository;
        this.capacityService = capacityService;
    }

    public Cell create(CellReq req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new BizException("库间编号不能为空");
        }
        if (req.getName() == null || req.getName().isBlank()) {
            throw new BizException("库间名称不能为空");
        }
        if (req.getTempZone() == null || (!req.getTempZone().equals("冷冻") && !req.getTempZone().equals("冷藏"))) {
            throw new BizException("温区只能是 冷冻 或 冷藏");
        }
        if (cellRepository.existsByCode(req.getCode())) {
            throw new BizException("库间编号已存在");
        }
        validateNewCapacity(req.getCapacity());
        Cell cell = new Cell();
        cell.setCode(req.getCode());
        cell.setName(req.getName());
        cell.setTempZone(req.getTempZone());
        cell.setCapacity(req.getCapacity());
        cell.setDeleted(0);
        return cellRepository.save(cell);
    }

    /** 容量是台账数字：新建库间时不能空、不能为负（此时没有任何已承诺箱数） */
    private void validateNewCapacity(Integer capacity) {
        if (capacity == null) {
            throw new BizException("容量不能为空（箱数必须填写非负整数）");
        }
        if (capacity < 0) {
            throw new BizException("容量不能为负数");
        }
    }

    public List<Cell> list() {
        return cellRepository.findAll();
    }

    public Cell get(Long id) {
        return cellRepository.findById(id)
                .orElseThrow(() -> new BizException("库间不存在"));
    }

    /**
     * 修改库间（容量变更走同一串行点）。
     *
     * 先对库间行加写锁（READ_COMMITTED 保证等锁后读到的是对方已提交的最新值），
     * 与「预占确认」「待入批次开立」等所有会抬高已承诺箱数的动作共用这一把行锁：
     *   - 容量变更先拿锁提交：新容量已落库，后到的预占确认等锁后按新容量重算剩余，
     *     不够就被准确驳回，绝不可能用旧容量把承诺盖过新容量；
     *   - 预占确认先拿锁提交：已确认预占已计入，后到的容量变更等锁后把它算进下限，
     *     低于「在库 + 待入 + 已确认未核销」总和的新容量当场驳回，容量、库存、预占全部不变。
     *
     * 容量校验只以后端锁内的实时口径为准，前端输入框 min、按钮禁用都只是体验层：
     * 旧页面提交、绕过页面直连、负数 / 空容量一律在此明确驳回。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Cell update(Long id, CellReq req) {
        Cell cell = cellRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> new BizException("库间不存在"));
        if (req.getCode() != null && !req.getCode().equals(cell.getCode())) {
            if (cellRepository.existsByCode(req.getCode())) {
                throw new BizException("库间编号已存在");
            }
            cell.setCode(req.getCode());
        }
        if (req.getName() != null) {
            cell.setName(req.getName());
        }
        if (req.getTempZone() != null) {
            if (!req.getTempZone().equals("冷冻") && !req.getTempZone().equals("冷藏")) {
                throw new BizException("温区只能是 冷冻 或 冷藏");
            }
            cell.setTempZone(req.getTempZone());
        }
        if (req.getCapacity() == null) {
            // 空容量不是「不改」：旧页面清空输入框 / 绕过页面直连给 null 都要明确驳回，
            // 保持原容量，绝不允许容量被静默写成 null 后被剩余口径当成 0。
            throw new BizException("容量不能为空（箱数必须填写非负整数），原容量 "
                    + cell.getCapacity() + " 箱保持不变");
        }
        // 锁到的库间行 + 锁后实时汇总，给出可审计的容量下限
        CapacityView view = capacityService.view(cell);
        int floor = view.getInStockQty() + view.getPendingQty() + view.getReservedQty();
        int newCapacity = req.getCapacity();
        if (newCapacity < 0) {
            throw new BizException(String.format(
                    "容量不能为负数；库间「%s」当前承诺下限 %d 箱（在库 %d + 待入 %d + 已确认未核销预占 %d），原容量 %d 箱保持不变",
                    cell.getCode(), floor, view.getInStockQty(), view.getPendingQty(),
                    view.getReservedQty(), view.getCapacity()));
        }
        if (newCapacity < floor) {
            throw new BizException(String.format(
                    "容量变更被驳回：库间「%s」新容量 %d 箱小于当前已承诺箱数下限 %d 箱"
                            + "（已在库 %d + 仍待入 %d + 已确认未核销预占 %d）；"
                            + "请先出库/取消待入/核销或释放预占后再下调，原容量 %d 箱保持不变",
                    cell.getCode(), newCapacity, floor,
                    view.getInStockQty(), view.getPendingQty(), view.getReservedQty(),
                    view.getCapacity()));
        }
        cell.setCapacity(newCapacity);
        return cellRepository.save(cell);
    }

    @Transactional
    public void delete(Long id) {
        if (!cellRepository.existsById(id)) {
            throw new BizException("库间不存在");
        }
        cellRepository.deleteById(id);
    }
}
