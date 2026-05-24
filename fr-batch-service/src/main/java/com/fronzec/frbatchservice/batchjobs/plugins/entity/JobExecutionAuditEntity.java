/* 2024 */
package com.fronzec.frbatchservice.batchjobs.plugins.entity;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "job_executions_audit")
public class JobExecutionAuditEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Basic
    @Column(name = "job_definition_id", nullable = false)
    private long jobDefinitionId;

    @Basic
    @Column(name = "job_execution_id", nullable = false)
    private long jobExecutionId;

    @Basic
    @Column(name = "job_version", length = 50)
    private String jobVersion;

    @Basic
    @Column(name = "execution_metadata", columnDefinition = "JSON")
    private String executionMetadata;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getJobDefinitionId() {
        return jobDefinitionId;
    }

    public void setJobDefinitionId(long jobDefinitionId) {
        this.jobDefinitionId = jobDefinitionId;
    }

    public long getJobExecutionId() {
        return jobExecutionId;
    }

    public void setJobExecutionId(long jobExecutionId) {
        this.jobExecutionId = jobExecutionId;
    }

    public String getJobVersion() {
        return jobVersion;
    }

    public void setJobVersion(String jobVersion) {
        this.jobVersion = jobVersion;
    }

    public String getExecutionMetadata() {
        return executionMetadata;
    }

    public void setExecutionMetadata(String executionMetadata) {
        this.executionMetadata = executionMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobExecutionAuditEntity that = (JobExecutionAuditEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
