/* 2024 */
package com.fronzec.frbatchservice.batchjobs.plugins.repository;

import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobExecutionAuditEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobExecutionAuditRepository
        extends JpaRepository<JobExecutionAuditEntity, Long> {

    List<JobExecutionAuditEntity> findByJobDefinitionId(long jobDefinitionId);
}
