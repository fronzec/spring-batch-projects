/* 2024-2025 */
package com.fronzec.frbatchservice.restclients;

import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Value;
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
    private final String getRandomNumberUrl;
    Logger logger = Logger.getLogger(ApiClient.class.getName());

    /**
     * Constructs an ApiClient and builds the full dispatch and random-value endpoint URLs
     * from the provided base URL and paths.
     *
     * @param restTemplate               the RestTemplate used for HTTP requests
     * @param batchServiceHost           the base URL of the batch service (e.g. "https://api.example.com")
     * @param batchServiceDispatchPath   the path appended to the base URL for the dispatch endpoint (e.g. "/dispatch")
     * @param getRandomValuePath         the path appended to the base URL for the random-value endpoint (e.g. "/calculate/random")
     */
    public ApiClient(
            RestTemplate restTemplate,
            @Value("${fr-batch-service.rest_clients.client1.base_url}") String batchServiceHost,
            @Value("${fr-batch-service.rest_clients.client1.dispatch_path.post}")
                    String batchServiceDispatchPath,
            @Value("${fr-batch-service.rest_clients.client1.calculate.get}")
                    String getRandomValuePath) {
        this.batchServiceDispatchUrl =
                UriComponentsBuilder.fromUriString(batchServiceHost + batchServiceDispatchPath)
                        .toUriString();
        this.getRandomNumberUrl =
                UriComponentsBuilder.fromUriString(batchServiceHost + getRandomValuePath)
                        .toUriString();
        this.restTemplateDispatch = restTemplate;
    }

    public boolean sendBatch(BatchItemsPayload payload) {
        HttpEntity<BatchItemsPayload> request = new HttpEntity<>(payload, createDefaultHeaders());
        ResponseEntity<Object> response =
                restTemplateDispatch.postForEntity(batchServiceDispatchUrl, request, Object.class);
        // TODO: 12/09/2022 handle exceptions
        if (response.getStatusCode() == HttpStatus.OK) {
            return true;
        }

        logger.warning("batch cannot be sent successfully");
        return false;
    }

    public DataCalculatedResponse getRandomValue(Long id) {
        ResponseEntity<DataCalculatedResponse> response =
                restTemplateDispatch.getForEntity(getRandomNumberUrl, DataCalculatedResponse.class);
        if (response.getStatusCode() == HttpStatus.OK) {
            return response.getBody();
        }
        logger.warning("cannot fetch the random number for -> " + id);
        throw new RuntimeException("cannot fetch the random number for -> " + id);
    }

    private HttpHeaders createDefaultHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        httpHeaders.add("X-FronzecInc-Caller", "FrBatchService");
        return httpHeaders;
    }
}