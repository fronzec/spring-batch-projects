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
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ApiClient {

  private final RestTemplate restTemplateDispatch;
  private final String batchServiceDispatchUrl;

  Logger logger = Logger.getLogger(ApiClient.class.getName());

  public ApiClient(RestTemplateBuilder restTemplateBuilder,
          @Value("${single_threaded.rest_clients.client1.base_url}")
          String batchServiceHost,
          @Value("${single_threaded.rest_clients.client1.dispatch_path.post}")
          String batchServiceDispatchPath,
          @Value("${single-threaded.rest-clients.client1.connection-timeout-millis}")
          int connectionTimeoutMillis,
          @Value("${single-threaded.rest-clients.client1.response-timeout-millis}")
          int readTimeoutMillis) {
    this.batchServiceDispatchUrl = UriComponentsBuilder.fromHttpUrl(batchServiceHost + batchServiceDispatchPath).toUriString();
    this.restTemplateDispatch = restTemplateBuilder.setConnectTimeout(Duration.ofMillis(connectionTimeoutMillis))
            .setReadTimeout(Duration.ofMillis(readTimeoutMillis))
            .build();
  }

  public boolean sendBatch(BatchItemsPayload payload) {
    HttpEntity<BatchItemsPayload> request = new HttpEntity<>(payload, createDefaultHeaders());
    ResponseEntity<Object> response = restTemplateDispatch.postForEntity(batchServiceDispatchUrl, request, Object.class);
    // TODO: 12/09/2022 handle exceptions
    if (response.getStatusCode() == HttpStatus.OK) {
      return true;
    }

    logger.warning("batch cannot be sent successfully");
    return false;
  }

  private HttpHeaders createDefaultHeaders() {
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(MediaType.APPLICATION_JSON);
    httpHeaders.add("X-FronzecInc-Caller", "SingleThreadedService");
    return httpHeaders;
  }

}