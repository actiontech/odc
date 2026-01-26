#!/usr/bin/env bash
# One-click ODC build script using the modular container management

CI_DOCKER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MANAGE_SCRIPT="$CI_DOCKER_DIR/manage_container.sh"

# 1. Switch branches on Host
echo "Step 1: Switching branches on Host..."
"$(cd "$CI_DOCKER_DIR/../.." && pwd)/ci/script/switch_branches.sh" || {
    echo "Error: Failed to switch branches on host."
    exit 1
}

# 2. Build Image if needed (Optional in CI, usually pulled from registry)
echo "Step 2: Preparing Docker image..."
# Source config to get IMAGE_NAME correctly expanded
source "$CI_DOCKER_DIR/config.sh"
# Only build if not in a CI environment or if explicitly requested
if [ -z "$GO_PIPELINE_NAME" ]; then
    echo "Local environment detected, building image locally..."
    docker build -t "$IMAGE_NAME" "$CI_DOCKER_DIR"
else
    echo "GoCD environment detected, skipping local build, will pull from registry."
fi

# 3. Start Container
echo "Step 3: Starting container..."
if ! "$MANAGE_SCRIPT" start; then
    echo "Error: Failed to start container."
    exit 1
fi

# 4. Execute Stages
echo "Step 4: Executing build stages..."
# We chain them here for local one-click experience
"$MANAGE_SCRIPT" exec "./ci/script/env_check.sh" && \
"$MANAGE_SCRIPT" exec "./ci/script/build_frontend.sh" && \
"$MANAGE_SCRIPT" exec "./ci/script/build_backend.sh" && \
"$MANAGE_SCRIPT" exec "./ci/script/build_rpm_pkg.sh"

EXIT_CODE=$?

# 5. Stop Container
echo "Step 5: Cleaning up..."
"$MANAGE_SCRIPT" stop

if [ $EXIT_CODE -eq 0 ]; then
    echo "SUCCESS: Local build completed successfully."
else
    echo "ERROR: Local build failed."
fi

exit $EXIT_CODE
