package com.fronzec.frbatchservice.restclients

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestTemplate
import spock.lang.Specification
import spock.lang.Subject

class ApiClientTest extends Specification {
    RestTemplate restTemplate = Mock()
    String baseUrl = "http://example.com"
    String dispatchPath = "/dispatch"
    String calculatePath = "/calculate"

    @Subject
    ApiClient apiClient = new ApiClient(restTemplate, baseUrl, dispatchPath, calculatePath)

    def "should send batch successfully"() {
        given: "a successful API call"
        BatchItemsPayload payload = new BatchItemsPayload()
        restTemplate.postForEntity(_, _, _) >> new ResponseEntity<Object>(HttpStatus.OK)

        when: "invoke sendBatch method"
        boolean result = apiClient.sendBatch(payload)

        then: "it should return true"
        result
    }

    def "should fail to send Batch"() {
        given: "an unsuccessful API call"
        BatchItemsPayload payload = new BatchItemsPayload()
        restTemplate.postForEntity(_, _, _) >> new ResponseEntity<Object>(HttpStatus.BAD_REQUEST)

        when: "invoke sendBatch method"
        boolean result = apiClient.sendBatch(payload)

        then: "it should return false"
        !result
    }

    def "should return valid DataCalculatedResponse"() {
        given: "a successful API call"
        restTemplate.getForEntity(_, DataCalculatedResponse.class) >> new ResponseEntity<>(new DataCalculatedResponse(), HttpStatus.OK)

        when: "invoke getRandomValue method"
        DataCalculatedResponse response = apiClient.getRandomValue(1L)

        then: "it should return DataCalculatedResponse"
        response != null
    }

    def "should throw exception when API call fails"() {
        given: "an unsuccessful API call"
        restTemplate.getForEntity(_, DataCalculatedResponse.class) >> new ResponseEntity<>(null, HttpStatus.BAD_REQUEST)

        when: "invoke getRandomValue method"
        Throwable thrown = null
        try {
            apiClient.getRandomValue(1L)
        } catch (Throwable t) {
            thrown = t
        }

        then: "it should throw RuntimeException"
        thrown instanceof RuntimeException
    }
}
