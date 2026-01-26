#!/usr/bin/env bash
# Library for RPM packaging and artifact management

function build_rpm_package() {
    
    # Set default RPM release if not provided
    local rpm_release=${RPM_RELEASE:-$(date +%Y%m%d)}
    local build_profile_arg=""
    
    if [ -n "$BUILD_PROFILE" ]; then
        log_info "  Using Maven profile: $BUILD_PROFILE"
        build_profile_arg="-P$BUILD_PROFILE"
    fi

    # Go to the module that performs RPM packaging
    # Usually server/odc-server or a distribution module
    # Based on existing ODC structure, it's often in server/odc-server
    cd "$PROJECT_ROOT/server/odc-server" || return 1
    
    local mvn_log=$(mktemp)
    if ! mvn rpm:rpm -Dmaven.test.skip=true -Drpm.release="${rpm_release}" ${build_profile_arg} -q -B >"$mvn_log" 2>&1; then
        # Show errors from build log
        if [ -s "$mvn_log" ]; then
            # Show RPM packaging errors, build failures, and warnings
            grep -iE "(error|Error|ERROR|failed|Failed|FAILED|BUILD FAILURE|RPM.*failed)" "$mvn_log" | head -50
            # Also show the last 20 lines for context
            echo "--- Last 20 lines of build log ---"
            tail -20 "$mvn_log"
        fi
        rm -f "$mvn_log"
        log_error "Maven RPM packaging failed."
        return 1
    fi
    
    # Show build success summary if available
    if grep -qiE "(BUILD SUCCESS|RPM.*success)" "$mvn_log" 2>/dev/null; then
        grep -iE "(BUILD SUCCESS|RPM.*success)" "$mvn_log" | head -1
    fi
    rm -f "$mvn_log"
    return 0
}

function organize_artifacts() {
    
    local dist_jar_dir="$PROJECT_ROOT/distribution/jar"
    mkdir -p "$dist_jar_dir"

    # 1. Locate and log RPM position (No copying as per request)
    local rpm_file
    rpm_file=$(find "$PROJECT_ROOT/server/odc-server/target/rpm" -name "*.rpm" 2>/dev/null | head -n 1)
    if [ -n "$rpm_file" ]; then
        log_info "  RPM package: $rpm_file"
    else
        log_warn "  No RPM file found in target directory."
    fi

    # 2. Copy JAR to distribution/jar/odc.jar
    local jar_file="$PROJECT_ROOT/server/odc-server/target/odc-server.jar"
    if [ -f "$jar_file" ]; then
        cp "$jar_file" "$dist_jar_dir/odc.jar" >/dev/null 2>&1
        log_info "  JAR copied to: $dist_jar_dir/odc.jar"
    else
        # Try to find the versioned jar if odc-server.jar doesn't exist
        local versioned_jar
        versioned_jar=$(find "$PROJECT_ROOT/server/odc-server/target" -maxdepth 1 -name "odc-server-*.jar" ! -name "*-sources.jar" 2>/dev/null | head -n 1)
        if [ -n "$versioned_jar" ]; then
            cp "$versioned_jar" "$dist_jar_dir/odc.jar" >/dev/null 2>&1
            log_info "  JAR copied to: $dist_jar_dir/odc.jar"
        else
            log_warn "  JAR file not found in target directory."
        fi
    fi

    return 0
}

function move_rpm_to_root() {
    log_info "Step 5.3: Skipping move to root as per request."
    return 0
}
