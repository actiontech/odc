#!/usr/bin/env bash
# System tools installation library

function install_system_tools() {
    log_info "Checking and installing missing system tools..."
    local pkg_manager=$(get_package_manager)
    local tools=("git" "curl" "zip")
    local missing_tools=()

    # Special check for rpmbuild
    if ! command -v rpmbuild >/dev/null 2>&1; then
        if [ "$pkg_manager" == "yum" ]; then
            missing_tools+=("rpm-build")
        elif [ "$pkg_manager" == "apt-get" ]; then
            missing_tools+=("rpm")
        fi
    fi

    for tool in "${tools[@]}"; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            missing_tools+=("$tool")
        fi
    done

    if [ ${#missing_tools[@]} -eq 0 ]; then
        log_success "All system tools are already installed."
        return 0
    fi

    log_info "Missing tools found: ${missing_tools[*]}. Reporting..."
    
    log_warn "No sudo permission available on this Agent. Skipping automatic system package installation."
    log_warn "Please ensure the following packages are pre-installed by the administrator: ${missing_tools[*]}"
    
    return 0 # Return 0 to allow the pipeline to proceed to the next step or re-check
}
