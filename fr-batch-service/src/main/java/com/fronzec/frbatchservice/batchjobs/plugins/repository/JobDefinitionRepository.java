/* 2024 */
package com.fronzec.frbatchservice.batchjobs.plugins.repository;

import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobDefinitionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobDefinitionRepository extends JpaRepository<JobDefinitionEntity, Long> {

    Optional<JobDefinitionEntity> findByJobName(String jobName);

    List<JobDefinitionEntity> findByEnabled(Boolean enabled);
}
