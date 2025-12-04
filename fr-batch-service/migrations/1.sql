
-- MySQL: sequence emulated as a table
-- https://github.com/spring-projects/spring-batch/blob/main/spring-batch-core/src/main/resources/org/springframework/batch/core/migration/6.0/migration-mysql.sql
-- RENAME TABLE BATCH_JOB_SEQ TO BATCH_JOB_INSTANCE_SEQ;

-- Alternative (MySQL): create new table, migrate value, drop old
CREATE TABLE BATCH_JOB_INSTANCE_SEQ (ID BIGINT NOT NULL);
INSERT INTO BATCH_JOB_INSTANCE_SEQ (ID) SELECT ID FROM BATCH_JOB_SEQ;
-- DROP TABLE BATCH_JOB_SEQ;
-- instead of dropping the table, rename it to avoid data loss
RENAME TABLE BATCH_JOB_SEQ TO DEPRECATED_BATCH_JOB_SEQ;
