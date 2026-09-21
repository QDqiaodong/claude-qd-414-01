package com.coldstore.freezer.dto;

public class StockInReq {
    /** 可选：指定用来顶本次入库的已确认预占 id；不传则自动匹配 */
    private Long reservationId;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
}
