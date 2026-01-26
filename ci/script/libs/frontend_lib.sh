#!/usr/bin/env bash
# Frontend build library

function build_frontend_module() {
    local project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
    local sqlconsole_path="$project_root/client"
    local backend_static_path="$project_root/server/odc-server/src/main/resources/static"

    if [ ! -d "$sqlconsole_path" ]; then
        log_error "Step 1 failed: SQLConsole directory not found at: $sqlconsole_path"
        return 1
    fi

    pushd "$sqlconsole_path" >/dev/null || return 1

    # Step 1: Configure pnpm and install dependencies
    log_execute "Step 1: Installing frontend dependencies..."
    pnpm config set registry https://registry.npmmirror.com >/dev/null 2>&1
    
    # Pre-emptively remove node_modules to avoid "Proceed?" prompts in some pnpm versions
    if [ -d "node_modules" ]; then
        rm -rf node_modules
    fi

    # Retry mechanism for pnpm install
    local retry=0
    local max_retries=3
    local install_log=$(mktemp)
    until [ $retry -ge $max_retries ]; do
        # Use --no-frozen-lockfile and --force for CI compatibility
        # Suppress progress output but capture errors to temp file
        if pnpm install --no-frozen-lockfile --force --reporter=silent >"$install_log" 2>&1; then
            # Check if install actually succeeded
            if [ -d "node_modules" ]; then
                # Show any errors from install log
                if grep -qiE "(error|Error|ERROR|failed|Failed|FAILED)" "$install_log" 2>/dev/null | grep -v "WARN" | head -10; then
                    grep -iE "(error|Error|ERROR|failed|Failed|FAILED)" "$install_log" | grep -v "WARN" | head -10
                fi
                rm -f "$install_log"
                log_success "Step 1 completed: Frontend dependencies installed."
                break
            fi
        else
            # Show errors if install failed
            if [ -s "$install_log" ]; then
                grep -iE "(error|Error|ERROR|failed|Failed|FAILED)" "$install_log" 2>/dev/null | head -10
            fi
        fi
        
        retry=$((retry + 1))
        if [ $retry -lt $max_retries ]; then
            log_warn "Step 1: pnpm install failed. Retrying ($retry/$max_retries)..."
            sleep 2
        fi
    done
    rm -f "$install_log"

    if [ $retry -eq $max_retries ]; then
        log_error "Step 1 failed: Failed to install frontend dependencies after $max_retries attempts."
        popd >/dev/null
        return 1
    fi

    # Step 2: Build frontend
    log_execute "Step 2: Building frontend application..."
    # Suppress verbose webpack output but capture errors
    local build_log=$(mktemp)
    export CI=true
    if ! pnpm run build:odc >"$build_log" 2>&1; then
        # Show errors from build log
        if [ -s "$build_log" ]; then
            grep -iE "(error|Error|ERROR|failed|Failed|FAILED)" "$build_log" | head -30
        fi
        rm -f "$build_log"
        log_error "Step 2 failed: Frontend build command failed."
        popd >/dev/null
        return 1
    fi
    
    # Show compilation success message if available
    if grep -qiE "(compiled successfully|build.*success)" "$build_log" 2>/dev/null; then
        grep -iE "(compiled successfully|build.*success)" "$build_log" | head -1
    fi
    rm -f "$build_log"
    
    # Check if build actually succeeded by verifying dist directory exists
    if [ ! -d "dist/renderer" ]; then
        log_error "Step 2 failed: Build artifacts not found in dist/renderer"
        popd >/dev/null
        return 1
    fi
    
    log_success "Step 2 completed: Frontend build completed."

    popd >/dev/null

    # Step 3: Sync artifacts to backend
    log_execute "Step 3: Syncing build artifacts to backend static resources..."
    if [ ! -d "$backend_static_path" ]; then
        mkdir -p "$backend_static_path"
    fi

    rm -rf "${backend_static_path:?}"/*

    local source_dir="$sqlconsole_path/dist/renderer"
    if [ ! -d "$source_dir" ]; then
        log_error "Step 3 failed: Frontend build artifacts not found at: $source_dir"
        return 1
    fi
    
    if cp -rf "$source_dir"/* "$backend_static_path/" >/dev/null 2>&1; then
        log_success "Step 3 completed: Frontend artifacts synchronized to backend."
        return 0
    else
        log_error "Step 3 failed: Failed to copy artifacts to backend."
        return 1
    fi
}
