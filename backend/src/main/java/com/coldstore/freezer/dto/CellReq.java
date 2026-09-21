package com.coldstore.freezer.dto;

public class CellReq {
    private String code;
    private String name;
    private String tempZone;
    private Integer capacity;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTempZone() { return tempZone; }
    public void setTempZone(String tempZone) { this.tempZone = tempZone; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
}
