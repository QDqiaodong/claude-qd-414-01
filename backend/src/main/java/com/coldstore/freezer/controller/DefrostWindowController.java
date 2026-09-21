package com.coldstore.freezer.controller;

import com.coldstore.freezer.dto.DefrostWindowReq;
import com.coldstore.freezer.entity.DefrostWindow;
import com.coldstore.freezer.service.DefrostWindowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/defrost-windows")
public class DefrostWindowController {

    private final DefrostWindowService windowService;

    public DefrostWindowController(DefrostWindowService windowService) {
        this.windowService = windowService;
    }

    /** 列表，可按 ?cellId= 过滤 */
    @GetMapping
    public List<DefrostWindow> list(@RequestParam(required = false) Long cellId) {
        return windowService.list(cellId);
    }

    @GetMapping("/{id}")
    public DefrostWindow get(@PathVariable Long id) {
        return windowService.get(id);
    }

    /** 开立占窗：时长不足 / 与既有占窗重叠都会被当场驳回（400） */
    @PostMapping
    public DefrostWindow create(@RequestBody DefrostWindowReq req) {
        return windowService.create(req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        windowService.delete(id);
    }
}
