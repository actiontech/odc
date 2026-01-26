#!/usr/bin/env bash
# Orchestration script for ODC backend build

CI_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$CI_SCRIPT_DIR/libs"

# Source libs
source "$LIB_DIR/common.sh"
source "$LIB_DIR/backend_lib.sh"

function run_backend_build_stage() {
    log_environment_info "build_backend.sh"
    log_info "🚀 Starting Backend Build Stage..."

    # Note: Backend branch switching is done on host before container starts
    # (see ci/docker/manage_container.sh start_container function)

    # Step 1: Install local libs (Mandatory)
    log_execute "Step 1: Installing local library dependencies..."
    if ! install_local_libs; then
        log_error "Step 1 failed: Local library installation failed."
        exit 1
    fi
    log_success "Step 1 completed: Local libraries installed."

    # Step 2: Prepare Obclient (Mandatory for RPM)
    log_execute "Step 2: Preparing obclient driver..."
    if ! prepare_obclient_driver; then
        log_error "Step 2 failed: Obclient driver preparation failed."
        exit 1
    fi
    log_success "Step 2 completed: Obclient driver prepared."

    # Step 3: Build Core JAR
    log_execute "Step 3: Compiling backend core JAR..."
    if ! compile_backend_jar; then
        log_error "Step 3 failed: Backend compilation failed."
        exit 1
    fi
    log_success "Step 3 completed: Backend core JAR compiled."

    log_success "✅ Backend Build Stage completed successfully."
}

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    run_backend_build_stage
fi
