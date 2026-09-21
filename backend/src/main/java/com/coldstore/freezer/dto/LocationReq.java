package com.coldstore.freezer.dto;

public class LocationReq {
    private String code;
    private Long cellId;
    private String status;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public Long getCellId() { return cellId; }
    public void setCellId(Long cellId) { this.cellId = cellId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
