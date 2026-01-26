#!/usr/bin/env bash
# Submodule management library

function sync_submodules() {
    # If ODC_UI_BRANCH is set, we might need to update the submodule branch
    local target_branch="${ODC_UI_BRANCH:-dev-4.3.4-v2}"
    
    if [ "$IS_IN_CONTAINER" = "1" ]; then
        log_info "Running in container. Checking if client submodule matches $target_branch..."
        # In container, we usually don't want to sync everything, 
        # but we should at least ensure the branch is correct if git is available
        if [ -d ".git" ]; then
             log_info "Updating client submodule branch to $target_branch..."
             git submodule set-branch --branch "$target_branch" client || true
        fi
        return 0
    fi

    # Git submodule command need to run from the top level of the working tree
    local project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
    pushd "$project_root" >/dev/null || return 1

    log_execute "Initializing and syncing submodules..."
    git submodule init >/dev/null 2>&1
    git submodule sync >/dev/null 2>&1
    
    if [ -n "$ODC_UI_URL" ]; then
        git config submodule.client.url "$ODC_UI_URL" >/dev/null 2>&1
    fi

    if [ -n "$ODC_BUILD_RESOURCE_URL" ]; then
        git config submodule.build-resource.url "$ODC_BUILD_RESOURCE_URL" >/dev/null 2>&1
    fi

    if [ -n "$ODC_UI_BRANCH" ]; then
        # Check if client submodule exists in .gitmodules before setting branch
        if git config -f .gitmodules --get-regexp "submodule\.client\." >/dev/null 2>&1; then
            # Use git submodule set-branch if available, suppress stderr to avoid usage output
            if ! git submodule set-branch --branch "$ODC_UI_BRANCH" client 2>/dev/null; then
                # Fallback: manually set the branch in .gitmodules if set-branch fails
                git config -f .gitmodules submodule.client.branch "$ODC_UI_BRANCH" >/dev/null 2>&1
            fi
        fi
    fi
    
    log_execute "Updating submodules to latest commits..."
    if git submodule update --init --remote >/dev/null 2>&1; then
        
        # Ensure client submodule is on the correct branch (not detached HEAD)
        if [ -n "$ODC_UI_BRANCH" ]; then
            # Check if client submodule exists (git submodule can have .git as file or directory)
            if [ -d "client" ] && ([ -d "client/.git" ] || [ -f "client/.git" ]); then
                pushd client >/dev/null || {
                    log_warn "Failed to enter client submodule directory"
                    popd >/dev/null
                    return 0
                }
                
                # Fetch the target branch from remote
                if ! git fetch origin "$ODC_UI_BRANCH" 2>/dev/null; then
                    if ! git fetch origin 2>/dev/null; then
                        log_error "Failed to fetch from remote"
                        popd >/dev/null
                        popd >/dev/null
                        return 1
                    fi
                fi
                
                # Check if remote branch exists
                if git show-ref --verify --quiet "refs/remotes/origin/$ODC_UI_BRANCH"; then
                    # Try to checkout the branch, create local branch if it doesn't exist
                    if ! git checkout "$ODC_UI_BRANCH" 2>/dev/null; then
                        if ! git checkout -b "$ODC_UI_BRANCH" "origin/$ODC_UI_BRANCH" >/dev/null 2>&1; then
                            log_error "Failed to checkout branch $ODC_UI_BRANCH"
                            popd >/dev/null
                            popd >/dev/null
                            return 1
                        fi
                    else
                        # Branch exists, ensure it tracks the remote branch
                        git branch --set-upstream-to="origin/$ODC_UI_BRANCH" "$ODC_UI_BRANCH" 2>/dev/null || true
                    fi
                    
                    # Reset to the latest commit on the remote branch
                    if ! git reset --hard "origin/$ODC_UI_BRANCH" >/dev/null 2>&1; then
                        log_error "Failed to reset to origin/$ODC_UI_BRANCH"
                        popd >/dev/null
                        popd >/dev/null
                        return 1
                    fi
                    
                    local current_commit=$(git rev-parse HEAD)
                    log_info "Client submodule on branch $ODC_UI_BRANCH (commit: ${current_commit:0:8})"
                fi
                
                popd >/dev/null
            fi
        fi
        
        popd >/dev/null
        return 0
    else
        log_error "Submodule update failed."
        popd >/dev/null
        return 1
    fi
}
