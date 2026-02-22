package com.corporate.travel.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Security Headers Filter
 * 
 * Adds security-related HTTP headers to all responses to protect against
 * common web vulnerabilities (XSS, clickjacking, MIME sniffing, etc.).
 */
@Slf4j
@Component
public class SecurityHeadersFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            
            // Prevent MIME type sniffing
            headers.add("X-Content-Type-Options", "nosniff");
            
            // Prevent clickjacking attacks
            headers.add("X-Frame-Options", "DENY");
            
            // Enable XSS protection in browsers
            headers.add("X-XSS-Protection", "1; mode=block");
            
            // Enforce HTTPS (in production)
            headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            
            // Referrer policy
            headers.add("Referrer-Policy", "strict-origin-when-cross-origin");
            
            // Permissions policy (formerly Feature-Policy)
            headers.add("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
            
            log.debug("Added security headers to response");
        }));
    }

    @Override
    public int getOrder() {
        return 0; // Execute after logging filter
    }
}
