/* 2024-2025 */
package com.fronzec.frbatchservice.restclients;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class ApiClientJavaVersionTest {

    @Mock private RestTemplate restTemplate;

    private ApiClient apiClient;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        String batchServiceHost = "http://batchservice.com";
        String batchServiceDispatchPath = "/dispatch";
        String getRandomValuePath = "/calculate";
        this.apiClient =
                new ApiClient(
                        restTemplate,
                        batchServiceHost,
                        batchServiceDispatchPath,
                        getRandomValuePath);
    }

    @Test
    public void testSendBatch_Success() {
        BatchItemsPayload payload =
                new BatchItemsPayload(); // Crea el objeto BatchItemsPayload según corresponda

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-FronzecInc-Caller", "FrBatchService");

        HttpEntity<BatchItemsPayload> request = new HttpEntity<>(payload, headers);
        ResponseEntity<Object> response = new ResponseEntity<>(HttpStatus.OK);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(response);

        boolean result = apiClient.sendBatch(payload);

        assertTrue(result);
        verify(restTemplate, times(1))
                .postForEntity(anyString(), any(HttpEntity.class), eq(Object.class));
    }

    @Test
    public void testSendBatch_Failure() {
        BatchItemsPayload payload =
                new BatchItemsPayload(); // Crea el objeto BatchItemsPayload según corresponda

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-FronzecInc-Caller", "FrBatchService");

        HttpEntity<BatchItemsPayload> request = new HttpEntity<>(payload, headers);
        ResponseEntity<Object> response = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(response);

        boolean result = apiClient.sendBatch(payload);

        assertFalse(result);
        verify(restTemplate, times(1))
                .postForEntity(anyString(), any(HttpEntity.class), eq(Object.class));
    }

    @Test
    public void testGetRandomValue_Success() {
        Long id = 123L;
        DataCalculatedResponse dataCalculatedResponse =
                new DataCalculatedResponse(); // Crea el objeto DataCalculatedResponse según
        // corresponda
        ResponseEntity<DataCalculatedResponse> response =
                new ResponseEntity<>(dataCalculatedResponse, HttpStatus.OK);

        when(restTemplate.getForEntity(anyString(), eq(DataCalculatedResponse.class)))
                .thenReturn(response);

        DataCalculatedResponse result = apiClient.getRandomValue(id);

        assertNotNull(result);
        verify(restTemplate, times(1)).getForEntity(anyString(), eq(DataCalculatedResponse.class));
    }

    @Test
    public void testGetRandomValue_Failure() {
        Long id = 123L;
        ResponseEntity<DataCalculatedResponse> response =
                new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.getForEntity(anyString(), eq(DataCalculatedResponse.class)))
                .thenReturn(response);

        RuntimeException exception =
                assertThrows(
                        RuntimeException.class,
                        () -> {
                            apiClient.getRandomValue(id);
                        });

        assertEquals("cannot fetch the random number for -> " + id, exception.getMessage());
        verify(restTemplate, times(1)).getForEntity(anyString(), eq(DataCalculatedResponse.class));
    }
}
