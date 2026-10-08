package com.corporate.travel.bff.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void downstreamClientError_isPassedThroughWithStatusAndBody() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        String body = "{\"title\":\"Forbidden\",\"detail\":\"Not authorized to view this booking\"}";

        ResponseEntity<Object> response = handler.handleDownstreamError(HttpClientErrorException.create(
            HttpStatus.FORBIDDEN, "Forbidden", headers, body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody()).isEqualTo(body);
    }

    @Test
    void downstreamServerError_becomesBadGateway() {
        ResponseEntity<Object> response = handler.handleDownstreamError(HttpServerErrorException.create(
            HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", new HttpHeaders(),
            "boom".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) response.getBody()).get("message")).isEqualTo("Downstream service returned HTTP 500");
    }
}
