# Build stage - using Maven builder
FROM maven:3.9-eclipse-temurin-17 AS build

# Set working directory
WORKDIR /build

# Copy pom.xml and download dependencies first (for better caching)
COPY pom.xml .
RUN mvn -B org.apache.maven.plugins:maven-dependency-plugin:3.6.0:go-offline -Dmaven.repo.local=/root/.m2

# Copy source code
COPY src/main/java src/main/java
COPY src/main/resources src/main/resources

# Build the application using Quarkus Maven plugin (with extensions=true)
RUN mvn -B clean package -DskipTests

# Runtime stage - minimal footprint with Java
FROM registry.access.redhat.com/ubi9-minimal:latest

# Install necessary components including Java
RUN microdnf update -y && \
    microdnf install -y shadow-utils java-17-openjdk-headless curl-minimal && \
    microdnf clean all

# Create non-root user
RUN useradd -u 1000 -m -s /bin/bash quarkus

# Set working directory
WORKDIR /home/quarkus

# Create deploy directories with proper permissions (will be mounted as volumes)
RUN mkdir -p /home/quarkus/deploy/input \
            /home/quarkus/deploy/output \
            /home/quarkus/deploy/archive && \
    chown -R quarkus:quarkus /home/quarkus

# Define mount point for persistent storage
VOLUME ["/home/quarkus/deploy"]

# Copy the Quarkus app directory (JAR + dependencies)
COPY --from=build /build/target/quarkus-app/ /home/quarkus/app/

# Switch to non-root user
USER quarkus

# Expose the HTTP port
EXPOSE 8080

# Set environment variables
ENV PORT=8080 \
    CAMEL_INPUT_DIR=/home/quarkus/deploy/input \
    CAMEL_OUTPUT_DIR=/home/quarkus/deploy/output \
    CAMEL_ARCHIVE_DIR=/home/quarkus/deploy/archive

# Health check (using curl-minimal)
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl-minimal -f http://localhost:8080/health/live || exit 1

# Run the application
CMD ["java", "-jar", "/home/quarkus/app/quarkus-run.jar"]
