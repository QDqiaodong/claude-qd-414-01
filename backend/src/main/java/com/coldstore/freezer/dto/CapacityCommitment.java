package com.coldstore.freezer.dto;

import com.coldstore.freezer.entity.Batch;
import com.coldstore.freezer.entity.Reservation;

import java.util.List;

/**
 * 容量变更下限的构成（不做化霜截断：化霜只压「剩余可收」，不改既有承诺）：
 *   floor = 已在库箱数 + 仍待入箱数 + 已确认未核销预占箱数。
 * 明细清单用于驳回时点明是哪一笔在库、待入或已确认预占把新容量顶到了线下。
 */
public class CapacityCommitment {
    private final int inStockQty;
    private final int pendingQty;
    private final int reservedQty;
    private final List<Batch> inStockBatches;
    private final List<Batch> pendingBatches;
    private final List<Reservation> confirmedReservations;

    public CapacityCommitment(int inStockQty, int pendingQty, int reservedQty,
                              List<Batch> inStockBatches,
                              List<Batch> pendingBatches,
                              List<Reservation> confirmedReservations) {
        this.inStockQty = inStockQty;
        this.pendingQty = pendingQty;
        this.reservedQty = reservedQty;
        this.inStockBatches = inStockBatches;
        this.pendingBatches = pendingBatches;
        this.confirmedReservations = confirmedReservations;
    }

    public int getInStockQty() { return inStockQty; }
    public int getPendingQty() { return pendingQty; }
    public int getReservedQty() { return reservedQty; }
    public int getFloor() { return inStockQty + pendingQty + reservedQty; }
    public List<Batch> getInStockBatches() { return inStockBatches; }
    public List<Batch> getPendingBatches() { return pendingBatches; }
    public List<Reservation> getConfirmedReservations() { return confirmedReservations; }
}
