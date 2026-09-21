package com.coldstore.freezer.controller;

import com.coldstore.freezer.dto.CapacityView;
import com.coldstore.freezer.dto.CellReq;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.service.CapacityService;
import com.coldstore.freezer.service.CellService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cells")
public class CellController {

    private final CellService cellService;
    private final CapacityService capacityService;

    public CellController(CellService cellService, CapacityService capacityService) {
        this.cellService = cellService;
        this.capacityService = capacityService;
    }

    @GetMapping
    public List<Cell> list() {
        return cellService.list();
    }

    @GetMapping("/{id}")
    public Cell get(@PathVariable Long id) {
        return cellService.get(id);
    }

    /** 全库剩余可收箱数口径：容量-在库-待入-已确认预占，化霜进行中直接 0 */
    @GetMapping("/capacity/overview")
    public List<CapacityView> capacityOverview() {
        return capacityService.viewAll();
    }

    /** 单个库间的剩余可收箱数明细 */
    @GetMapping("/{id}/capacity")
    public CapacityView capacity(@PathVariable Long id) {
        return capacityService.view(id);
    }

    @PostMapping
    public Cell create(@RequestBody CellReq req) {
        return cellService.create(req);
    }

    @PutMapping("/{id}")
    public Cell update(@PathVariable Long id, @RequestBody CellReq req) {
        return cellService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        cellService.delete(id);
    }
}
