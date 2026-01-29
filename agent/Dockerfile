# Build stage
FROM gradle:8.12-jdk21-alpine AS builder

WORKDIR /build

# Copy Gradle files first for dependency caching
COPY build.gradle.kts settings.gradle.kts ./

# Download dependencies (cached unless build.gradle.kts changes)
RUN gradle dependencies --no-daemon

# Copy source (separate layer)
COPY src ./src

# Build the application (uses cached dependencies)
RUN gradle bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install git for git operations
RUN apk add --no-cache git

WORKDIR /app

# Copy the built JAR
COPY --from=builder /build/build/libs/*.jar /app/agent.jar

# Create directories
RUN mkdir -p /app/automations

EXPOSE 8080

# JVM options for container environment
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

# Health check with longer start period for JVM warmup
HEALTHCHECK --interval=30s --timeout=10s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/agent.jar"]
