package com.fronzec.frbatchservice.datasources;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Configuration
@ConfigurationProperties(prefix = "datasources")
@EnableConfigurationProperties
public class DataSourcesConfig {
  private Map<String, DatasourceConfigNode> configs = new ConcurrentHashMap<>();

  public Map<String, DatasourceConfigNode> getConfigs() {
    return configs;
  }

  public void setConfigs(Map<String, DatasourceConfigNode> configs) {
    this.configs = configs;
  }

  public DataSource buildDatasource(DatasourceConfigNode config, String host, String password) {
    HikariDataSource dataSource = DataSourceBuilder.create().type(HikariDataSource.class).build();
    dataSource.setJdbcUrl(String.format("jdbc:mysql://%s/%s?%s", host, config.dbName(), config.connectionParams()));
    dataSource.setUsername(config.username());
    dataSource.setPassword(password);
    dataSource.setDriverClassName(config.driverClassName());
    dataSource.setPoolName(config.poolName());
    dataSource.setMaximumPoolSize(config.connsMaxPoolSize());
    dataSource.setMinimumIdle(config.connsMinIdle());
    dataSource.setIdleTimeout(config.connsIdleTimeoutMillis());
    dataSource.setMaxLifetime(config.connsMaxLifetimeMillis());
    dataSource.setConnectionTimeout(config.connsTimeoutMillis());
    return dataSource;
  }
}
