# Multi-stage build for API Gateway
# Best practices: multi-platform support, layer caching, minimal image size, security

# Stage 1: Build dependencies (cached layer)
FROM gradle:9.3-jdk17 AS dependencies

WORKDIR /app

# Copy Docker-specific settings file
COPY services/api-gateway/settings-docker.gradle settings.gradle
COPY build.gradle gradle.properties ./

# Copy shared libraries build files for dependency resolution
COPY services/shared/security-commons/build.gradle services/shared/security-commons/build.gradle
COPY services/shared/domain-models/build.gradle services/shared/domain-models/build.gradle

# Copy service build file
COPY services/api-gateway/build.gradle services/api-gateway/build.gradle

# Download dependencies (this layer will be cached)
RUN gradle dependencies --no-daemon || return 0

# Stage 2: Build application
FROM gradle:9.3-jdk17 AS builder

WORKDIR /app

# Copy dependency layer from previous stage
COPY --from=dependencies /home/gradle/.gradle /home/gradle/.gradle

# Copy Docker-specific settings file
COPY services/api-gateway/settings-docker.gradle settings.gradle
COPY build.gradle gradle.properties ./

# Copy shared libraries source (needed for compilation)
COPY services/shared ./services/shared

# Copy ONLY api-gateway source
COPY services/api-gateway ./services/api-gateway

# Build using pre-installed gradle (NO DOWNLOAD!)
RUN gradle :services:api-gateway:build -x test --no-daemon

# Stage 3: Runtime (minimal multi-platform image)
FROM eclipse-temurin:17-jre-jammy

# Add metadata
LABEL maintainer="corporate-travel-platform"
LABEL service="api-gateway"
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
    /app/services/api-gateway/build/libs/*.jar app.jar

# Switch to non-root user
USER spring:spring

# Expose the service port
EXPOSE 8000

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
    CMD wget --no-verbose --tries=1 --spider http://localhost:8000/actuator/health || exit 1

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]