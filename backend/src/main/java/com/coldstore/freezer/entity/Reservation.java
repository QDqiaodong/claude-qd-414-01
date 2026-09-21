package com.coldstore.freezer.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 入库预占：库间 + 货品 + 箱数 + 计划入库日。
 * 状态机：待确认 → 已确认 → 已核销（用于顶一笔待入批次入库）。
 * 只有「已确认」的预占占用库间剩余可收箱数；「已核销」不能再顶第二笔批次。
 */
@Entity
@Table(name = "reservation")
@EntityListeners(AuditingEntityListener.class)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 归属库间 id */
    @Column(name = "cell_id", nullable = false)
    private Long cellId;

    /** 货品名（与批次货品名精确一致才可顶用） */
    @Column(nullable = false)
    private String cargo;

    /** 预占箱数，必须大于 0 */
    @Column(nullable = false)
    private Integer qty;

    /** 计划入库日 */
    @Column(name = "plan_date", nullable = false)
    private LocalDate planDate;

    /** 状态：待确认 / 已确认 / 已核销 */
    @Column(nullable = false)
    private String status;

    /** 确认时刻：用于判断确认后是否被新的化霜占窗盖过 */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** 核销时刻 */
    @Column(name = "written_off_at")
    private LocalDateTime writtenOffAt;

    /** 顶用的入库批次 id（核销时回填） */
    @Column(name = "batch_id")
    private Long batchId;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCellId() { return cellId; }
    public void setCellId(Long cellId) { this.cellId = cellId; }
    public String getCargo() { return cargo; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public LocalDateTime getWrittenOffAt() { return writtenOffAt; }
    public void setWrittenOffAt(LocalDateTime writtenOffAt) { this.writtenOffAt = writtenOffAt; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
