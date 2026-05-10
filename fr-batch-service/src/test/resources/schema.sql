CREATE TABLE job_definitions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_name VARCHAR(255) UNIQUE NOT NULL,
    display_name VARCHAR(255),
    description TEXT,
    version VARCHAR(50) NOT NULL,
    jar_file_path VARCHAR(500) NOT NULL,
    jar_checksum VARCHAR(64) NOT NULL,
    main_class_name VARCHAR(255) NOT NULL,
    enabled BOOLEAN DEFAULT true,
    auto_start BOOLEAN DEFAULT false,
    load_status VARCHAR(50),
    load_error TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100)
);

CREATE INDEX idx_job_name ON job_definitions (job_name);
CREATE INDEX idx_enabled ON job_definitions (enabled);
CREATE INDEX idx_load_status ON job_definitions (load_status);

CREATE TABLE job_parameters_template (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_definition_id BIGINT NOT NULL,
    param_key VARCHAR(100) NOT NULL,
    param_type VARCHAR(50) NOT NULL,
    default_value VARCHAR(255),
    required BOOLEAN DEFAULT false,
    description TEXT,
    validation_regex VARCHAR(500),
    CONSTRAINT fk_job_parameters_template_job_definition_id
        FOREIGN KEY (job_definition_id)
        REFERENCES job_definitions(id)
        ON DELETE CASCADE,
    CONSTRAINT uk_job_param UNIQUE (job_definition_id, param_key)
);

CREATE TABLE job_executions_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    job_definition_id BIGINT NOT NULL,
    job_execution_id BIGINT NOT NULL,
    job_version VARCHAR(50),
    execution_metadata JSON,
    CONSTRAINT fk_job_executions_audit_job_definition_id
        FOREIGN KEY (job_definition_id)
        REFERENCES job_definitions(id)
);
