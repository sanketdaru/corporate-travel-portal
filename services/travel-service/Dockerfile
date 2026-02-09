FROM eclipse-temurin:17-jre-alpine

# Add metadata
LABEL maintainer="corporate-travel"
LABEL service="travel-service"
LABEL description="Travel booking service with multi-tenant support"

# Create app directory
WORKDIR /app

# Copy the JAR file
COPY services/travel-service/build/libs/travel-service.jar app.jar

# Expose port
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

# Optional JVM arguments for production
# ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]
