/* (C)2024 */
package com.fronzec.frbatchservice.batchjobs.dispatchedgroups;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "dispatched_group")
public class DispatchedGroupEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", nullable = false)
    private long id;

    @Basic
    @Column(name = "uuid_v4", nullable = false, length = 36)
    private String uuidV4;

    @Column(name = "dispatch_status", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private DispatchStatus dispatchStatus = DispatchStatus.UNKNOWN;

    @Basic
    @Column(name = "records_included", nullable = false)
    private int recordsIncluded = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUuidV4() {
        return uuidV4;
    }

    public void setUuidV4(String uuidV4) {
        this.uuidV4 = uuidV4;
    }

    public DispatchStatus getDispatchStatus() {
        return dispatchStatus;
    }

    public void setDispatchStatus(DispatchStatus dispatchStatus) {
        this.dispatchStatus = dispatchStatus;
    }

    public int getRecordsIncluded() {
        return recordsIncluded;
    }

    public void setRecordsIncluded(int recordsIncluded) {
        this.recordsIncluded = recordsIncluded;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DispatchedGroupEntity that = (DispatchedGroupEntity) o;
        return (id == that.id
                && uuidV4.equals(that.uuidV4)
                && dispatchStatus.equals(that.dispatchStatus)
                && recordsIncluded == that.recordsIncluded
                && createdAt.equals(that.createdAt)
                && updatedAt.equals(that.updatedAt));
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, uuidV4, dispatchStatus, recordsIncluded, createdAt, updatedAt);
    }
}
