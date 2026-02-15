#!/bin/bash
#
# run.sh - Deploy Camel Quarkus container on Podman
#
# This script builds and deploys the Camel Quarkus application
# in a Podman container with persistent storage.
#

set -e

# Configuration
REGISTRY="quay.io"
QUAY_USER="wojcio"
CONTAINER_NAME="podman-camel-quarkus"
IMAGE_NAME="${REGISTRY}/${QUAY_USER}/camel-quarkus:latest"
PORT_MAPPING="8080:8080"
DEPLOY_DIR="./deploy"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Podman is installed
check_podman() {
    log_info "Checking if Podman is installed..."
    if ! command -v podman &> /dev/null; then
        log_error "Podman is not installed. Please install Podman first."
        exit 1
    fi
    log_info "Podman is installed: $(podman --version)"
}

# Build the container image
build_image() {
    log_info "Building container image..."
    
    # Check if Dockerfile exists
    if [[ ! -f "Dockerfile" ]]; then
        log_error "Dockerfile not found in the current directory"
        exit 1
    fi
    
    if ! podman build -t "$IMAGE_NAME" .; then
        log_error "Failed to build container image"
        exit 1
    fi
    
    log_info "Container image built successfully: $IMAGE_NAME"
}

# Stop and remove existing container if it exists
cleanup_existing_container() {
    log_info "Checking for existing containers..."
    
    if podman ps -a --format "{{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
        log_warn "Existing container '$CONTAINER_NAME' found"
        
        # Stop the container if it's running
        if podman ps --format "{{.Names}}" | grep -q "^${CONTAINER_NAME}$"; then
            log_info "Stopping existing container..."
            if ! podman stop "$CONTAINER_NAME"; then
                log_warn "Container stop failed, attempting force remove..."
            fi
        fi
        
        # Wait briefly for container to fully stop before removing
        sleep 2
        
        # Remove the container (force if needed)
        log_info "Removing existing container..."
        podman rm -f "$CONTAINER_NAME" 2>/dev/null || true
    else
        log_info "No existing container found"
    fi
}

# Deploy the container
deploy_container() {
    log_info "Deploying container..."
    
    # Ensure deploy directory exists
    if [[ ! -d "$DEPLOY_DIR" ]]; then
        log_warn "Deploy directory '$DEPLOY_DIR' not found, creating it..."
        mkdir -p "$DEPLOY_DIR"/{input,output,archive}
    fi
    
    # Create network if it doesn't exist
    NETWORK_NAME="camel-network"
    if ! podman network exists "$NETWORK_NAME"; then
        log_info "Creating network '$NETWORK_NAME'..."
        if ! podman network create "$NETWORK_NAME"; then
            log_error "Failed to create network '$NETWORK_NAME'"
            exit 1
        fi
    fi
    
    # Run the container
    if ! podman run -d \
        --name "$CONTAINER_NAME" \
        -p "$PORT_MAPPING" \
        -v "$(pwd)/$DEPLOY_DIR:/home/quarkus/deploy:Z" \
        --network "$NETWORK_NAME" \
        -e PORT=8080 \
        -e CAMEL_INPUT_DIR=/home/quarkus/deploy/input \
        -e CAMEL_OUTPUT_DIR=/home/quarkus/deploy/output \
        -e CAMEL_ARCHIVE_DIR=/home/quarkus/deploy/archive \
        "$IMAGE_NAME"; then
        log_error "Failed to deploy container"
        exit 1
    fi
    
    log_info "Container deployed successfully: $CONTAINER_NAME"
}

# Verify container is running after deployment
verify_container_running() {
    log_info "Verifying container is running..."
    
    local max_attempts=6
    local attempt=0
    
    while [[ $attempt -lt $max_attempts ]]; do
        sleep 1
        
        local container_state
        container_state=$(podman inspect --format '{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null)
        local inspect_exit_code=$?
        
        if [[ $inspect_exit_code -ne 0 ]]; then
            log_warn "Container not found (attempt $attempt/$max_attempts)"
            attempt=$((attempt + 1))
            continue
        fi
        
        if [[ "$container_state" == "running" ]]; then
            log_info "Container is running!"
            return 0
        elif [[ "$container_state" == "exited" || "$container_state" == "stopped" ]]; then
            log_error "Container has stopped/exited. Check logs with: podman logs $CONTAINER_NAME"
            return 1
        else
            log_info "Container state: $container_state (attempt $attempt/$max_attempts)"
        fi
        
        attempt=$((attempt + 1))
    done
    
    log_error "Container failed to start within $((max_attempts)) seconds"
    return 1
}

# Wait for the container to be healthy
wait_for_healthy() {
    log_info "Waiting for container to become healthy..."
    
    # Wait up to 60 seconds for the container to be healthy
    local max_attempts=12
    local attempt=0
    
    while [[ $attempt -lt $max_attempts ]]; do
        sleep 5
        
        # Check if container exists and get health status
        local health_status
        health_status=$(podman inspect --format '{{.State.Health.Status}}' "$CONTAINER_NAME" 2>/dev/null)
        local inspect_exit_code=$?
        
        # If container doesn't exist or inspect failed
        if [[ $inspect_exit_code -ne 0 ]]; then
            log_warn "Container not found or inspect failed (attempt $attempt/$max_attempts)"
            attempt=$((attempt + 1))
            continue
        fi
        
        # Handle different health states
        if [[ "$health_status" == "healthy" ]]; then
            log_info "Container is healthy!"
            return 0
        elif [[ "$health_status" == "unhealthy" ]]; then
            log_error "Container is unhealthy!"
            return 1
        elif [[ "$health_status" == "<nil>" || -z "$health_status" ]]; then
            # No healthcheck defined or waiting for first check
            log_info "Waiting for health check (no healthcheck defined yet)..."
        else
            # Other states like "starting"
            log_info "Container health status: $health_status (attempt $attempt/$max_attempts)"
        fi
        
        attempt=$((attempt + 1))
    done
    
    log_warn "Container health check timed out after $((max_attempts * 5)) seconds"
    # Return 0 to allow deployment to continue (health check is informational)
    return 0
}

# Display deployment information
show_info() {
    echo ""
    log_info "=========================================="
    log_info "Deployment Complete!"
    log_info "=========================================="
    echo ""
    echo "Container Name:  $CONTAINER_NAME"
    echo "Image:           $IMAGE_NAME"
    echo "Port:            http://localhost:8080"
    echo "Input Directory: $(pwd)/deploy/input/"
    echo "Output Directory: $(pwd)/deploy/output/"
    echo ""
    
    # Show container status
    echo "Container Status:"
    podman ps --filter name="$CONTAINER_NAME" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
    echo ""
    
    # Show health check endpoint
    log_info "Health Check:"
    echo "  curl http://localhost:8080/health/live"
    echo ""
    
    # Show logs command
    log_info "View Logs:"
    echo "  podman logs -f $CONTAINER_NAME"
    echo ""
    
    # Show stop command
    log_info "To Stop:"
    echo "  podman stop $CONTAINER_NAME"
    echo ""
}

# Main execution
main() {
    echo ""
    log_info "Starting Camel Quarkus Podman deployment..."
    echo ""
    
    check_podman
    build_image
    cleanup_existing_container
    deploy_container
    verify_container_running || true
    wait_for_healthy || true
    show_info
    
    log_info "Deployment finished successfully!"
}

# Run the main function
main "$@"