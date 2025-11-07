# ![logo.png](./images/png/Color40X50.png) NoteVC: Version Control for Markdown


# Repository management

Initialize notevc repo:
```bash
notevc init [path]
```

Show changed files:
```bash
notevc status
```

Create snapshot:
```bash
notevc commit [--file <file>] "message"
```

Show commit history:
```bash
notevc log [--since=time]
```

# Viewing changes

Show changes since last commit:
```bash
notevc diff [file]
```

Compare with previous commit:
```bash
notevc diff HEAD~1 [file]
```

Show specific commit:
```bash
notevc show <commit-hash>
```

# Restoration

Restore to specific version:
```bash
notevc restore <commit-hash> [file]
```

Restore to specific block:
```bash
notevc restore --block <commit-hash> [file]
```

Restore entire repo state:
```bash
notevc checkout <commit-hash>
```

# Utilities

Remove old snapshots:
```bash
notevc clean
```

Garbage collect unused objects:
```bash
notevc gc
```

Show/set configuration:
```bash
notevc config
```
