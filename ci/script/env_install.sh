#!/usr/bin/env bash
# Orchestration script for ODC environment installation

CI_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LIB_DIR="$CI_SCRIPT_DIR/libs"

# Source libraries
source "$LIB_DIR/common.sh"
source "$LIB_DIR/system_tool_install.sh"
source "$LIB_DIR/node_pnpm_install.sh"

function run_env_install() {
    log_info "🚀 Starting environment installation for ODC build..."
    
    local failed=0

    # 1. Install system tools (rpmbuild, git, etc.)
    install_system_tools || failed=1
    
    # 2. Install Node.js & pnpm
    install_node_pnpm || failed=1

    if [ $failed -eq 1 ]; then
        log_error "❌ Environment installation failed."
        exit 1
    fi

    log_success "🎉 All environment dependencies installed successfully."
}

# Execute if run directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    run_env_install
fi
