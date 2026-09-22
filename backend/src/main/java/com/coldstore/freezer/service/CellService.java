package com.coldstore.freezer.service;

import com.coldstore.freezer.dto.BizException;
import com.coldstore.freezer.dto.CapacityCommitment;
import com.coldstore.freezer.dto.CellReq;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.repository.CellRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CellService {

    private final CellRepository cellRepository;
    private final CapacityService capacityService;

    public CellService(CellRepository cellRepository, CapacityService capacityService) {
        this.cellRepository = cellRepository;
        this.capacityService = capacityService;
    }

    @Transactional
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
        if (req.getCapacity() == null) {
            throw new BizException("容量不能为空，且必须是不小于 0 的整数（箱）");
        }
        if (req.getCapacity() < 0) {
            throw new BizException("容量不能为负数");
        }
        if (cellRepository.existsByCode(req.getCode())) {
            throw new BizException("库间编号已存在");
        }
        Cell cell = new Cell();
        cell.setCode(req.getCode());
        cell.setName(req.getName());
        cell.setTempZone(req.getTempZone());
        cell.setCapacity(req.getCapacity());
        cell.setDeleted(0);
        return cellRepository.save(cell);
    }

    public List<Cell> list() {
        return cellRepository.findAll();
    }

    public Cell get(Long id) {
        return cellRepository.findById(id)
                .orElseThrow(() -> new BizException("库间不存在"));
    }

    /**
     * 修改库间。容量变更是可审计的容量变更，不靠输入框 min / 按钮禁用防守：
     *
     * 1) 空、负数一律明确驳回，原容量、库存、预占状态一字不动；
     * 2) 新容量不得小于「已在库 + 仍待入 + 已确认未核销预占」的最新合计，
     *    驳回信息逐项列出是哪些批次/预占把容量撑在线下；
     * 3) 与「预占确认」走同一串行点：先对库间行加悲观写锁（READ_COMMITTED），
     *    再在锁后的最新数据上算下限。容量变更先提交：后到的预占确认等锁后看到
     *    新剩余，不够就被准确驳回；预占确认先提交：后到的容量变更等锁后把那张
     *    已确认预占计入下限，当场驳回。绝不会出现「页面提示成功、库里却覆盖了
     *    新预占」或「预占已确认而容量落到承诺量之下」。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Cell update(Long id, CellReq req) {
        // 与预占确认同一把库间行锁：整个校验+写入都在锁后最新状态上完成
        Cell cell = cellRepository.findActiveByIdForUpdate(id)
                .orElseThrow(() -> new BizException("库间不存在或已删除"));
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
        if (req.getCapacity() != null) {
            int newCapacity = req.getCapacity();
            if (newCapacity < 0) {
                throw new BizException("容量不能为负数，本次变更已驳回，库间「"
                        + cell.getCode() + "」仍保持原容量 " + cell.getCapacity() + " 箱");
            }
            // 锁后最新口径：等锁期间对方提交的预占确认 / 批次状态翻转都计入下限
            CapacityCommitment c = capacityService.commitment(cell);
            int floor = c.getFloor();
            if (newCapacity < floor) {
                throw new BizException(buildBelowFloorMessage(cell, newCapacity, c, floor));
            }
            cell.setCapacity(newCapacity);
        } else {
            // 空容量不是「保持不变」：旧页面/直连请求漏传时必须明确报错，
            // 不能让值班误以为容量已按预期保存
            throw new BizException("容量不能为空，本次修改已驳回，库间「"
                    + cell.getCode() + "」仍保持原容量 " + cell.getCapacity() + " 箱");
        }
        return cellRepository.save(cell);
    }

    /** 驳回时逐项点清是哪一笔承诺超出了新容量，值班不用自己翻台账对账 */
    private String buildBelowFloorMessage(Cell cell, int newCapacity, CapacityCommitment c, int floor) {
        StringBuilder sb = new StringBuilder();
        sb.append("容量变更被驳回：库间「").append(cell.getCode()).append("」新容量 ").append(newCapacity)
          .append(" 箱，小于当前承诺下限 ").append(floor)
          .append(" 箱（已在库 ").append(c.getInStockQty())
          .append(" + 仍待入 ").append(c.getPendingQty())
          .append(" + 已确认未核销预占 ").append(c.getReservedQty()).append("）");

        String inStockLines = c.getInStockBatches().stream()
                .map(b -> "批次#" + b.getId() + "「" + b.getCargo() + "」在库 " + b.getQty() + " 箱")
                .collect(Collectors.joining("；"));
        String pendingLines = c.getPendingBatches().stream()
                .map(b -> "批次#" + b.getId() + "「" + b.getCargo() + "」待入 " + b.getQty() + " 箱")
                .collect(Collectors.joining("；"));
        String reservedLines = c.getConfirmedReservations().stream()
                .map(r -> "预占#" + r.getId() + "「" + r.getCargo() + "」已确认 " + r.getQty() + " 箱")
                .collect(Collectors.joining("；"));

        StringBuilder detail = new StringBuilder();
        if (!inStockLines.isEmpty()) {
            detail.append("已在库：").append(inStockLines).append('。');
        }
        if (!pendingLines.isEmpty()) {
            detail.append("仍待入：").append(pendingLines).append('。');
        }
        if (!reservedLines.isEmpty()) {
            detail.append("已确认未核销预占：").append(reservedLines).append('。');
        }
        if (detail.length() > 0) {
            sb.append("。超限承诺：").append(detail);
        }
        sb.append("请先出库、撤销待入或核销/重开预占后再下调；原容量 ")
          .append(cell.getCapacity()).append(" 箱及库存、预占状态均保持不变");
        return sb.toString();
    }

    @Transactional
    public void delete(Long id) {
        if (!cellRepository.existsById(id)) {
            throw new BizException("库间不存在");
        }
        cellRepository.deleteById(id);
    }
}
