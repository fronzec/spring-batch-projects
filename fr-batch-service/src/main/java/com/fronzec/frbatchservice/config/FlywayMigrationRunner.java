/* 2024-2026 */
package com.fronzec.frbatchservice.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Runs Flyway migrations explicitly when the default auto-configuration is
 * bypassed by custom DataSource beans (e.g., {@link
 * com.fronzec.frbatchservice.datasources.main.JpaConfig}).
 *
 * <p>Active only in the {@code local} profile.
 */
@Component
@Profile("local")
class FlywayMigrationRunner implements InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(FlywayMigrationRunner.class);

  private final DataSource dataSource;

  FlywayMigrationRunner(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public void afterPropertiesSet() {
    log.info("Running Flyway migrations...");
    Flyway flyway =
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .load();
    int applied = flyway.migrate().migrationsExecuted;
    log.info("Flyway migration complete — {} migration(s) applied", applied);
  }
}
