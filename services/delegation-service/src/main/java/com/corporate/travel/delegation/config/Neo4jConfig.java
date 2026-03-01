package com.corporate.travel.delegation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Neo4j Configuration
 * 
 * Configures Neo4j repositories for graph-based delegation queries
 */
@Configuration
@EnableNeo4jRepositories(basePackages = "com.corporate.travel.delegation.repository.graph")
@EnableTransactionManagement
public class Neo4jConfig {
    // Spring Boot auto-configuration handles driver setup from application.yml
    // Custom configuration can be added here if needed
}