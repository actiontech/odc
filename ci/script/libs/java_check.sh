#!/usr/bin/env bash
# Java environment checks

function check_java() {
    if ! check_command "java"; then
        log_error "Java check failed: java command not found"
        return 1
    fi

    local java_version=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2)
    if [[ "$java_version" != 1.8* ]]; then
        log_error "Java check failed: Java 1.8 (JDK 8) is required. Current version: $java_version"
        return 1
    fi

    log_info "  Java: $java_version"
    return 0
}
