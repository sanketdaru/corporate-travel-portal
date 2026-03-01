package com.corporate.travel.delegation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Delegation Service - Manages delegation relationships using PostgreSQL and Neo4j
 * 
 * This service provides:
 * - Delegation CRUD operations (PostgreSQL as source of truth)
 * - Graph traversal for delegation chains (Neo4j for optimized queries)
 * - Multi-tenant isolation
 * - OPA-based authorization
 */
@SpringBootApplication(scanBasePackages = {
    "com.corporate.travel.delegation",
    "com.corporate.travel.security"
})
@EnableJpaRepositories(basePackages = "com.corporate.travel.delegation.repository.jpa")
@EnableAsync
public class DelegationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DelegationServiceApplication.class, args);
    }
}