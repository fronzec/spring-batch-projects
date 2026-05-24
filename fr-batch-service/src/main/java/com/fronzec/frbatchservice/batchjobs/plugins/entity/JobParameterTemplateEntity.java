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
@Table(name = "job_parameters_template")
public class JobParameterTemplateEntity {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "id", nullable = false)
    private Long id;

    @Basic
    @Column(name = "job_definition_id", nullable = false)
    private long jobDefinitionId;

    @Basic
    @Column(name = "param_key", nullable = false, length = 100)
    private String paramKey;

    @Basic
    @Column(name = "param_type", nullable = false, length = 50)
    private String paramType;

    @Basic
    @Column(name = "default_value", length = 255)
    private String defaultValue;

    @Basic
    @Column(name = "required")
    private Boolean required = false;

    @Basic
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Basic
    @Column(name = "validation_regex", length = 500)
    private String validationRegex;

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

    public String getParamKey() {
        return paramKey;
    }

    public void setParamKey(String paramKey) {
        this.paramKey = paramKey;
    }

    public String getParamType() {
        return paramType;
    }

    public void setParamType(String paramType) {
        this.paramType = paramType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getValidationRegex() {
        return validationRegex;
    }

    public void setValidationRegex(String validationRegex) {
        this.validationRegex = validationRegex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JobParameterTemplateEntity that = (JobParameterTemplateEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : System.identityHashCode(this);
    }
}
