/* 2024 */
package com.fronzec.frbatchservice.batchjobs.plugins.repository;

import com.fronzec.frbatchservice.batchjobs.plugins.entity.JobParameterTemplateEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobParameterTemplateRepository
        extends JpaRepository<JobParameterTemplateEntity, Long> {

    List<JobParameterTemplateEntity> findByJobDefinitionId(long jobDefinitionId);

    void deleteByJobDefinitionId(long jobDefinitionId);
}
