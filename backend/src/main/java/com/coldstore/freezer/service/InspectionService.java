package com.coldstore.freezer.service;

import com.coldstore.freezer.dto.BizException;
import com.coldstore.freezer.dto.InspectionReq;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.entity.Inspection;
import com.coldstore.freezer.entity.Location;
import com.coldstore.freezer.repository.CellRepository;
import com.coldstore.freezer.repository.InspectionRepository;
import com.coldstore.freezer.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class InspectionService {

    /** 质检口径的温区上下限（℃）：实测温度越界必须记「异常」并写处置意见 */
    public static final double FROZEN_MIN = -25.0, FROZEN_MAX = -15.0;
    public static final double CHILLED_MIN = 0.0, CHILLED_MAX = 8.0;

    private final InspectionRepository inspectionRepository;
    private final CellRepository cellRepository;
    private final LocationRepository locationRepository;

    public InspectionService(InspectionRepository inspectionRepository,
                             CellRepository cellRepository,
                             LocationRepository locationRepository) {
        this.inspectionRepository = inspectionRepository;
        this.cellRepository = cellRepository;
        this.locationRepository = locationRepository;
    }

    /** 温区上下限文案，报错/提示统一口径 */
    public static String rangeText(String tempZone) {
        return "冷冻".equals(tempZone)
                ? String.format("%.1f ~ %.1f℃", FROZEN_MIN, FROZEN_MAX)
                : String.format("%.1f ~ %.1f℃", CHILLED_MIN, CHILLED_MAX);
    }

    private static boolean outOfRange(String tempZone, double temp) {
        if ("冷冻".equals(tempZone)) {
            return temp < FROZEN_MIN || temp > FROZEN_MAX;
        }
        return temp < CHILLED_MIN || temp > CHILLED_MAX;
    }

    /** 新巡检只能挂在未软删的库间上（findById 走 @Where(deleted=0)，软删即查不到） */
    private Cell requireActiveCell(Long cellId) {
        if (cellId == null) {
            throw new BizException("库间不能为空");
        }
        Cell cell = cellRepository.findById(cellId).orElse(null);
        if (cell == null) {
            throw new BizException("库间不存在或已软删，不能开新巡检");
        }
        return cell;
    }

    private void assertResult(String result) {
        if (result == null || (!result.equals("正常") && !result.equals("异常"))) {
            throw new BizException("巡检结果只能是 正常 或 异常");
        }
    }

    /** 至少点一个货位，且每个货位都必须真实存在、属于该库间 */
    private List<Long> assertLocations(Long cellId, List<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            throw new BizException("巡检必须至少点选一个货位");
        }
        List<Long> distinct = locationIds.stream().distinct().toList();
        List<Location> locs = locationRepository.findAllById(distinct);
        if (locs.size() != distinct.size()) {
            throw new BizException("所选货位不存在或已删除");
        }
        for (Location loc : locs) {
            if (!loc.getCellId().equals(cellId)) {
                throw new BizException("货位「" + loc.getCode() + "」不属于该库间，不能点选");
            }
        }
        return distinct;
    }

    private Double assertMeasuredTemp(Double measuredTemp) {
        if (measuredTemp == null) {
            throw new BizException("必须填写实测温度");
        }
        return measuredTemp;
    }

    /**
     * 质检按温区卡：实测温度越界必须记「异常」并写处置意见，否则当场驳回；
     * 任何「异常」单都必须有处置意见（未越界的异常，如设备异响，同样要交代处置）。
     */
    private void assertZoneRule(Cell cell, Double measuredTemp, String result, String handling) {
        if (outOfRange(cell.getTempZone(), measuredTemp) && !"异常".equals(result)) {
            throw new BizException(String.format(
                    "实测温度 %.1f℃ 超出%s库上下限（%s），必须记为「异常」并填写处置意见，已当场驳回",
                    measuredTemp, cell.getTempZone(), rangeText(cell.getTempZone())));
        }
        if ("异常".equals(result) && (handling == null || handling.isBlank())) {
            throw new BizException("异常巡检必须填写处置意见");
        }
    }

    @Transactional
    public Inspection create(InspectionReq req) {
        Cell cell = requireActiveCell(req.getCellId());
        if (req.getCheckDate() == null) {
            throw new BizException("巡检日期不能为空");
        }
        List<Long> locationIds = assertLocations(cell.getId(), req.getLocationIds());
        Double measuredTemp = assertMeasuredTemp(req.getMeasuredTemp());
        assertResult(req.getResult());
        assertZoneRule(cell, measuredTemp, req.getResult(), req.getHandling());
        if (inspectionRepository.existsByCellIdAndCheckDate(cell.getId(), req.getCheckDate())) {
            throw new BizException("该库间当天已有巡检记录");
        }
        Inspection ins = new Inspection();
        ins.setCellId(cell.getId());
        ins.setCheckDate(req.getCheckDate());
        ins.setResult(req.getResult());
        ins.setNote(req.getNote());
        ins.setMeasuredTemp(measuredTemp);
        ins.setLocationIds(locationIds);
        ins.setHandling(req.getHandling());
        ins.setClosed(0);
        return inspectionRepository.save(ins);
    }

    public List<Inspection> list() {
        return inspectionRepository.findAll();
    }

    public List<Inspection> listByDate(java.time.LocalDate date) {
        if (date == null) {
            return inspectionRepository.findAll();
        }
        return inspectionRepository.findByCheckDate(date);
    }

    /** 未闭环的异常巡检单（开出库闸和巡检页共用口径） */
    public List<Inspection> openAbnormal(Long cellId) {
        if (cellId == null) {
            return inspectionRepository.findByResultAndClosed("异常", 0);
        }
        return inspectionRepository.findByCellIdAndResultAndClosed(cellId, "异常", 0);
    }

    public Inspection get(Long id) {
        return inspectionRepository.findById(id)
                .orElseThrow(() -> new BizException("巡检记录不存在"));
    }

    @Transactional
    public Inspection update(Long id, InspectionReq req) {
        Inspection ins = get(id);
        if ("异常".equals(ins.getResult()) && Integer.valueOf(1).equals(ins.getClosed())) {
            throw new BizException("该异常巡检单已闭环，留痕记录不能再修改");
        }
        Cell cell = cellRepository.findById(ins.getCellId()).orElse(null);
        if (cell == null) {
            throw new BizException("库间已软删，该巡检单不能再修改（异常单请走闭环）");
        }
        if (req.getCellId() != null && !req.getCellId().equals(ins.getCellId())) {
            cell = requireActiveCell(req.getCellId());
            ins.setCellId(cell.getId());
        }
        if (req.getCheckDate() != null) {
            ins.setCheckDate(req.getCheckDate());
        }
        if ("异常".equals(ins.getResult()) && "正常".equals(req.getResult())) {
            throw new BizException("异常巡检单未闭环，不能改记为「正常」，请先处置并闭环");
        }
        if (req.getResult() != null) {
            assertResult(req.getResult());
            ins.setResult(req.getResult());
        }
        if (req.getLocationIds() != null) {
            ins.setLocationIds(assertLocations(ins.getCellId(), req.getLocationIds()));
        } else {
            // 换了库间但没重选货位时，旧货位多半对不上，合并值必须仍然合法
            ins.setLocationIds(assertLocations(ins.getCellId(), ins.getLocationIds()));
        }
        if (req.getMeasuredTemp() != null) {
            ins.setMeasuredTemp(req.getMeasuredTemp());
        }
        assertMeasuredTemp(ins.getMeasuredTemp());
        if (req.getNote() != null) {
            ins.setNote(req.getNote());
        }
        if (req.getHandling() != null) {
            ins.setHandling(req.getHandling());
        }
        assertZoneRule(cell, ins.getMeasuredTemp(), ins.getResult(), ins.getHandling());
        return inspectionRepository.save(ins);
    }

    /**
     * 异常单闭环：行级锁串行化，两人同时点闭环只许落成一次，
     * 后点的那下等锁后看到单子已关上，报「已闭环」当场驳回。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Inspection close(Long id, String closeNote) {
        Inspection ins = inspectionRepository.findLockedById(id)
                .orElseThrow(() -> new BizException("巡检记录不存在"));
        if (!"异常".equals(ins.getResult())) {
            throw new BizException("正常巡检无需闭环");
        }
        if (Integer.valueOf(1).equals(ins.getClosed())) {
            throw new BizException("该异常巡检单已闭环，不能重复闭环");
        }
        ins.setClosed(1);
        ins.setClosedAt(LocalDateTime.now());
        if (closeNote != null && !closeNote.isBlank()) {
            ins.setCloseNote(closeNote.trim());
        }
        return inspectionRepository.save(ins);
    }

    /** 异常单只能闭环留痕，不能删除；正常巡检可撤销 */
    @Transactional
    public void delete(Long id) {
        Inspection ins = get(id);
        if ("异常".equals(ins.getResult())) {
            throw new BizException(Integer.valueOf(1).equals(ins.getClosed())
                    ? "已闭环的异常巡检单需留痕，不能删除"
                    : "异常巡检单未闭环，不能删除，请先处置并闭环");
        }
        inspectionRepository.deleteById(id);
    }
}
