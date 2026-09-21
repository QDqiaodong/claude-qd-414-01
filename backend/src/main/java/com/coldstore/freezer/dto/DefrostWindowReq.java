package com.coldstore.freezer.dto;

import java.time.LocalDateTime;

public class DefrostWindowReq {
    private Long cellId;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String reason;

    public Long getCellId() { return cellId; }
    public void setCellId(Long cellId) { this.cellId = cellId; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
