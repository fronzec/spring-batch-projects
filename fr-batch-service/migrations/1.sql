
-- MySQL: sequence emulated as a table
-- https://github.com/spring-projects/spring-batch/blob/main/spring-batch-core/src/main/resources/org/springframework/batch/core/migration/6.0/migration-mysql.sql
-- https://github.com/spring-projects/spring-batch/blob/main/spring-batch-core/src/main/resources/org/springframework/batch/core/schema-mysql.sql
-- RENAME TABLE BATCH_JOB_SEQ TO BATCH_JOB_INSTANCE_SEQ;

-- Alternative (MySQL): create new table, migrate value, drop old
-- WARNING: documentation shows an ALTER TABLE, but it doesn't work', use next statement instead
CREATE TABLE BATCH_JOB_INSTANCE_SEQ (
                                      ID BIGINT NOT NULL,
                                      UNIQUE_KEY CHAR(1) NOT NULL,
                                      constraint UNIQUE_KEY_UN unique (UNIQUE_KEY)
) ENGINE=InnoDB;
INSERT INTO BATCH_JOB_INSTANCE_SEQ (ID, UNIQUE_KEY) SELECT ID, UNIQUE_KEY FROM BATCH_JOB_SEQ;


-- DROP TABLE BATCH_JOB_SEQ; WARNING: avoid this, instead rename the table
-- instead of dropping the table, rename it to avoid data loss
RENAME TABLE BATCH_JOB_SEQ TO DEPRECATED_BATCH_JOB_SEQ;
