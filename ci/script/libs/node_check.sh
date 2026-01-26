#!/usr/bin/env bash
# Node.js and pnpm environment checks

function check_node() {
    if ! check_command "node"; then
        log_error "Node.js check failed: node command not found"
        return 1
    fi

    local node_version=$(node -v | sed 's/v//')
    if [[ "$node_version" != 18.* && "$node_version" != 16.* ]]; then
        log_error "Node.js check failed: Node.js 18.x or 16.x is required. Current version: v$node_version"
        return 1
    fi

    if ! check_command "pnpm"; then
        log_error "pnpm check failed: pnpm not found. Please ensure it is installed (pnpm 10.x is required)."
        return 1
    fi

    local pnpm_version=$(pnpm -v)
    if [[ "$pnpm_version" != 10.* && "$pnpm_version" != 8.* ]]; then
        log_error "pnpm check failed: pnpm 10.x or 8.x is required. Current version: $pnpm_version"
        return 1
    fi

    log_info "  Node.js: $node_version, pnpm: $pnpm_version"
    return 0
}
