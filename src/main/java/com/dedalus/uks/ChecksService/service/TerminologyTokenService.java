package com.dedalus.uks.ChecksService.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;


import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
public class TerminologyTokenService {
    
    
    private final RestTemplate restTemplate;
    private final AtomicReference<String> token = new AtomicReference<>();
    private long expiryTime = 0;

    @Autowired
    public TerminologyTokenService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;

    }

    public String getToken(String tokenEndpoint, String clientId, String clientSecret, boolean force) throws JsonMappingException, JsonProcessingException {
        log.info("Getting token from endpoint {}", tokenEndpoint);
        // Check if token is expired or null
        if (token.get() == null || System.currentTimeMillis() >= expiryTime || force) {
            log.info("Calling refresh token");
            refreshToken(tokenEndpoint, clientId, clientSecret);
        } else {
            log.info("Token is still valid " + token.get());
        }
        return token.get();
    }

    private void refreshToken(String tokenEndpoint, String clientId, String clientSecret) throws JsonMappingException, JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        
        // Prepare the body for the token request
        String body = String.format("grant_type=client_credentials&client_id=%s&client_secret=%s", clientId, clientSecret);
        HttpEntity<String> requestEntity = new HttpEntity<>(body, headers);
        
        log.info("Calling tokenEndpoint {}", tokenEndpoint);
        ResponseEntity<String> response = restTemplate.exchange(tokenEndpoint, HttpMethod.POST, requestEntity, String.class);
        log.info("Response from tokenEndpoint {}", response.getStatusCode());   

        // Parse the JSON response to extract the access_token
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(response.getBody());

        if (response.getStatusCode() == HttpStatus.OK) {
            
            String accessToken = jsonNode.path("access_token").asText();
            long expires_in = jsonNode.path("expires_in").asLong();
            token.set(accessToken);
            expiryTime = System.currentTimeMillis() + (expires_in * 1000) - (60 * 1000); // Refresh 1 minute early
        } else {
            throw new RuntimeException("Failed to refresh token: " + response.getStatusCode());
        }
    }

}

