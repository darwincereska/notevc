package org.notevc.commands

import org.notevc.core.*
import org.notevc.utils.FileUtils
import org.notevc.utils.HashUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.time.Instant
import org.notevc.utils.ColorUtils
import kotlin.io.path.*

class CommitCommand {
    
    fun execute(args: List<String>): Result<String> {
        return try {
            val (targetFile, message) = parseArgs(args)
            
            if (message.isBlank()) {
                return Result.failure(Exception("Commit message cannot be empty"))
            }
            
            val repo = Repository.find()
                ?: return Result.failure(Exception("Not in a notevc repository. Run 'notevc init' first."))
            
            val commitResult = if (targetFile != null) {
                createSingleFileCommit(repo, targetFile, message)
            } else {
                createChangedFilesCommit(repo, message)
            }
            
            Result.success(commitResult)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun parseArgs(args: List<String>): Pair<String?, String> {
        if (args.isEmpty()) {
            return null to ""
        }
        
        // Check for --file flag
        val fileIndex = args.indexOf("--file")
        if (fileIndex != -1 && fileIndex + 1 < args.size) {
            val targetFile = args[fileIndex + 1]
            val messageArgs = args.filterIndexed { index, _ -> 
                index != fileIndex && index != fileIndex + 1 
            }
            return targetFile to messageArgs.joinToString(" ")
        }
        
        // No --file flag, all args are the message
        return null to args.joinToString(" ")
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
        
        if (!targetFile.endsWith(".md")) {
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
            appendLine("${ColorUtils.success("Created commit")} ${ColorUtils.hash(commitHash)}")
            appendLine("${ColorUtils.bold("Message:")} $message")
            appendLine("${ColorUtils.bold("File:")} ${ColorUtils.filename(relativePath)} ${ColorUtils.dim("(${snapshot.blocks.size} blocks)")}")
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
            appendLine("${ColorUtils.success("Created commit")} ${ColorUtils.hash(commitHash)}")
            appendLine("${ColorUtils.bold("Message:")} $message")
            appendLine("${ColorUtils.bold("Files committed:")} ${changedFiles.size}")
            appendLine("${ColorUtils.bold("Total blocks:")} $totalBlocksStored")
            appendLine()
            changedFiles.forEach { fileInfo ->
                val parts = fileInfo.split(" (")
                val filename = parts[0]
                val blockInfo = if (parts.size > 1) " (${parts[1]}" else ""
                appendLine("  ${ColorUtils.filename(filename)}${ColorUtils.dim(blockInfo)}")
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
