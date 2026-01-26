#!/usr/bin/env bash
# Common logging and utility functions for CI scripts

function log_info() {
    echo "$(date +"%Y-%m-%dT%H:%M:%S.%Z") ℹ️ [INFO]" "$*"
}

function log_execute() {
    echo "$(date +"%Y-%m-%dT%H:%M:%S.%Z") ⚙️ [EXECUTE]" "$*"
}

function log_success() {
    echo "$(date +"%Y-%m-%dT%H:%M:%S.%Z") ✅ [SUCCESS]" "$*"
}

function log_warn() {
    echo "$(date +"%Y-%m-%dT%H:%M:%S.%Z") ⚠️ [WARN]" "$*"
}

function log_error() {
    echo 1>&2 "$(date +"%Y-%m-%dT%H:%M:%S.%Z") ❌ [ERROR]" "$*"
}

function check_command() {
    local cmd="$1"
    if ! command -v "$cmd" >/dev/null 2>&1; then
        log_error "Command '$cmd' not found."
        return 1
    fi
    return 0
}

function get_package_manager() {
    if command -v yum >/dev/null 2>&1; then
        echo "yum"
    elif command -v apt-get >/dev/null 2>&1; then
        echo "apt-get"
    else
        echo "unknown"
    fi
}

function log_environment_info() {
    local script_name="$1"
    local current_pwd=$(pwd)
    local environment="Host"
    
    if [ "$IS_IN_CONTAINER" = "1" ]; then
        environment="Container"
    fi
    
    log_info "Environment: $environment | Working Directory: $current_pwd | Script: $script_name"
}
