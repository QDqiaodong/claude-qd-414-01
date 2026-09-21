package com.coldstore.freezer.dto;

import java.time.LocalDate;
import java.util.List;

public class InspectionReq {
    private Long cellId;
    private LocalDate checkDate;
    private String result;
    private String note;
    /** 实测温度（℃），必填 */
    private Double measuredTemp;
    /** 点检货位 id 列表，至少一个且都属于该库间 */
    private List<Long> locationIds;
    /** 处置意见：异常必填 */
    private String handling;

    public Long getCellId() { return cellId; }
    public void setCellId(Long cellId) { this.cellId = cellId; }
    public LocalDate getCheckDate() { return checkDate; }
    public void setCheckDate(LocalDate checkDate) { this.checkDate = checkDate; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Double getMeasuredTemp() { return measuredTemp; }
    public void setMeasuredTemp(Double measuredTemp) { this.measuredTemp = measuredTemp; }
    public List<Long> getLocationIds() { return locationIds; }
    public void setLocationIds(List<Long> locationIds) { this.locationIds = locationIds; }
    public String getHandling() { return handling; }
    public void setHandling(String handling) { this.handling = handling; }
}
