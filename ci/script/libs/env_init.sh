#!/usr/bin/env bash
# Environment variables initialization and printing library

function print_env_vars() {
    # Define critical variables to track
    local critical_vars=(
        "RPM_RELEASE"
        "SYNC_SUBMODULE"
        "BUILD_FRONTEND"
        "ODC_UI_BRANCH"
        "ODC_SERVER_BRANCH"
        "ODC_UI_URL"
        "ODC_BUILD_RESOURCE_URL"
        "ODC_BUILD_IMAGE"
        "JAVA_HOME"
        "MAVEN_HOME"
        "NODE_HOME"
    )

    echo "----------------------------------------------------------------"
    printf "%-25s | %-40s\n" "Variable Name" "Current Value"
    echo "----------------------------------------------------------------"
    
    for var in "${critical_vars[@]}"; do
        printf "%-25s | %-40s\n" "$var" "${!var:-<NOT SET>}"
    done
    
    echo "----------------------------------------------------------------"
}

function init_default_vars() {
    local defaults_set=0
    
    # RPM_RELEASE: Default to current date YYYYMMDD
    if [ -z "$RPM_RELEASE" ]; then
        export RPM_RELEASE=$(date +%Y%m%d)
        defaults_set=1
    fi

    # SYNC_SUBMODULE: Default to 1
    if [ -z "$SYNC_SUBMODULE" ]; then
        export SYNC_SUBMODULE=1
        defaults_set=1
    fi

    # BUILD_FRONTEND: Default to 1
    if [ -z "$BUILD_FRONTEND" ]; then
        export BUILD_FRONTEND=1
        defaults_set=1
    fi

    # ODC_UI_BRANCH: Default to dev-4.3.4
    if [ -z "$ODC_UI_BRANCH" ]; then
        export ODC_UI_BRANCH="dev-4.3.4"
        defaults_set=1
    fi

    # ODC_SERVER_BRANCH: Default to dev/4.3.4
    if [ -z "$ODC_SERVER_BRANCH" ]; then
        export ODC_SERVER_BRANCH="dev/4.3.4"
        defaults_set=1
    fi

    # ODC_UI_URL: Default URL for client submodule
    if [ -z "$ODC_UI_URL" ]; then
        export ODC_UI_URL="https://github.com/actiontech/odc-client.git"
        defaults_set=1
    fi

    # ODC_BUILD_RESOURCE_URL: Default URL for build-resource submodule
    if [ -z "$ODC_BUILD_RESOURCE_URL" ]; then
        export ODC_BUILD_RESOURCE_URL="https://github.com/winfredLIN/odc-build-resource.git"
        defaults_set=1
    fi

    # ODC_BUILD_IMAGE: Default build image
    if [ -z "$ODC_BUILD_IMAGE" ]; then
        export ODC_BUILD_IMAGE="reg.actiontech.com/actiontech-dev/odc/build-env:1.0"
        defaults_set=1
    fi

    if [ $defaults_set -eq 1 ]; then
        log_info "Applied default values for unset environment variables."
    fi
    
    return 0
}
