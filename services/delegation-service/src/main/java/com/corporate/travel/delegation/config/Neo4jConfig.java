package com.corporate.travel.delegation.config;

import jakarta.persistence.EntityManagerFactory;
import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Neo4j Configuration
 *
 * Configures Neo4j repositories for graph-based delegation queries.
 *
 * <p>This service uses both JPA (PostgreSQL, system of record) and Neo4j (delegation graph).
 * Boot only auto-configures a Neo4j transaction manager when no other transaction manager
 * exists, so with JPA present there is none — and Spring Data Neo4j 8's {@code Neo4jTemplate}
 * requires one (every graph query failed with a NullPointerException). Both managers are
 * therefore declared here: JPA stays the primary one for {@code @Transactional}; the Neo4j
 * repositories use the Neo4j manager.</p>
 */
@Configuration
@EnableNeo4jRepositories(
    basePackages = "com.corporate.travel.delegation.repository.graph",
    transactionManagerRef = "neo4jTransactionManager")
@EnableTransactionManagement
public class Neo4jConfig {

    @Bean
    @Primary
    public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }

    @Bean
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver, DatabaseSelectionProvider databaseSelectionProvider) {
        return Neo4jTransactionManager.with(driver).withDatabaseSelectionProvider(databaseSelectionProvider).build();
    }
}
