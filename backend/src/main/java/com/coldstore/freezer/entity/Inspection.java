package com.coldstore.freezer.entity;

import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inspection")
@EntityListeners(AuditingEntityListener.class)
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 库间 id */
    @Column(nullable = false)
    private Long cellId;

    /** 巡检日期 */
    @Column(nullable = false)
    private LocalDate checkDate;

    /** 结果：正常 / 异常 */
    @Column(nullable = false)
    private String result;

    /** 备注 */
    private String note;

    /** 实测温度（℃），必填 */
    @Column(name = "measured_temp", nullable = false)
    private Double measuredTemp;

    /** 点检货位（至少一个，且都必须属于该库间）。open-in-view 关闭，故用 EAGER 保证出事务后可序列化 */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "inspection_location", joinColumns = @JoinColumn(name = "inspection_id"))
    @Column(name = "location_id")
    private List<Long> locationIds = new ArrayList<>();

    /** 处置意见：结果「异常」时必填 */
    private String handling;

    /** 异常单闭环标记：0 开着，1 已闭环（正常单恒为 0，不参与判定） */
    @Column(nullable = false)
    private Integer closed = 0;

    /** 闭环时刻 */
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** 闭环说明（选填） */
    @Column(name = "close_note")
    private String closeNote;

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
    public Integer getClosed() { return closed; }
    public void setClosed(Integer closed) { this.closed = closed; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public String getCloseNote() { return closeNote; }
    public void setCloseNote(String closeNote) { this.closeNote = closeNote; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
