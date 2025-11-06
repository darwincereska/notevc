# Project Setup

- [x] Initialize Project
- [x] Configure `build.gradle.kts` with dependencies
- [ ] Set up testing framework

# Core

- [ ] Create `Repository.kt` class
- [ ] Implement `.notevc` directory initialization
- [ ] Create `ObjectStore.kt` for content storage 
- [ ] Implement content hashing `HashUtils.kt`
- [ ] Create `NoteSnapshot` data class
- [ ] Implement `Timeline.kt` for version tracking
- [ ] Add `RepoMetadata` and configuration

# File Operations

- [ ] Implement markdown file scanning
- [ ] Create file change detection logic
- [ ] Add file content reading/writing utilities
- [ ] Implement path resolution and validation
- [ ] Add file timestamp tracking
- [ ] Create backup and restore mechanisms

# Core Commands

## Init Command

- [ ] `notevc init` - Initialize repository
- [ ] Create `.notevc` directory structure
- [ ] Generate initial metadata file
- [ ] Handle existing repository detection

## Status Command

- [ ] `notevc status` - Show file changes
- [ ] Compare current files with last snapshot
- [ ] Display added/modified/deleted files
- [ ] Show clean working directory message

## Commit command

- [ ] `notevc commit "message"` - Create snapshot
- [ ] Validate commit message exists
- [ ] Store changed file contents
- [ ] Create snapshot with metadata
- [ ] Update repository head pointer

## Log Command

- [ ] `notevc log` - Show commit history
- [ ] Display snapshots in reverse chronological order
- [ ] Show commit hashes, messages, and timestamps
- [ ] add `--since` time filtering option

# Advanced Commands

## Diff Command

- [ ] `notevc diff` - Show current changes
- [ ] `notevc diff <file>` - Show changes for specific file
- [ ] `notevc diff <commit>` - Compare with specific commit
- [ ] Implement basic text diffing algorithm

## Restore Command

- [ ] `notevc restore <commit>` - Restore entire state
- [ ] `notevc restore <commit> <file>` - Restore specific file
- [ ] Add conformation prompts for destructive operations
- [ ] Handle file conflicts gracefully

## Show Command

- [ ] `notevc show <commit>` - Display commit changes
- [ ] Show commit metadata and file changes
- [ ] Display file contents at specific commit

# Utilities and Polish

- [ ] Add colored output for better UX
- [ ] Implement proper error handling messages
- [ ] Add input validation for all commands
- [ ] Create help system (`notevc --help`)
- [ ] Add version information (`notevc --version`)
- [ ] Implement configuration file support

# Testing

- [ ] Write unit tests for `ObjectStore`
- [ ] Test `Repository` initialization and operations
- [ ] Add integration tests for CLI commands
- [ ] Test file change detection logic
- [ ] Add edge case testing (empty repos, corrupted data)
- [ ] Performance testing with large note collections

# Build and Distribution

- [ ] Create fat JAR for distribution
- [ ] Add shell script wrapper for easy execution
- [ ] Test on different operating systems
- [ ] Create installation scripts
- [ ] Add build automation (GitHub Actions)

# Future Features

- [ ] Compression for stored content
- [ ] Garbage collection for unused objects
- [ ] Branch-like functionality for different contexts
- [ ] Automatic backup scheduling
- [ ] File watching for auto-commits
- [ ] Export/import functionality
- [ ] NeoVim Plugin
