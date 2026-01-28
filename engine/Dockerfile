FROM golang:1.23-alpine AS builder

WORKDIR /build

# Install git for go mod download
RUN apk add --no-cache git

# Copy source
COPY . .

# Download dependencies and generate go.sum
RUN go mod tidy

# Build
RUN CGO_ENABLED=0 GOOS=linux go build -o /engine .

# Runtime image
FROM alpine:3.20

RUN apk add --no-cache ca-certificates tzdata

WORKDIR /app

COPY --from=builder /engine /app/engine

# Create directories
RUN mkdir -p /app/automations /app/state

EXPOSE 9000

CMD ["/app/engine"]
