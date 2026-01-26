#!/usr/bin/env bash
# OS compatibility checks

function check_os() {
    local os=$(uname -s)
    if [ "$os" != "Linux" ]; then
        log_error "OS check failed: This build script only supports Linux. Current OS: $os"
        return 1
    fi

    if [ -f /etc/os-release ]; then
        . /etc/os-release
        log_info "  OS: $NAME $VERSION"
    else
        log_info "  OS: $(uname -a)"
    fi
    return 0
}
