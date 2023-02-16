package com.fronzec.singlethreaded.group;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name="groups")
public class GroupedEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "profession", nullable = false)
    private String profession;

    @Column(name = "total_salary", nullable = false)
    private BigDecimal totalSalary;

    @Enumerated
    @Column(name = "dispatch_status", nullable = false)
    private GroupedDispatchStatuses dispatchStatus;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getSnapshotDate() {
        return snapshotDate;
    }

    public void setSnapshotDate(LocalDate snapshotDate) {
        this.snapshotDate = snapshotDate;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public BigDecimal getTotalSalary() {
        return totalSalary;
    }

    public void setTotalSalary(BigDecimal totalSalary) {
        this.totalSalary = totalSalary;
    }

    public GroupedDispatchStatuses getDispatchStatus() {
        return dispatchStatus;
    }

    public void setDispatchStatus(GroupedDispatchStatuses dispatchStatus) {
        this.dispatchStatus = dispatchStatus;
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
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((snapshotDate == null) ? 0 : snapshotDate.hashCode());
        result = prime * result + ((profession == null) ? 0 : profession.hashCode());
        result = prime * result + ((totalSalary == null) ? 0 : totalSalary.hashCode());
        result = prime * result + ((dispatchStatus == null) ? 0 : dispatchStatus.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        GroupedEntity other = (GroupedEntity) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (snapshotDate == null) {
            if (other.snapshotDate != null)
                return false;
        } else if (!snapshotDate.equals(other.snapshotDate))
            return false;
        if (profession == null) {
            if (other.profession != null)
                return false;
        } else if (!profession.equals(other.profession))
            return false;
        if (totalSalary == null) {
            if (other.totalSalary != null)
                return false;
        } else if (!totalSalary.equals(other.totalSalary))
            return false;
        return (dispatchStatus != other.dispatchStatus);
    }

    

}
