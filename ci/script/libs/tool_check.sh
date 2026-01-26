#!/usr/bin/env bash
# System tools checks

function check_system_tools() {
    local tools=("rpmbuild" "git" "curl" "zip" "file")
    local missing_tools=()

    for tool in "${tools[@]}"; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            missing_tools+=("$tool")
        fi
    done

    if [ ${#missing_tools[@]} -ne 0 ]; then
        log_error "System tools check failed: Missing tools: ${missing_tools[*]}"
        return 1
    fi

    log_info "  System tools: ${tools[*]}"
    return 0
}
