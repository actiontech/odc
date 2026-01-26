#!/usr/bin/env bash
# Maven environment checks

function check_maven() {
    if ! check_command "mvn"; then
        log_error "Maven check failed: mvn command not found"
        return 1
    fi

    local mvn_version=$(mvn -version | head -n 1 | cut -d ' ' -f 3)
    
    # Minimal version 3.6
    local major=$(echo "$mvn_version" | cut -d '.' -f 1)
    local minor=$(echo "$mvn_version" | cut -d '.' -f 2)

    if [ "$major" -lt 3 ] || { [ "$major" -eq 3 ] && [ "$minor" -lt 6 ]; }; then
        log_error "Maven check failed: Maven 3.6+ is required. Current version: $mvn_version"
        return 1
    fi

    log_info "  Maven: $mvn_version"
    return 0
}
