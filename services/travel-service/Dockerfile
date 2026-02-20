# Multi-stage build for Travel Service
# Best practices: multi-platform support, layer caching, minimal image size, security

# Stage 1: Build dependencies (cached layer)
FROM gradle:8.6-jdk17 AS dependencies

WORKDIR /app

# Copy only build files first for dependency caching
COPY build.gradle settings.gradle gradle.properties ./
COPY gradle gradle/

# Copy shared libraries for dependency resolution
COPY services/shared/security-commons/build.gradle services/shared/security-commons/build.gradle
COPY services/shared/domain-models/build.gradle services/shared/domain-models/build.gradle

# Download dependencies (this layer will be cached)
RUN gradle dependencies --no-daemon || return 0

# Stage 2: Build application
FROM gradle:8.6-jdk17 AS builder

WORKDIR /app

# Copy dependency layer from previous stage
COPY --from=dependencies /root/.gradle /root/.gradle

# Copy build configuration
COPY build.gradle settings.gradle gradle.properties gradlew ./
COPY gradle gradle/

# Copy shared libraries source
COPY services/shared services/shared

# Copy travel service source
COPY services/travel-service services/travel-service

# Build the application (skip tests for faster builds)
RUN ./gradlew :services:travel-service:build -x test --no-daemon

# Stage 3: Runtime (minimal multi-platform image)
FROM eclipse-temurin:17-jre-jammy

# Add metadata
LABEL maintainer="corporate-travel-platform"
LABEL service="travel-service"
LABEL version="1.0.0"
LABEL org.opencontainers.image.source="https://github.com/corporate-travel-portal"

# Install wget for health checks (if not present)
RUN apt-get update && \
    apt-get install -y --no-install-recommends wget && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user for security
RUN groupadd -r spring && useradd -r -g spring spring

WORKDIR /app

# Copy the built jar from builder stage
COPY --from=builder --chown=spring:spring \
    /app/services/travel-service/build/libs/*.jar app.jar

# Switch to non-root user
USER spring:spring

# Expose the service port
EXPOSE 8081

# JVM optimization flags for container environments
ENV JAVA_OPTS="-XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:InitialRAMPercentage=50.0 \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.backgroundpreinitializer.ignore=true"

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]