#!/usr/bin/env bash
# Orchestration script for ODC environment check and initialization

# Set CI_SCRIPT_DIR to the directory where this script resides
CI_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$CI_SCRIPT_DIR/libs"

# Source modular library scripts
source "$LIB_DIR/common.sh"
source "$LIB_DIR/os_check.sh"
source "$LIB_DIR/tool_check.sh"
source "$LIB_DIR/java_check.sh"
source "$LIB_DIR/maven_check.sh"
source "$LIB_DIR/node_check.sh"
source "$LIB_DIR/env_init.sh"

function run_env_check() {
    log_environment_info "env_check.sh"
    log_info "🚀 Starting environment check and initialization for ODC build..."
    
    # Step 1: Initialize default environment variables
    log_execute "Step 1: Initializing environment variables..."
    if ! init_default_vars; then
        log_error "Step 1 failed: Environment variable initialization failed."
        exit 1
    fi
    log_success "Step 1 completed: Environment variables initialized."
    
    # Step 2: Print environment variables
    log_execute "Step 2: Displaying build configuration..."
    print_env_vars
    log_success "Step 2 completed: Configuration displayed."
    
    # Step 3: Check system environment
    log_execute "Step 3: Checking system environment and dependencies..."
    local failed=0

    if ! check_os; then
        failed=1
    fi
    if ! check_system_tools; then
        failed=1
    fi
    if ! check_java; then
        failed=1
    fi
    if ! check_maven; then
        failed=1
    fi
    if ! check_node; then
        failed=1
    fi

    if [ $failed -eq 1 ]; then
        log_error "Step 3 failed: System environment check failed. Please resolve the missing dependencies."
        exit 1
    fi

    log_success "Step 3 completed: All system checks passed."
    log_success "✅ All environment checks and initialization completed successfully."
}

# Execute if run directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    run_env_check
fi
