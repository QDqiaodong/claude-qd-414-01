package com.coldstore.freezer.service;

import com.coldstore.freezer.dto.BizException;
import com.coldstore.freezer.dto.LocationReq;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.entity.Location;
import com.coldstore.freezer.repository.CellRepository;
import com.coldstore.freezer.repository.LocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocationService {

    private final LocationRepository locationRepository;
    private final CellRepository cellRepository;

    public LocationService(LocationRepository locationRepository, CellRepository cellRepository) {
        this.locationRepository = locationRepository;
        this.cellRepository = cellRepository;
    }

    private void assertCellValid(Long cellId) {
        if (cellId == null) {
            throw new BizException("归属库间不能为空");
        }
        Cell cell = cellRepository.findById(cellId).orElse(null);
        if (cell == null) {
            throw new BizException("归属库间不存在或已删除");
        }
    }

    public Location create(LocationReq req) {
        if (req.getCode() == null || req.getCode().isBlank()) {
            throw new BizException("货位编号不能为空");
        }
        assertCellValid(req.getCellId());
        String status = (req.getStatus() == null || req.getStatus().isBlank()) ? "空" : req.getStatus();
        if (!status.equals("空") && !status.equals("占用")) {
            throw new BizException("货位状态只能是 空 或 占用");
        }
        Location loc = new Location();
        loc.setCode(req.getCode());
        loc.setCellId(req.getCellId());
        loc.setStatus(status);
        return locationRepository.save(loc);
    }

    public List<Location> list() {
        return locationRepository.findAll();
    }

    public Location get(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new BizException("货位不存在"));
    }

    @Transactional
    public Location update(Long id, LocationReq req) {
        Location loc = get(id);
        if (req.getCode() != null) {
            loc.setCode(req.getCode());
        }
        if (req.getCellId() != null && !req.getCellId().equals(loc.getCellId())) {
            assertCellValid(req.getCellId());
            loc.setCellId(req.getCellId());
        }
        if (req.getStatus() != null) {
            if (!req.getStatus().equals("空") && !req.getStatus().equals("占用")) {
                throw new BizException("货位状态只能是 空 或 占用");
            }
            loc.setStatus(req.getStatus());
        }
        return locationRepository.save(loc);
    }

    @Transactional
    public void delete(Long id) {
        if (!locationRepository.existsById(id)) {
            throw new BizException("货位不存在");
        }
        locationRepository.deleteById(id);
    }
}
