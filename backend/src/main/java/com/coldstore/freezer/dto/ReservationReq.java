package com.coldstore.freezer.dto;

import java.time.LocalDate;

public class ReservationReq {
    private Long cellId;
    private String cargo;
    private Integer qty;
    private LocalDate planDate;

    public Long getCellId() { return cellId; }
    public void setCellId(Long cellId) { this.cellId = cellId; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }
}
