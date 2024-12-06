/* 2024 */
package com.fronzec.frbatchservice.datasources.main;

import com.fronzec.frbatchservice.datasources.DataSourcesConfig;
import com.fronzec.frbatchservice.datasources.DatasourceConfigNode;
import com.fronzec.frbatchservice.web.JobController;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
public class JpaConfig {

    private final Logger logger = LoggerFactory.getLogger(JpaConfig.class);

    @Autowired
    DataSourcesConfig dataSourcesConfig;

    @Bean(name =  "dataSource")
    @Profile({"production"})
    public DataSource mainDatasourceProduction() {
        logger.info("Using production datasource [main]");
        String host;
        String password;
        String configKey = "main";
        DatasourceConfigNode config = dataSourcesConfig.getConfigs().get(configKey);
        host = System.getenv(config.host());
        password = System.getenv(config.password());
        return dataSourcesConfig.buildDatasource(config, host, password);
    }

    @Bean(name =  "dataSource")
    @ConfigurationProperties(prefix = "datasources.main")// NOTE for local development we access directly to the keys
    public DataSource mainDatasourceDevelopment() {
        logger.info("Using development datasource [main]");
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.fronzec.frbatchservice"); // Change to target entity packages

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);
        em.setJpaProperties(hibernateProperties());

        return em;
    }

    @Bean
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory);
        return transactionManager;
    }

    private Properties hibernateProperties() {
        Properties properties = new Properties();
        properties.setProperty("hibernate.hbm2ddl.auto", "none");
        return properties;
    }
}
