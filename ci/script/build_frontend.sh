#!/usr/bin/env bash
# Orchestration script for ODC frontend build

CI_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$CI_SCRIPT_DIR/libs"

# Source common and modular libs
source "$LIB_DIR/common.sh"
source "$LIB_DIR/frontend_lib.sh"

function run_frontend_build_stage() {
    log_environment_info "build_frontend.sh"
    log_info "🚀 Starting Frontend Build Stage..."

    # Note: Submodule sync (branch switching) should be done on host before container starts
    # Use ./ci/script/switch_branches.sh on host before starting container

    # Build Frontend (Mandatory)
    log_execute "Building frontend module..."
    if ! build_frontend_module; then
        log_error "Frontend build failed."
        exit 1
    fi

    log_success "✅ Frontend Build Stage completed successfully."
}

# Execute if run directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    run_frontend_build_stage
fi
