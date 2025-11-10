# Project Setup

- [x] Initialize Project
- [x] Configure `build.gradle.kts` with dependencies
- [x] Set up testing framework


# Core

- [x] Create `Repository.kt` class
- [x] Implement `.notevc` directory initialization
- [x] Create `ObjectStore.kt` for content storage 
- [x] Implement content hashing `HashUtils.kt`
- [x] Create `NoteSnapshot` data class
- [x] Implement `Timeline.kt` for version tracking
- [x] Add `RepoMetadata` and configuration


# File Operations

- [x] Implement markdown file scanning
- [x] Create file change detection logic
- [x] Add file content reading/writing utilities
- [x] Implement path resolution and validation
- [x] Add file timestamp tracking
- [ ] Create backup and restore mechanisms


# Core Commands


## Init Command

- [x] `notevc init` - Initialize repository
- [x] Create `.notevc` directory structure
- [x] Generate initial metadata file
- [x] Handle existing repository detection


## Status Command

- [x] `notevc status` - Show file changes
- [x] Compare current files with last snapshot
- [x] Display added/modified/deleted files
- [x] Show clean working directory message


## Commit command

- [x] `notevc commit "message"` - Create snapshot
- [x] Validate commit message exists
- [x] Store changed file contents
- [x] Create snapshot with metadata
- [x] Update repository head pointer


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


