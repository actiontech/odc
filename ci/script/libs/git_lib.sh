#!/usr/bin/env bash
# Git repository management library for main repository operations

function switch_backend_branch() {
    # Switch the main repository (backend) to the specified branch
    # Uses ODC_SERVER_BRANCH environment variable, defaults to test/build_rpm
    local target_branch="${ODC_SERVER_BRANCH:-test/build_rpm}"
    
    # Get project root
    local project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
    pushd "$project_root" >/dev/null || return 1
    
    # Check if we're in a git repository
    if [ ! -d ".git" ] && [ ! -f ".git" ]; then
        log_warn "Not in a git repository, skipping branch switch"
        popd >/dev/null
        return 0
    fi
    
    # Get current branch
    local current_branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "unknown")
    
    # If already on the target branch, verify it's up to date
    if [ "$current_branch" = "$target_branch" ]; then
        log_info "Current branch: $target_branch, checking for updates..."
        
        # Fetch latest changes from remote
        if git fetch origin "$target_branch" 2>/dev/null || git fetch origin 2>/dev/null; then
            # Check if local branch is behind remote
            local local_commit=$(git rev-parse HEAD 2>/dev/null)
            local remote_commit=$(git rev-parse "origin/$target_branch" 2>/dev/null)
            
            if [ -n "$remote_commit" ] && [ "$local_commit" != "$remote_commit" ]; then
                log_info "Updating to latest commit (${remote_commit:0:8})..."
                if ! git reset --hard "origin/$target_branch" >/dev/null 2>&1; then
                    log_error "Failed to reset to origin/$target_branch"
                    popd >/dev/null
                    return 1
                fi
            fi
        else
            log_warn "Failed to fetch from remote, continuing with current state"
        fi
        
        popd >/dev/null
        return 0
    fi
    
    log_execute "Switching from $current_branch to $target_branch..."
    
    # Fetch the target branch from remote
    if ! git fetch origin "$target_branch" 2>/dev/null; then
        log_warn "Failed to fetch branch $target_branch, trying to fetch all..."
        if ! git fetch origin 2>/dev/null; then
            log_error "Failed to fetch from remote"
            popd >/dev/null
            return 1
        fi
    fi
    
    # Check if remote branch exists
    if git show-ref --verify --quiet "refs/remotes/origin/$target_branch"; then
        # Try to checkout the branch, create local branch if it doesn't exist
        if ! git checkout "$target_branch" 2>/dev/null; then
            log_info "Creating local branch tracking origin/$target_branch..."
            if ! git checkout -b "$target_branch" "origin/$target_branch" >/dev/null 2>&1; then
                log_error "Failed to checkout branch $target_branch"
                popd >/dev/null
                return 1
            fi
        else
            # Branch exists, ensure it tracks the remote branch
            git branch --set-upstream-to="origin/$target_branch" "$target_branch" 2>/dev/null || true
        fi
        
        # Reset to the latest commit on the remote branch
        if ! git reset --hard "origin/$target_branch" >/dev/null 2>&1; then
            log_error "Failed to reset to origin/$target_branch"
            popd >/dev/null
            return 1
        fi
        
        local final_commit=$(git rev-parse HEAD)
        log_info "Switched to $target_branch (commit: ${final_commit:0:8})"
    else
        log_warn "Remote branch origin/$target_branch does not exist, keeping current branch: $current_branch"
    fi
    
    popd >/dev/null
    return 0
}
