# Camel Quarkus Podman Container

A Camel-Quarkus application running in a Podman container with persistent storage for file processing.

## Overview

This project demonstrates:
- Apache Camel Quarkus for enterprise integration patterns
- Podman containerization with JAR deployment (Java 17)
- Persistent storage via volume mounting to the `deploy` directory
- File-based message processing with automatic archiving

## Project Structure

```
podman-camel-quarkus/
├── src/main/java/org/example/camel/
│   ├── CamelRoute.java          # Main Camel routes
│   └── HealthResource.java      # REST health check endpoint
├── src/main/resources/
│   └── application.properties   # Application configuration
├── deploy/                       # Persistent storage directory
│   ├── input/                   # Files to process (mounted volume)
│   ├── output/                  # Processed files (mounted volume)
│   └── archive/                 # Archived files (mounted volume)
├── Dockerfile                    # Container build instructions
├── .podman-compose.yml          # Podman compose configuration
├── run.sh                        # Deployment automation script
└── pom.xml                       # Maven build configuration
```

## Directory Structure

The `deploy` folder contains three subdirectories:
- **input/** - Place files here for processing
- **output/** - Processed files appear here (converted to uppercase)
- **archive/** - Original files are archived here after processing

## Prerequisites

- Podman or Docker installed
- Maven 3.8+ (for local development)
- Java 17+ (for local development)

## Quick Start

### Option 1: Using the Deployment Script (Recommended)

```bash
chmod +x run.sh
./run.sh
```

### Option 2: Manual Podman Deployment

#### Build the Container

```bash
podman build -t quay.io/wojcio/camel-quarkus:latest .
```

#### Run the Container

```bash
podman run -d \
  --name podman-camel-quarkus \
  -p 8080:8080 \
  -v $(pwd)/deploy:/home/quarkus/deploy:Z \
  --network camel-network \
  -e PORT=8080 \
  -e CAMEL_INPUT_DIR=/home/quarkus/deploy/input \
  -e CAMEL_OUTPUT_DIR=/home/quarkus/deploy/output \
  -e CAMEL_ARCHIVE_DIR=/home/quarkus/deploy/archive \
  quay.io/wojcio/camel-quarkus:latest
```

### Option 3: Using Podman Compose

```bash
podman-compose up -d
```

## Using the Application

### 1. Place a file in the input directory:

```bash
echo "Hello, Camel Quarkus!" > deploy/input/test.txt
```

### 2. View the logs:

```bash
podman logs -f podman-camel-quarkus
```

### 3. Check the output:

Processed files appear in `deploy/output/` with content converted to uppercase.

### 4. Check archived files:

Original files are moved to `deploy/archive/`.

## Health Check

```bash
curl http://localhost:8080/health/live
curl http://localhost:8080/health/ready
```

## Metrics

```bash
curl http://localhost:8080/q/metrics
```

## Stopping the Container

```bash
podman stop podman-camel-quarkus
podman rm podman-camel-quarkus
```

Or with compose:

```bash
podman-compose down
```

## Development

### Build locally (without container):

```bash
mvn clean package -DskipTests
```

### Run locally:

```bash
mvn quarkus:dev
```

## Configuration

The application can be configured via environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | 8080 | HTTP port |
| `CAMEL_INPUT_DIR` | /home/quarkus/deploy/input | Input directory path |
| `CAMEL_OUTPUT_DIR` | /home/quarkus/deploy/output | Output directory path |
| `CAMEL_ARCHIVE_DIR` | /home/quarkus/deploy/archive | Archive directory path |

## Camel Routes

The application uses the following routes:

1. **file-processor-route** - Monitors input directory for `.txt` files, processes them (converts to uppercase), and archives originals
2. **health-check** - Provides health status via direct endpoint

## Application Properties

| Property | Value | Description |
|----------|-------|-------------|
| `quarkus.application.name` | podman-camel-quarkus | Application name |
| `camel.context.name` | podman-camel-context | Camel context name |
| `quarkus.log.level` | INFO | Log level |
| `quarkus.micrometer.enabled` | true | Metrics enabled |

## Deployment Information

- **Container Name**: podman-camel-quarkus
- **Image Registry**: quay.io/wojcio/camel-quarkus:latest
- **Port**: 8080
- **Network**: camel-network

## License

MIT