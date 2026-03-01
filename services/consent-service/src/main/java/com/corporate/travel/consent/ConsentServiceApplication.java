package com.corporate.travel.consent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Consent Service - Manages consent records with purpose binding
 * 
 * This service provides:
 * - Consent lifecycle management (grant, revoke, expire)
 * - Purpose binding and scope validation
 * - Multi-tenant isolation
 * - OPA-based authorization
 * - Complete audit trail
 */
@SpringBootApplication(scanBasePackages = {
    "com.corporate.travel.consent",
    "com.corporate.travel.security"
})
@EnableJpaRepositories(basePackages = "com.corporate.travel.consent.repository")
public class ConsentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConsentServiceApplication.class, args);
    }
}