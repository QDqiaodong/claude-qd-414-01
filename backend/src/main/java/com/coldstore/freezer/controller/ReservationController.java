package com.coldstore.freezer.controller;

import com.coldstore.freezer.dto.ReservationReq;
import com.coldstore.freezer.entity.Reservation;
import com.coldstore.freezer.service.ReservationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    /** 列表，可按 ?cellId= 过滤（含已确认未核销、已核销历史，软删库间的也仍可见） */
    @GetMapping
    public List<Reservation> list(@RequestParam(required = false) Long cellId) {
        return reservationService.list(cellId);
    }

    @GetMapping("/{id}")
    public Reservation get(@PathVariable Long id) {
        return reservationService.get(id);
    }

    /** 开立预占（待确认，不占剩余箱数） */
    @PostMapping
    public Reservation create(@RequestBody ReservationReq req) {
        return reservationService.create(req);
    }

    /** 确认预占：占剩余箱数；化霜进行中或剩余不足时 400 驳回 */
    @PutMapping("/{id}/confirm")
    public Reservation confirm(@PathVariable Long id) {
        return reservationService.confirm(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        reservationService.delete(id);
    }
}
