package org.notevc.commands

import org.notevc.core.*
import org.notevc.utils.FileUtils
import org.notevc.utils.HashUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.*
import org.kargs.*

class CommitCommand : Subcommand("commit", description = "Create a commit of changed files") {
    val targetFile by Option(ArgType.readableFile(), longName = "file", shortName = "f", description = "Commit only a specific file")
    val message by Argument(ArgType.String, name = "message", description = "Message for commit", required = true)

    override fun execute() {
        val result: Result<String> = runCatching {
            val repo = Repository.find() ?: throw Exception("Not in a notevc repository. Run `notevc init` first.")

            if (targetFile != null) {
                createSingleFileCommit(repo, targetFile.toString(), message!!)
            } else {
                createChangedFilesCommit(repo, message!!)
            }
        }

        result.onSuccess { message -> println(message) }
        result.onFailure { error -> println("${Colors.error("Error:")} ${error.message}") }
    }
    
    private fun createSingleFileCommit(repo: Repository, targetFile: String, message: String): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))
        val blockParser = BlockParser()
        val timestamp = Instant.now()
        
        // Resolve the target file path
        val filePath = repo.path.resolve(targetFile)
        if (!filePath.exists()) {
            throw Exception("File not found: $targetFile")
        }
        
        if (!targetFile.toString().endsWith(".md")) {
            throw Exception("Only markdown files (.md) are supported")
        }
        
        val relativePath = repo.path.relativize(filePath).toString()
        val content = Files.readString(filePath)
        val parsedFile = blockParser.parseFile(content, relativePath)
        
        // Check if file is disabled
        if (parsedFile.frontMatter?.isEnabled == false) {
            throw Exception("File $targetFile is disabled (enabled: false in front matter)")
        }
        
        // Check if file actually changed
        val latestSnapshot = blockStore.getLatestBlockSnapshot(relativePath)
        if (latestSnapshot != null) {
            val currentSnapshot = createCurrentSnapshot(parsedFile, objectStore, timestamp)
            val changes = blockStore.compareBlocks(latestSnapshot, currentSnapshot)
            
            if (changes.isEmpty()) {
                return "No changes detected in $targetFile"
            }
        }
        
        // Store blocks for this file
        val snapshot = blockStore.storeBlocks(parsedFile, timestamp)
        val commitHash = HashUtils.sha256("$timestamp:$message:$relativePath").take(8)
        
        // Update repository metadata
        updateRepositoryHead(repo, commitHash, timestamp, message)
        
        return buildString {
            appendLine("${Colors.success("Created commit")} ${Colors.yellow(commitHash)}")
            appendLine("${Colors.bold("Message:")} $message")
            appendLine("${Colors.bold("File:")} ${Colors.filename(relativePath)} ${Colors.dim("(${snapshot.blocks.size} blocks)")}")
        }
    }
    
    private fun createChangedFilesCommit(repo: Repository, message: String): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))
        val blockParser = BlockParser()
        val timestamp = Instant.now()
        
        // Find all markdown files
        val markdownFiles = FileUtils.findMarkdownFiles(repo.path)
        
        if (markdownFiles.isEmpty()) {
            throw Exception("No markdown files found to commit")
        }
        
        val changedFiles = mutableListOf<String>()
        var totalBlocksStored = 0
        
        // Process each file and check for changes
        markdownFiles.forEach { filePath ->
            val relativePath = repo.path.relativize(filePath).toString()
            val content = Files.readString(filePath)
            val parsedFile = blockParser.parseFile(content, relativePath)
            
            // Skip files with front matter disabled
            if (parsedFile.frontMatter?.isEnabled == false) {
                return@forEach
            }
            
            // Check if file changed
            val latestSnapshot = blockStore.getLatestBlockSnapshot(relativePath)
            val hasChanges = if (latestSnapshot != null) {
                val currentSnapshot = createCurrentSnapshot(parsedFile, objectStore, timestamp)
                val changes = blockStore.compareBlocks(latestSnapshot, currentSnapshot)
                changes.isNotEmpty()
            } else {
                // New file - always has changes
                true
            }
            
            if (hasChanges) {
                // Store blocks for this file
                val snapshot = blockStore.storeBlocks(parsedFile, timestamp)
                changedFiles.add("$relativePath (${snapshot.blocks.size} blocks)")
                totalBlocksStored += snapshot.blocks.size
            }
        }
        
        if (changedFiles.isEmpty()) {
            return "No changes detected - working directory clean"
        }
        
        // Create commit hash from timestamp and message
        val commitHash = HashUtils.sha256("$timestamp:$message").take(8)
        
        // Update repository metadata
        updateRepositoryHead(repo, commitHash, timestamp, message)
        
        return buildString {
            appendLine("${Colors.success("Created commit")} ${Colors.yellow(commitHash)}")
            appendLine("${Colors.bold("Message:")} $message")
            appendLine("${Colors.bold("Files committed:")} ${changedFiles.size}")
            appendLine("${Colors.bold("Total blocks:")} $totalBlocksStored")
            appendLine()
            changedFiles.forEach { fileInfo ->
                val parts = fileInfo.split(" (")
                val filename = parts[0]
                val blockInfo = if (parts.size > 1) " (${parts[1]}" else ""
                appendLine("  ${Colors.filename(filename)}${Colors.dim(blockInfo)}")
            }
        }
    }
    
    private fun createCurrentSnapshot(parsedFile: ParsedFile, objectStore: ObjectStore, timestamp: Instant): BlockSnapshot {
        return BlockSnapshot(
            filePath = parsedFile.path,
            timestamp = timestamp.toString(),
            blocks = parsedFile.blocks.map { block ->
                BlockState(
                    id = block.id,
                    heading = block.heading,
                    contentHash = objectStore.storeContent(block.content),
                    type = block.type,
                    order = block.order
                )
            },
            frontMatter = parsedFile.frontMatter
        )
    }
    
    private fun updateRepositoryHead(repo: Repository, commitHash: String, timestamp: Instant, message: String) {
        val metadataFile = repo.path.resolve("${Repository.NOTEVC_DIR}/metadata.json")
        val timelineFile = repo.path.resolve("${Repository.NOTEVC_DIR}/timeline.json")
        
        // Read current timeline
        val currentCommits = if (timelineFile.exists()) {
            val content = Files.readString(timelineFile)
            if (content.trim() == "[]") {
                emptyList()
            } else {
                Json.decodeFromString<List<CommitEntry>>(content)
            }
        } else {
            emptyList()
        }

        // Create new commit entry
        val newCommit = CommitEntry(
            hash = commitHash,
            message = message,
            timestamp = timestamp.toString(),
            author = System.getProperty("user.name"),
            parent = currentCommits.firstOrNull()?.hash // Previous commit
        )

        val updatedCommits = listOf(newCommit) + currentCommits

        // Save timeline
        val json = Json { prettyPrint = true }
        Files.writeString(timelineFile, json.encodeToString(updatedCommits))

        // Read current metadata
        val currentMetadata = if (metadataFile.exists()) {
            val content = Files.readString(metadataFile)
            Json.decodeFromString<RepoMetadata>(content)
        } else {
            RepoMetadata(
                version = Repository.VERSION,
                created = timestamp.toString(),
                head = null
            )
        }
        
        // Update with new commit
        val updatedMetadata = currentMetadata.copy(
            head = commitHash,
            lastCommit = CommitInfo(
                hash = commitHash,
                message = message,
                timestamp = timestamp.toString(),
                author = System.getProperty("user.name")
            )
        )
        
        // Save updated metadata
        Files.writeString(metadataFile, json.encodeToString(updatedMetadata))
    }
}
