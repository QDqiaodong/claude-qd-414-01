package com.coldstore.freezer.dto;

import java.time.LocalDate;

public class BatchReq {
    private Long cellId;
    private String cargo;
    private Integer qty;
    private LocalDate batchDate;
    private String status;

    public Long getCellId() { return cellId; }
    public void setCellId(Long cellId) { this.cellId = cellId; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public LocalDate getBatchDate() { return batchDate; }
    public void setBatchDate(LocalDate batchDate) { this.batchDate = batchDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
