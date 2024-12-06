package com.fronzec.frbatchservice.datasources;

public record DatasourceConfigNode(
        /*
        For cloud environments this is an env var name
         */
        String host,
        String dbName,
        String username,
        /*
        For cloud environments this is an env var name
         */
        String password,
        String connectionParams,
        String driverClassName,
        String poolName,
        Integer connsMinIdle,
        Integer connsIdleTimeoutMillis,
        Integer connsMaxPoolSize,
        Integer connsMaxLifetimeMillis,
        Integer connsTimeoutMillis
) {

}
