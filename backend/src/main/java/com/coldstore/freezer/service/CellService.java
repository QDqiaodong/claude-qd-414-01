package com.coldstore.freezer.service;

import com.coldstore.freezer.dto.BizException;
import com.coldstore.freezer.dto.CellReq;
import com.coldstore.freezer.entity.Cell;
import com.coldstore.freezer.repository.CellRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CellService {

    private final CellRepository cellRepository;

    public CellService(CellRepository cellRepository) {
        this.cellRepository = cellRepository;
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

    @Transactional
    public Cell update(Long id, CellReq req) {
        Cell cell = get(id);
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
            cell.setCapacity(req.getCapacity());
        }
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
