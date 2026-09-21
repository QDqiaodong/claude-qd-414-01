package com.coldstore.freezer.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 化霜占窗：挂在一个未软删的库间上，占用 [startAt, endAt) 时段。
 * 同一库间重叠时段只允许一扇进行中的占窗；窗口进行中该库间剩余可收箱数按 0 计。
 */
@Entity
@Table(name = "defrost_window")
@EntityListeners(AuditingEntityListener.class)
public class DefrostWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 归属库间 id（仅允许挂在未软删的库间上） */
    @Column(name = "cell_id", nullable = false)
    private Long cellId;

    /** 开始时刻 */
    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    /** 结束时刻 */
    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    /** 占窗事由 */
    @Column(nullable = false)
    private String reason;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 进行中：开始时刻 &le; 当前时刻 &lt; 结束时刻 */
    @Transient
    public boolean isOngoing() {
        LocalDateTime now = LocalDateTime.now();
        return (startAt != null && endAt != null)
                && !now.isBefore(startAt) && now.isBefore(endAt);
    }

    /** 状态：进行中 / 未开始 / 已结束 */
    @Transient
    public String getState() {
        LocalDateTime now = LocalDateTime.now();
        if (startAt != null && !now.isBefore(startAt) && now.isBefore(endAt)) {
            return "进行中";
        }
        if (endAt != null && !now.isBefore(endAt)) {
            return "已结束";
        }
        return "未开始";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCellId() { return cellId; }
    public void setCellId(Long cellId) { this.cellId = cellId; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
