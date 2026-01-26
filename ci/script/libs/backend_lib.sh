#!/usr/bin/env bash
# Backend build and dependency management library

function install_local_libs() {
    local project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
    local mvn_log=$(mktemp)
    
    # 1. Install ob-sql-parser
    log_execute "  Installing ob-sql-parser..."
    pushd "$project_root/libs/ob-sql-parser" >/dev/null || return 1
    if ! mvn clean install -Dmaven.test.skip=true -q -B >"$mvn_log" 2>&1; then
        # Show errors from build log
        if [ -s "$mvn_log" ]; then
            grep -iE "(error|Error|ERROR|failed|Failed|FAILED|BUILD FAILURE)" "$mvn_log" | head -30
        fi
        rm -f "$mvn_log"
        log_error "Failed to install ob-sql-parser."
        popd >/dev/null
        return 1
    fi
    rm -f "$mvn_log"
    popd >/dev/null

    # 2. Install db-browser
    log_execute "  Installing db-browser..."
    pushd "$project_root/libs/db-browser" >/dev/null || return 1
    if ! mvn clean install -Dmaven.test.skip=true -q -B >"$mvn_log" 2>&1; then
        if [ -s "$mvn_log" ]; then
            grep -iE "(error|Error|ERROR|failed|Failed|FAILED|BUILD FAILURE)" "$mvn_log" | head -30
        fi
        rm -f "$mvn_log"
        log_error "Failed to install db-browser."
        popd >/dev/null
        return 1
    fi
    rm -f "$mvn_log"
    popd >/dev/null

    # 3. Install import jars (pty4j, purejavacomm)
    log_execute "  Installing external JARs (pty4j, purejavacomm)..."
    pushd "$project_root/import" >/dev/null || return 1
    
    if [ ! -f "./pty4j-0.11.4.jar" ]; then
        log_error "pty4j-0.11.4.jar not found in import directory"
        popd >/dev/null
        return 1
    fi
    if ! mvn install:install-file -Dfile=./pty4j-0.11.4.jar -DgroupId=org.jetbrains.pty4j -DartifactId=pty4j -Dversion=0.11.4 -Dpackaging=jar -q -B >"$mvn_log" 2>&1; then
        if [ -s "$mvn_log" ]; then
            grep -iE "(error|Error|ERROR|failed|Failed|FAILED)" "$mvn_log" | head -10
        fi
        rm -f "$mvn_log"
        log_error "Failed to install pty4j"
        popd >/dev/null
        return 1
    fi
    
    if [ ! -f "./purejavacomm-0.0.11.1.jar" ]; then
        log_error "purejavacomm-0.0.11.1.jar not found in import directory"
        rm -f "$mvn_log"
        popd >/dev/null
        return 1
    fi
    if ! mvn install:install-file -Dfile=./purejavacomm-0.0.11.1.jar -DgroupId=org.jetbrains.pty4j -DartifactId=purejavacomm -Dversion=0.0.11.1 -Dpackaging=jar -q -B >"$mvn_log" 2>&1; then
        if [ -s "$mvn_log" ]; then
            grep -iE "(error|Error|ERROR|failed|Failed|FAILED)" "$mvn_log" | head -10
        fi
        rm -f "$mvn_log"
        log_error "Failed to install purejavacomm"
        popd >/dev/null
        return 1
    fi
    rm -f "$mvn_log"
    popd >/dev/null
    
    return 0
}

function prepare_obclient_driver() {
    
    local project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
    local target_path="$project_root/import/obclient.tar.gz"
    
    if [ "${FETCH_FROM_OSS:-0}" == "1" ]; then
        log_warn "OSS fetch logic not yet implemented, falling back to local copy."
    fi

    # Detect architecture
    local arch=$(uname -m)
    local source_dir="linux_x86"
    # uname -m on typical linux returns x86_64 or aarch64
    [[ "$arch" == "aarch64" ]] && source_dir="linux_arm64"
    [[ "$arch" == "x86_64" ]] && source_dir="linux_x86"
    
    local source_path="$project_root/build-resource/obclient/2.2.4/$source_dir/obclient.tar.gz"
    
    if [ -f "$source_path" ]; then
        cp "$source_path" "$target_path" >/dev/null 2>&1
        return 0
    else
        log_warn "Obclient source not found at $source_path. Packaging might fail."
        return 0  # Non-fatal, continue build
    fi
}

function compile_backend_jar() {
    
    local project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
    pushd "$project_root" >/dev/null || return 1

    local mvn_args=("-Dmaven.test.skip=true" "-q" "-B")
    if [ -n "$BUILD_PROFILE" ]; then
        log_info "  Using Maven Profile: $BUILD_PROFILE"
        mvn_args+=("-P" "$BUILD_PROFILE")
    fi

    local mvn_log=$(mktemp)
    if ! mvn clean install "${mvn_args[@]}" >"$mvn_log" 2>&1; then
        # Show errors from build log
        if [ -s "$mvn_log" ]; then
            # Show compilation errors, test failures, and build failures
            grep -iE "(error|Error|ERROR|failed|Failed|FAILED|BUILD FAILURE|Compilation failure|Test failure)" "$mvn_log" | head -50
            # Also show the last 20 lines for context
            echo "--- Last 20 lines of build log ---"
            tail -20 "$mvn_log"
        fi
        rm -f "$mvn_log"
        log_error "Backend compilation failed."
        popd >/dev/null
        return 1
    fi
    
    # Show build success summary if available
    if grep -qiE "(BUILD SUCCESS)" "$mvn_log" 2>/dev/null; then
        grep -iE "(BUILD SUCCESS)" "$mvn_log" | head -1
    fi
    rm -f "$mvn_log"
    popd >/dev/null
    return 0
}
