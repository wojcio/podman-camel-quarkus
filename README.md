---
{}
---
# Camel Quarkus Podman Container

A Camel-Quarkus application running in a Podman container with persistent storage for file processing and REST API.

## Overview

This project demonstrates:

- Apache Camel Quarkus for enterprise integration patterns
- Podman containerization with JAR deployment (Java 17)
- Persistent storage via volume mounting to the `deploy` directory
- File-based message processing with automatic archiving
- **NEW: REST Bookshop API** using Netty HTTP with SQLite database

## Project Structure

```
podman-camel-quarkus/
├── src/main/java/org/example/camel/
│   ├── CamelRoute.java          # Main Camel routes (file processor)
│   ├── BookshopRoute.java       # REST Bookshop API (Netty + SQLite) ⭐ NEW
│   └── HealthResource.java      # REST health check endpoint
├── src/main/resources/
│   ├── application.properties   # Application configuration
│   └── camel/                   # Blueprint route definitions
├── blueprints/                  # External route definitions
│   ├── camel-route.xml          # XML-based Camel routes
│   ├── camel-route.yaml         # YAML-based Camel routes
│   ├── bookshop-route.xml       # Bookshop API reference (XML)
│   └── README.md                # Blueprint usage documentation
├── deploy/                       # Persistent storage directory
│   ├── input/                   # Files to process (mounted volume)
│   ├── output/                  # Processed files (mounted volume)
│   ├── archive/                 # Archived files (mounted volume)
│   └── bookshop.db              # SQLite database (auto-created) ⭐ NEW
├── Dockerfile                    # Container build instructions
├── .podman-compose.yml          # Podman compose configuration
├── run.sh                        # Deployment automation script
└── pom.xml                       # Maven build configuration
```

## Directory Structure

The `deploy` folder contains directories for file processing and the SQLite database:

