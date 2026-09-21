package com.coldstore.freezer.service;

import com.coldstore.freezer.dto.BizException;
import com.coldstore.freezer.dto.DefrostWindowReq;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.entity.DefrostWindow;
import com.coldstore.freezer.repository.CellRepository;
import com.coldstore.freezer.repository.DefrostWindowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class DefrostWindowService {

    /** 质检口径的占窗最短时长（分钟），值班长想压短也以这个下限为准 */
    private static final int MIN_MINUTES_FROZEN = 40;
    private static final int MIN_MINUTES_CHILLED = 20;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final DefrostWindowRepository windowRepository;
    private final CellRepository cellRepository;

    public DefrostWindowService(DefrostWindowRepository windowRepository,
                                CellRepository cellRepository) {
        this.windowRepository = windowRepository;
        this.cellRepository = cellRepository;
    }

    /** 开立化霜占窗：校验库间、时段、质检时长下限，重叠的后开扇当场驳回 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DefrostWindow create(DefrostWindowReq req) {
        if (req.getCellId() == null) {
            throw new BizException("归属库间不能为空");
        }
        // 锁库间行：两扇占窗抢同一库间时在此串行，重叠判定不会漏
        Cell cell = cellRepository.findActiveByIdForUpdate(req.getCellId()).orElse(null);
        if (cell == null) {
            throw new BizException("库间不存在或已软删，不能开立化霜占窗");
        }
        LocalDateTime startAt = req.getStartAt();
        LocalDateTime endAt = req.getEndAt();
        if (startAt == null || endAt == null) {
            throw new BizException("开始时刻和结束时刻都必须填写");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BizException("结束时刻必须晚于开始时刻");
        }
        if (req.getReason() == null || req.getReason().isBlank()) {
            throw new BizException("占窗事由不能为空");
        }
        int minMinutes = "冷冻".equals(cell.getTempZone()) ? MIN_MINUTES_FROZEN : MIN_MINUTES_CHILLED;
        long minutes = Duration.between(startAt, endAt).toMinutes();
        if (minutes < minMinutes) {
            throw new BizException(String.format(
                    "%s库化霜占窗不得短于 %d 分钟（质检口径），本次仅 %d 分钟，已驳回",
                    cell.getTempZone(), minMinutes, minutes));
        }
        List<DefrostWindow> overlaps = windowRepository.findOverlapping(cell.getId(), startAt, endAt);
        if (!overlaps.isEmpty()) {
            DefrostWindow w = overlaps.get(0);
            throw new BizException(String.format(
                    "库间「%s」在 %s～%s 已有一扇化霜占窗，当前申请与之时间重叠，后开的一扇当场驳回",
                    cell.getCode(), w.getStartAt().format(FMT), w.getEndAt().format(FMT)));
        }
        DefrostWindow w = new DefrostWindow();
        w.setCellId(cell.getId());
        w.setStartAt(startAt);
        w.setEndAt(endAt);
        w.setReason(req.getReason().trim());
        return windowRepository.save(w);
    }

    @Transactional(readOnly = true)
    public List<DefrostWindow> list(Long cellId) {
        if (cellId == null) {
            return windowRepository.findAll();
        }
        return windowRepository.findByCellIdOrderByStartAtAsc(cellId);
    }

    @Transactional(readOnly = true)
    public DefrostWindow get(Long id) {
        return windowRepository.findById(id)
                .orElseThrow(() -> new BizException("化霜占窗不存在"));
    }

    @Transactional
    public void delete(Long id) {
        if (!windowRepository.existsById(id)) {
            throw new BizException("化霜占窗不存在");
        }
        windowRepository.deleteById(id);
    }
}
