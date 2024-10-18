package com.fronzec.frbatchservice.restclients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class ApiClientsConfig {

  @Bean
  public RestTemplate restTemplateApiClient(
          RestTemplateBuilder restTemplateBuilder,
          @Value("${fr-batch-service.rest_clients.client1.connection_timeout_millis}")
          int connectionTimeoutMillis,
          @Value("${fr-batch-service.rest_clients.client1.response_timeout_millis}")
          int readTimeoutMillis
  ) {
    return restTemplateBuilder
                    .setConnectTimeout(Duration.ofMillis(connectionTimeoutMillis))
                    .setReadTimeout(Duration.ofMillis(readTimeoutMillis))
                    .build();

  }


}
