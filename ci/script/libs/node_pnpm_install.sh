#!/usr/bin/env bash
# Node.js and pnpm installation library

function install_node_pnpm() {
    log_info "Installing Node.js 18 and pnpm 10..."
    
    # Check if node 18 is already installed
    if command -v node >/dev/null 2>&1 && [[ $(node -v) == v18.* ]]; then
        log_info "Node.js 18 is already installed ($(node -v))."
    else
        log_info "Installing Node.js 18 using nvs..."
        local NVS_HOME="$HOME/.nvs"
        if [ ! -d "$NVS_HOME" ]; then
            git clone https://github.com/jasongin/nvs --depth=1 "$NVS_HOME"
        fi
        source "$NVS_HOME/nvs.sh" install
        nvs add 18.20.0
        nvs use 18.20.0
        nvs link 18.20.0
    fi

    log_info "Checking pnpm 10..."
    if command -v pnpm >/dev/null 2>&1 && [[ $(pnpm -v) == 10.* ]]; then
        log_info "pnpm 10 is already installed ($(pnpm -v))."
        return 0
    fi

    log_info "Installing pnpm 10..."
    # Attempt to install pnpm. With nvs or local node, this should work without sudo.
    if npm install -g pnpm@10.28.1; then
        log_success "pnpm 10 installed successfully."
    else
        log_warn "Failed to install pnpm 10 globally. Building might fail if pnpm is missing."
        log_warn "Try running: npm install -g pnpm@10.28.1 manually."
    fi

    if command -v pnpm >/dev/null 2>&1; then
        log_success "Node.js and pnpm installation completed."
        pnpm -v
        return 0
    else
        return 1
    fi
}
