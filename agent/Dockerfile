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

# Model download stage
FROM alpine:3.19 AS model-downloader

# Install curl and jq for downloading from HuggingFace
RUN apk add --no-cache curl jq

WORKDIR /models

# Download CodeRankEmbed ONNX model from HuggingFace
# Using the ONNX-converted version by sirasagi62
# Note: DJL expects the ONNX file to be named after the directory, so we rename model.onnx to coderank-embed.onnx
RUN mkdir -p coderank-embed && \
    echo "Downloading CodeRankEmbed ONNX model..." && \
    curl -L -o coderank-embed/coderank-embed.onnx \
        "https://huggingface.co/sirasagi62/code-rank-embed-onnx/resolve/main/model.onnx" && \
    curl -L -o coderank-embed/tokenizer.json \
        "https://huggingface.co/sirasagi62/code-rank-embed-onnx/resolve/main/tokenizer.json" && \
    curl -L -o coderank-embed/config.json \
        "https://huggingface.co/sirasagi62/code-rank-embed-onnx/resolve/main/config.json" && \
    echo "Model download complete"

# Runtime stage - using Debian-based image for glibc compatibility with ONNX Runtime
FROM eclipse-temurin:21-jre

# Install git for git operations and curl for healthcheck
RUN apt-get update && apt-get install -y --no-install-recommends git curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy the built JAR
COPY --from=builder /build/build/libs/*.jar /app/agent.jar

# Copy the ONNX model
COPY --from=model-downloader /models/coderank-embed /app/models/coderank-embed

# Create directories for automations and data
RUN mkdir -p /app/automations /app/data

EXPOSE 8080

# JVM options for container environment
# Increased memory for ONNX model loading
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom -Dai.djl.default_engine=OnnxRuntime"

# Health check with longer start period for JVM warmup and model loading
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/agent.jar"]
