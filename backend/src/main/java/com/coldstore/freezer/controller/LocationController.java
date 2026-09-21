package com.coldstore.freezer.controller;

import com.coldstore.freezer.dto.LocationReq;
import com.coldstore.freezer.entity.Location;
import com.coldstore.freezer.service.LocationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping
    public List<Location> list() {
        return locationService.list();
    }

    @GetMapping("/{id}")
    public Location get(@PathVariable Long id) {
        return locationService.get(id);
    }

    @PostMapping
    public Location create(@RequestBody LocationReq req) {
        return locationService.create(req);
    }

    @PutMapping("/{id}")
    public Location update(@PathVariable Long id, @RequestBody LocationReq req) {
        return locationService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        locationService.delete(id);
    }
}