- **input/** - Place files here for processing
- **output/** - Processed files appear here (converted to uppercase)
- **archive/** - Original files are archived here after processing
- **bookshop.db** - SQLite database for the Bookshop API (auto-created on first run)

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
  -p 8081:8081 \
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

### File Processor (Port 8080)

#### 1. Place a file in the input directory:

```bash
echo "Hello, Camel Quarkus!" > deploy/input/test.txt
```

#### 2. View the logs:

```bash
podman logs -f podman-camel-quarkus
```

#### 3. Check the output:

Processed files appear in `deploy/output/` with content converted to uppercase.

#### 4. Check archived files:

Original files are moved to `deploy/archive/`.

### Bookshop REST API (Port 8081) ⭐ NEW

The application provides a complete RESTful Bookshop API:

#### List all books:
```bash
curl http://localhost:8081/api/books
```

#### Get a specific book:
```bash
curl http://localhost:8081/api/books/1
```

#### Create a new book:
```bash
curl -X POST http://localhost:8081/api/books \
  -H "Content-Type: application/json" \
  -d '{
    "title": "New Book",
    "author": "Author Name",
    "isbn": "123-4567890123",
    "price": 29.99,
    "stock": 10,
    "genre": "Fiction"
  }'
```

#### Update a book:
```bash
curl -X PUT http://localhost:8081/api/books/1 \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Updated Title",
    "author": "Author Name",
    "isbn": "978-1788399672",
    "price": 39.99,
    "stock": 20,
    "genre": "Programming"
  }'
```

#### Delete a book:
```bash
curl -X DELETE http://localhost:8081/api/books/1
```

#### API Root (Info):
```bash
curl http://localhost:8081/
```

## Health Check

```bash
# Health endpoints (port 8080)
curl http://localhost:8080/health/live
curl http://localhost:8080/health/ready

# Bookshop API health (port 8081)
curl http://localhost:8081/health/live
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
# This will start both the REST endpoints (8080) and Bookshop API (8081)
mvn quarkus:dev
```

## Configuration

The application can be configured via environment variables:

| Variable            | Default                      | Description             |
|---------------------|------------------------------|-------------------------|
| `PORT`              | 8080                         | HTTP port for health endpoints |
| `CAMEL_INPUT_DIR`   | /home/quarkus/deploy/input   | Input directory path    |
| `CAMEL_OUTPUT_DIR`  | /home/quarkus/deploy/output  | Output directory path   |
| `CAMEL_ARCHIVE_DIR` | /home/quarkus/deploy/archive | Archive directory path  |

## Application Properties

| Property                     | Value                | Description         |
|------------------------------|----------------------|---------------------|
| `quarkus.application.name`   | podman-camel-quarkus | Application name    |
| `camel.context.name`         | podman-camel-context | Camel context name  |
| `quarkus.log.level`          | INFO                 | Log level           |
| `quarkus.micrometer.enabled` | true                 | Metrics enabled     |
| `quarkus.netty.ports`        | 8081                 | Bookshop API port   |
| `quarkus.datasource.db-kind` | sqlite               | Database type       |

## Camel Routes

The application uses the following routes:

### 1. File Processor Route (`CamelRoute.java`)
- **ID**: `file-processor-route`
- **Function**: Monitors input directory for `.txt` files, processes them (converts to uppercase), and archives originals
- **ID**: `health-check`
- **Function**: Provides health status via direct endpoint

### 2. Bookshop REST API Route (`BookshopRoute.java`) ⭐ NEW
- **ID**: `bookshop-api-route`
- **Function**: Complete CRUD REST API for book management using Netty HTTP and SQLite
- **Endpoints**: GET, POST, PUT, DELETE on `/api/books/{id}`

## Blueprints

The `blueprints/` directory contains example Camel route definitions in different formats:

| File | Format | Description |
|------|--------|-------------|
| `camel-route.xml` | XML | XML-based Camel routes using Spring XML namespace |
| `camel-route.yaml` | YAML | YAML-based Camel routes for simple routing scenarios |
| `bookshop-route.xml` | XML | Reference implementation of Bookshop API in XML |

For detailed blueprint usage, see [blueprints/README.md](blueprints/README.md).

## Database Schema

The Bookshop API uses SQLite with the following schema:

```sql
CREATE TABLE books (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title VARCHAR(255) NOT NULL,
    author VARCHAR(255) NOT NULL,
    isbn VARCHAR(20) UNIQUE,
    price DECIMAL(10,2) NOT NULL,
    stock INTEGER DEFAULT 0,
    genre VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

### Sample Data

On first run, the database is seeded with 5 sample books:
1. Modern Java in Action - Raoul-Gabriel Urma ($45.99)
2. Apache Camel in Action - Christian Posta ($39.99)
3. Quarkus in Action - Ken Finnigan ($54.99)
4. Kubernetes Up and Running - Kelsey Hightower ($49.99)
5. Building Microservices - Sam Newman ($52.99)

## Deployment Information

- **Container Name**: podman-camel-quarkus
- **Image Registry**: quay.io/wojcio/camel-quarkus:latest
- **Ports**: 8080 (health/REST), 8081 (Bookshop API)
- **Network**: camel-network

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                    Podman Container                          │
│  ┌───────────────────────────────────────────────────────┐  │
│  │              Quarkus Application                       │  │
│  │  ┌─────────────────────┐    ┌─────────────────────┐   │  │
│  │  │ HealthResource.java │    │  CamelRoute.java    │   │  │
│  │  │  /health/live       │    │  File Processor     │   │  │
│  │  │  /health/ready      │    │  Port: (internal)   │   │  │
│  │  └──────────┬──────────┘    └──────────┬──────────┘   │  │
│  │             │                           │              │  │
│  │  ┌──────────▼───────────────────────────▼──────────┐   │  │
│  │  │         BookshopRoute.java (NEW)                │   │  │
│  │  │         Netty HTTP Server on Port 8081          │   │  │
│  │  │         /api/books (GET, POST, PUT, DELETE)     │   │  │
│  │  └─────────────────────┬───────────────────────────┘   │  │
│  └────────────────────────┼───────────────────────────────┘  │
│                           │                                   │
│              ┌────────────▼────────────┐                     │
│              │   SQLite (bookshop.db)  │                     │
│              └─────────────────────────┘                     │
└───────────────┬──────────────────────────────────────────────┘
                │ Volume Mount
                ▼
┌─────────────────────────────────────────────────────────────┐
│           Host Filesystem (deploy/)                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │ input/   │  │ output/  │  │ archive/ │                  │
│  └──────────┘  └──────────┘  └──────────┘                  │
│                                                             │
│  ┌─────────────────┐                                       │
│  │ bookshop.db     │ SQLite Database                       │
│  └─────────────────┘                                       │
└─────────────────────────────────────────────────────────────┘
```

## License

MIT