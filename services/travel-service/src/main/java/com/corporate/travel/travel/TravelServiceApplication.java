package com.corporate.travel.travel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Travel Service - Manages travel bookings (flights, hotels, car rentals)
 * 
 * This service demonstrates:
 * - Multi-tenant data isolation
 * - OPA-based authorization
 * - Delegation-aware operations
 * - SecurityContext integration
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.corporate.travel.travel",
    "com.corporate.travel.security"  // Scan shared security package
})
public class TravelServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(TravelServiceApplication.class, args);
    }
}
