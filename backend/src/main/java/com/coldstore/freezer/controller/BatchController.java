package com.coldstore.freezer.controller;

import com.coldstore.freezer.dto.BatchReq;
import com.coldstore.freezer.dto.StockInReq;
import com.coldstore.freezer.entity.Batch;
import com.coldstore.freezer.service.BatchService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @GetMapping
    public List<Batch> list() {
        return batchService.list();
    }

    @GetMapping("/{id}")
    public Batch get(@PathVariable Long id) {
        return batchService.get(id);
    }

    @PostMapping
    public Batch create(@RequestBody BatchReq req) {
        return batchService.create(req);
    }

    @PutMapping("/{id}")
    public Batch update(@PathVariable Long id, @RequestBody BatchReq req) {
        return batchService.update(id, req);
    }

    /**
     * 入库：必须有匹配的已确认预占。
     * 支持 body 传 {"reservationId": 12}，也支持 ?reservationId=12，都不传则自动匹配。
     */
    @PutMapping("/{id}/stock-in")
    public Batch stockIn(@PathVariable Long id,
                         @RequestBody(required = false) StockInReq body,
                         @RequestParam(required = false) Long reservationId) {
        Long rid = reservationId != null ? reservationId
                : (body != null ? body.getReservationId() : null);
        return batchService.stockIn(id, rid);
    }

    @PutMapping("/{id}/stock-out")
    public Batch stockOut(@PathVariable Long id) {
        return batchService.stockOut(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        batchService.delete(id);
    }
}
