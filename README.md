# NoteVC: Version Control for Markdown
# Repository management
notevc init [path]                    # Initialize notevc repo
notevc status                         # Show changed files
notevc commit "message"               # Create snapshot
notevc log [--since=time]            # Show commit history

# Viewing changes
notevc diff [file]                    # Show changes since last commit
notevc diff HEAD~1 [file]            # Compare with previous commit
notevc show <commit-hash>             # Show specific commit

# Restoration
notevc restore <commit-hash> [file]   # Restore to specific version
notevc checkout <commit-hash>         # Restore entire repo state

# Utilities
notevc clean                          # Remove old snapshots
notevc gc                            # Garbage collect unused objects
notevc config                        # Show/set configuration

