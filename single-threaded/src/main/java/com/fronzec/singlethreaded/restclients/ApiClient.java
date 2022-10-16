package com.fronzec.singlethreaded.restclients;

import java.time.Duration;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ApiClient {

  private final RestTemplate restTemplate;

  Logger logger = Logger.getLogger(ApiClient.class.getName());

  public ApiClient(RestTemplateBuilder restTemplateBuilder,
          @Value("${single-threaded.rest-clients.client1.connection-timeout-millis}")
          int connectionTimeoutMillis,
          @Value("${single-threaded.rest-clients.client1.response-timeout-millis}")
          int readTimeoutMillies) {
    this.restTemplate = restTemplateBuilder.setConnectTimeout(Duration.ofMillis(connectionTimeoutMillis))
            .setReadTimeout(Duration.ofMillis(readTimeoutMillies))
            .build();
  }

  public boolean sendBatch(BatchItemsPayload payload) {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
    // TODO: 12/09/2022 load the URL
    String url = "http://localhost:8081/mock-service/receive-batch";
    HttpEntity<BatchItemsPayload> request = new HttpEntity<>(payload, httpHeaders);
    ResponseEntity<Object> response = restTemplate.postForEntity(url, request, Object.class);
    // TODO: 12/09/2022 handle exceptions
    if (response.getStatusCode() == HttpStatus.OK) {
      return true;
    }

    logger.warning("batch cannot be sent successfully");
    return false;
  }

}