#!/usr/bin/env bash
# Switch backend and frontend branches on host before container starts

CI_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$CI_SCRIPT_DIR/libs"

# Source required libraries
source "$LIB_DIR/common.sh"
source "$LIB_DIR/env_init.sh"
source "$LIB_DIR/git_lib.sh"
source "$LIB_DIR/submodule_lib.sh"

function switch_branches() {
    log_environment_info "switch_branches.sh"
    log_info "🔄 Starting branch switching on host..."
    
    # Ensure we're not in container (this script should only run on host)
    if [ "$IS_IN_CONTAINER" = "1" ]; then
        log_error "This script should only run on host, not inside container."
        return 1
    fi
    
    # Step 1: Initialize and print environment variables on host
    log_execute "Step 1: Initializing environment variables and printing configuration..."
    if ! init_default_vars; then
        log_error "Step 1 failed: Environment variable initialization failed."
        return 1
    fi
    print_env_vars
    log_success "Step 1 completed: Environment variables initialized."
    
    # Step 2: Switch backend branch
    log_execute "Step 2: Switching backend repository to branch ${ODC_SERVER_BRANCH:-test/build_rpm}..."
    if ! switch_backend_branch; then
        log_error "Step 2 failed: Backend branch switch failed."
        return 1
    fi
    log_success "Step 2 completed: Backend branch switched successfully."
    
    # Step 3: Switch frontend branch (via submodule sync)
    log_execute "Step 3: Switching frontend submodule to branch ${ODC_UI_BRANCH:-dev-4.3.4-v2}..."
    if ! sync_submodules; then
        log_error "Step 3 failed: Frontend submodule sync failed."
        return 1
    fi
    log_success "Step 3 completed: Frontend submodule switched successfully."
    
    log_success "🎉 All branches switched successfully on host."
    return 0
}

# Execute if run directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    switch_branches
    exit $?
fi
