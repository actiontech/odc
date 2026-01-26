#!/usr/bin/env bash
# Step 5: RPM Packaging Orchestration

set -e

# Set CI directories
CI_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CI_SCRIPT_DIR="$CI_DIR/script"
PROJECT_ROOT="$(cd "$CI_DIR/.." && pwd)"

# Source libraries
source "$CI_SCRIPT_DIR/libs/common.sh"
source "$CI_SCRIPT_DIR/libs/packaging_lib.sh"

log_environment_info "build_rpm_pkg.sh"
log_info "🚀 Starting RPM Packaging Stage..."

# Step 1: Build RPM
log_execute "Step 1: Building RPM package..."
if ! build_rpm_package; then
    log_error "Step 1 failed: RPM package build failed."
    exit 1
fi
log_success "Step 1 completed: RPM package built successfully."

# Step 2: Organize artifacts
log_execute "Step 2: Organizing build artifacts..."
if ! organize_artifacts; then
    log_error "Step 2 failed: Artifact organization failed."
    exit 1
fi
log_success "Step 2 completed: Artifacts organized."

log_success "✅ RPM Packaging Stage completed successfully."
