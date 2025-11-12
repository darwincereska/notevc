# ![logo.png](./images/png/NoteVC_50×50.png) NoteVC: Version Control for Markdown

Block-level version control for markdown files. Track changes at the heading level, not just file level.

## Features

- **Block-level tracking**: Version control at heading granularity
- **Frontmatter support**: Control versioning with YAML frontmatter (tags, title, enabled flag)
- **Smart commits**: Only commits changed blocks
- **Block restoration**: Restore individual sections without affecting the entire file
- **Rich diffs**: See exactly which sections changed

---

## Installation

Build from source:
```bash
gradle build
```

The executable will be in `build/install/notevc/bin/notevc`

---

## Global Options

These options work with any command and should be placed before the command name:

- `--no-color`: Disable colored output

**Examples:**
```bash
notevc --no-color status
notevc --no-color diff
notevc --no-color log --oneline
notevc --no-color show a1b2c3d4
```

**Alternative methods to disable colors:**
- Set `NO_COLOR=1` environment variable: `NO_COLOR=1 notevc status`
- Pipe output: `notevc status | less` (colors auto-disabled)
- CI environments automatically disable colors

---

## Command Reference

### Repository Management

#### `notevc init [path]`
Initialize a new notevc repository in the current or specified directory.

```bash
notevc init              # Initialize in current directory
notevc init ./notes      # Initialize in specific directory
```

#### `notevc status` or `notevc st`
Show the status of tracked files and which blocks have changed.

```bash
notevc status
```

#### `notevc commit [options] "message"`
Create a commit (snapshot) of changed files.

**Options:**
- `--file <file>`: Commit only a specific file

**Examples:**
```bash
notevc commit "Added new features"                    # Commit all changed files
notevc commit --file notes.md "Updated notes"         # Commit specific file
```

---

### Viewing History

#### `notevc log [options]`
Show commit history with details.

**Options:**
- `--max-count <n>` or `-n <n>`: Limit number of commits shown
- `--since <time>`: Show commits since specified time (e.g., "1h", "2d", "1w")
- `--oneline`: Show compact one-line format
- `--file` or `-f [file]`: Show file and block details for each commit

**Examples:**
```bash
notevc log                           # Show all commits
notevc log --max-count 5             # Show last 5 commits
notevc log --since 1d                # Show commits from last day
notevc log --oneline                 # Compact format
notevc log --file                    # Show with file details
notevc log --file notes.md           # Show commits affecting specific file
notevc log --file --oneline          # Compact format with file info
```

#### `notevc show <commit-hash> [options]`
Show detailed information about a specific commit, including block/file contents.

**Options:**
- `--file <file>`: Show changes only for specific file
- `--block <block-hash>` or `-b <block-hash>`: Show specific block content
- `--content` or `-c`: Show full file content at commit

**Examples:**
```bash
notevc show a1b2c3d4                          # Show commit details
notevc show a1b2c3d4 --file notes.md          # Show changes to specific file
notevc show a1b2c3d4 --file notes.md --content # Show full file content
notevc show a1b2c3d4 --file notes.md --block 1a2b3c # Show specific block
```

---

### Viewing Changes

#### `notevc diff [commit1] [commit2] [options]`
Show differences between commits or working directory with enhanced visual formatting.

**Options:**
- `--file <file>`: Show diff for specific file only
- `--block <block-hash>` or `-b <block-hash>`: Compare specific block only

**Examples:**
```bash
notevc diff                               # Show uncommitted changes (enhanced view)
notevc diff a1b2c3d4                      # Compare working dir to commit
notevc diff a1b2c3d4 b5c6d7e8             # Compare two commits
notevc diff --file notes.md               # Diff specific file
notevc diff a1b2c3d4 --file notes.md      # Compare specific file to commit
notevc diff --block 1a2b3c --file notes.md # Compare specific block
notevc diff a1b2c3d4 --block 1a2b3c --file notes.md # Compare block to commit
```

**Visual Enhancements:**
- Clear separators (+++/---/~~~) for ADDED/DELETED/MODIFIED blocks
- Line-by-line diffs showing exact changes (+/- prefixes)
- Context lines shown in gray for unchanged content
- Block-level comparison for targeted analysis

---

### Restoration

#### `notevc restore <commit-hash> [file] [options]`
Restore files or blocks from a specific commit.

**Options:**
- `--block <block-hash>` or `-b <block-hash>`: Restore specific block only



**Examples:**
```bash
notevc restore a1b2c3d4                          # Restore entire repository
notevc restore a1b2c3d4 notes.md                 # Restore specific file
notevc restore a1b2c3d4 notes.md --block 1a2b3c  # Restore specific block
```

---

### Other Commands

#### `notevc version` or `notevc -v`
Show the version of notevc.

```bash
notevc version
```

---

## Frontmatter Support

Control versioning behavior with YAML frontmatter:

```markdown
---
enabled: "true"      # Enable/disable tracking (default: true)
automatic: "true"    # Auto-commit on changes (default: false)
title: "My Note"     # Note title for sorting
tags:                # Tags for categorization
  - project
  - important
---

# Your content here
```

**Frontmatter fields:**
- `enabled`: Set to "false" to exclude file from commits (default: true)
- `automatic`: Enable automatic commits (default: false)
- `title`: Custom title for the note
- `tags`: List of tags (array or comma-separated string)

---

## How It Works

NoteVC splits markdown files into blocks based on headings. Each block is:
- Identified by a stable hash
- Tracked independently
- Versioned separately
- **Compressed automatically** (GZIP) when content > 100 bytes

This means you can:
- See which sections changed
- Restore individual sections
- Track content at a granular level
- **Save disk space** with automatic compression

### Storage Optimization

NoteVC includes built-in compression for efficient storage:
- **Automatic GZIP compression** for block content over 100 bytes
- Typical compression ratio: 60-80% space savings for text
- Transparent decompression when reading
- No performance impact on small blocks


