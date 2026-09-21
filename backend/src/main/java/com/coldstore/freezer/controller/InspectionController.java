package com.coldstore.freezer.controller;

import com.coldstore.freezer.dto.InspectionCloseReq;
import com.coldstore.freezer.dto.InspectionReq;
import com.coldstore.freezer.entity.Inspection;
import com.coldstore.freezer.service.InspectionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/inspections")
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping
    public List<Inspection> list(@RequestParam(required = false)
                                 @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return inspectionService.listByDate(date);
    }

    /** 未闭环的异常巡检单：批次页开出库闸、巡检页列待闭环都用它 */
    @GetMapping("/open-abnormal")
    public List<Inspection> openAbnormal(@RequestParam(required = false) Long cellId) {
        return inspectionService.openAbnormal(cellId);
    }

    @GetMapping("/{id}")
    public Inspection get(@PathVariable Long id) {
        return inspectionService.get(id);
    }

    @PostMapping
    public Inspection create(@RequestBody InspectionReq req) {
        return inspectionService.create(req);
    }

    @PutMapping("/{id}")
    public Inspection update(@PathVariable Long id, @RequestBody InspectionReq req) {
        return inspectionService.update(id, req);
    }

    /** 异常单闭环：并发只许落成一次，后到者看到「已闭环」 */
    @PutMapping("/{id}/close")
    public Inspection close(@PathVariable Long id, @RequestBody(required = false) InspectionCloseReq body) {
        return inspectionService.close(id, body == null ? null : body.getCloseNote());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        inspectionService.delete(id);
    }
}
