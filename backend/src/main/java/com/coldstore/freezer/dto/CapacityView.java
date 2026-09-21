package com.coldstore.freezer.dto;

/**
 * 库间剩余可收箱数视图（质检口径）。
 * 剩余 = 库间容量 - 已在库箱数 - 仍待入箱数 - 已确认未核销预占箱数；
 * 撞上进行中的化霜占窗时 remaining 直接为 0。
 */
public class CapacityView {
    private Long cellId;
    private String code;
    private String name;
    private String tempZone;
    private Integer capacity;
    private Integer inStockQty;
    private Integer pendingQty;
    private Integer reservedQty;
    private boolean defrostOngoing;
    private Integer remaining;

    public CapacityView(Long cellId, String code, String name, String tempZone, Integer capacity,
                        Integer inStockQty, Integer pendingQty, Integer reservedQty,
                        boolean defrostOngoing, Integer remaining) {
        this.cellId = cellId;
        this.code = code;
        this.name = name;
        this.tempZone = tempZone;
        this.capacity = capacity;
        this.inStockQty = inStockQty;
        this.pendingQty = pendingQty;
        this.reservedQty = reservedQty;
        this.defrostOngoing = defrostOngoing;
        this.remaining = remaining;
    }

    public Long getCellId() { return cellId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getTempZone() { return tempZone; }
    public Integer getCapacity() { return capacity; }
    public Integer getInStockQty() { return inStockQty; }
    public Integer getPendingQty() { return pendingQty; }
    public Integer getReservedQty() { return reservedQty; }
    public boolean isDefrostOngoing() { return defrostOngoing; }
    public Integer getRemaining() { return remaining; }
}
