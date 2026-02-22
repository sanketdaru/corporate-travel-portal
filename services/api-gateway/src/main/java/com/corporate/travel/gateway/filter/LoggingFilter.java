package com.corporate.travel.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Global Logging Filter
 * 
 * Logs all incoming requests and outgoing responses for monitoring and debugging.
 * Executes with high priority to capture all traffic.
 */
@Slf4j
@Component
public class LoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Log incoming request
        log.info("Incoming request: {} {} from {}", 
            request.getMethod(),
            request.getURI().getPath(),
            request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress() : "unknown");
        
        // Log headers (excluding sensitive ones)
        request.getHeaders().forEach((key, value) -> {
            if (!key.equalsIgnoreCase("Authorization")) {
                log.debug("Header: {} = {}", key, value);
            } else {
                log.debug("Header: Authorization = [REDACTED]");
            }
        });
        
        // Continue filter chain and log response
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            log.info("Response status: {} for {} {}", 
                response.getStatusCode(),
                request.getMethod(),
                request.getURI().getPath());
        }));
    }

    @Override
    public int getOrder() {
        return -1; // High priority - execute first
    }
}