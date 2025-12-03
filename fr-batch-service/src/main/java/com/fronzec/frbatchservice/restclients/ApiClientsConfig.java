/* 2024-2025 */
package com.fronzec.frbatchservice.restclients;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ApiClientsConfig {

    @Bean
    public RestClient restTemplateApiClient() {
        return RestClient.builder().build();
    }
}
