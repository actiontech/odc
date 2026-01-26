#!/usr/bin/env bash
# Helper script to manage the lifecycle of the ODC build container for GoCD multi-stage execution

# --- Configuration ---
CI_DOCKER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$CI_DOCKER_DIR/config.sh"

# Source common logging functions
CI_SCRIPT_DIR="$CI_DOCKER_DIR/../script"
source "$CI_SCRIPT_DIR/libs/common.sh"

PROJECT_ROOT="$(cd "$CI_DOCKER_DIR/../.." && pwd)"

function start_container() {
    log_environment_info "manage_container.sh (start)"
    log_info "🚀 Starting ODC build container: $CONTAINER_NAME..."
    
    # Note: Branch switching should be done before calling this function
    # Use ./ci/script/switch_branches.sh on host before starting container
    
    # Step 1: Remove stale container if exists
    log_execute "Step 1: Checking for existing container..."
    if [ "$(docker ps -aq -f name=^/${CONTAINER_NAME}$)" ]; then
        log_info "Removing existing container: $CONTAINER_NAME"
        docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1
    fi
    log_success "Step 1 completed: Container cleanup done."

    # Step 2: Pull the latest image from registry
    log_execute "Step 2: Pulling Docker image $IMAGE_NAME..."
    if docker pull "$IMAGE_NAME" >/dev/null 2>&1; then
        log_success "Step 2 completed: Docker image pulled successfully."
    else
        log_warn "Step 2: Failed to pull image, will use local image if available."
    fi

    # Step 3: Start the container in detached mode
    log_execute "Step 3: Starting container with volume mount..."
    local container_id
    container_id=$(docker run -d --name "$CONTAINER_NAME" \
        -v "$PROJECT_ROOT":/root/odc/source_code \
        -e IS_IN_CONTAINER=1 \
        -e RPM_RELEASE="${RPM_RELEASE:-$(date +%Y%m%d)}" \
        -e ODC_UI_BRANCH="${ODC_UI_BRANCH:-dev-4.3.4-v2}" \
        -e ODC_SERVER_BRANCH="${ODC_SERVER_BRANCH:-test/build_rpm}" \
        -e ODC_UI_URL="${ODC_UI_URL}" \
        -e ODC_BUILD_RESOURCE_URL="${ODC_BUILD_RESOURCE_URL}" \
        -e ODC_BUILD_IMAGE="${IMAGE_NAME}" \
        -e SYNC_SUBMODULE=1 \
        -e BUILD_FRONTEND=1 \
        "$IMAGE_NAME" \
        tail -f /dev/null 2>&1)
    local run_exit_code=$?

    if [ $run_exit_code -eq 0 ] && [ -n "$container_id" ]; then
        log_success "Step 3 completed: Container started successfully."
        
        # Step 4: Configure Git safe.directory
        log_execute "Step 4: Configuring Git safe.directory for repositories..."
        local git_config_failed=0
        
        if ! docker exec "$CONTAINER_NAME" git config --global --add safe.directory /root/odc/source_code >/dev/null 2>&1; then
            log_warn "Failed to configure safe.directory for main repository"
            git_config_failed=1
        fi
        if ! docker exec "$CONTAINER_NAME" git config --global --add safe.directory /root/odc/source_code/client >/dev/null 2>&1; then
            log_warn "Failed to configure safe.directory for client submodule"
            git_config_failed=1
        fi
        if ! docker exec "$CONTAINER_NAME" git config --global --add safe.directory /root/odc/source_code/build-resource >/dev/null 2>&1; then
            log_warn "Failed to configure safe.directory for build-resource submodule"
            git_config_failed=1
        fi
        
        if [ $git_config_failed -eq 0 ]; then
            log_success "Step 4 completed: Git safe.directory configured."
        else
            log_warn "Step 4: Some Git safe.directory configurations failed, but continuing..."
        fi
    else
        log_error "Step 3 failed: Failed to start container $CONTAINER_NAME."
        exit 1
    fi
    
    log_success "✅ Container $CONTAINER_NAME is ready for build tasks."
}

function exec_command() {
    local cmd="$1"
    if [ -z "$cmd" ]; then
        log_error "No command specified for exec."
        exit 1
    fi

    log_info "Executing in $CONTAINER_NAME: $cmd"
    # Execute the command inside the container
    docker exec "$CONTAINER_NAME" /bin/bash -c "cd /root/odc/source_code && $cmd"
    
    local exit_code=$?
    if [ $exit_code -ne 0 ]; then
        log_error "Command failed with exit code $exit_code."
        exit $exit_code
    fi
}

function stop_container() {
    log_info "Stopping and removing ODC build container: $CONTAINER_NAME..."
    docker stop "$CONTAINER_NAME" >/dev/null 2>&1
    docker rm "$CONTAINER_NAME" >/dev/null 2>&1
    log_success "Container $CONTAINER_NAME cleaned up."
}

# --- Action Dispatcher ---
case "$1" in
    start)
        start_container
        ;;
    exec)
        exec_command "$2"
        ;;
    stop)
        stop_container
        ;;
    *)
        log_error "Usage: $0 {start|exec <command>|stop}"
        exit 1
        ;;
esac
