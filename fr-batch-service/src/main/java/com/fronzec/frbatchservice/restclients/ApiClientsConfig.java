/* 2024-2025 */
package com.fronzec.frbatchservice.restclients;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ApiClientsConfig {

    /**
     * Provides a Spring-managed RestClient bean for making HTTP requests to external services.
     *
     * @return a RestClient instance for performing HTTP requests
     */
    @Bean
    public RestClient restTemplateApiClient() {
        return RestClient.builder().build();
    }
}