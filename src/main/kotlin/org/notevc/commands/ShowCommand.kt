package org.notevc.commands

import org.notevc.core.*
import org.notevc.utils.ColorUtils
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.*
import org.kargs.*

class ShowCommand : Subcommand("show", description = "Show detailed information about a specific commit", aliases = listOf("sh")) {
    val commitHash by Argument(ArgType.String, "commit-hash", description = "Commit to show", required = true)
    val targetFile by Option(ArgType.readableFile(), longName = "file", shortName = "f", description = "Show changes only for specific file")
    val blockHash by Option(ArgType.String, longName = "block", shortName = "b", description = "Show specific block content")
    val showContent by Flag(longName = "content", shortName = "c", description = "Show full file content at commit")

    override fun execute() {
        val result: Result<String> = runCatching {
            val repo = Repository.find() ?: throw Exception("Not in a notevc repository. Run `notevc init` first.")

            val options = ShowOptions(commitHash!!, targetFile?.toString(), blockHash, showContent ?: false)

            when {
                options.blockHash != null -> showBlock(repo, options)
                options.showContent -> showFileContent(repo, options)
                else -> showCommit(repo, options.commitHash, options.targetFile)
            }
        }

        result.onSuccess { message -> println(message) }
        result.onFailure { error -> println("${ColorUtils.error("Error:")} ${error.message}") }
    }

    private fun showCommit(repo: Repository, commitHash: String, targetFile: String?): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))

        // Find the commit
        val commit = findCommit(repo, commitHash)
            ?: throw Exception("Commit ${ColorUtils.hash(commitHash)} not found")

        val commitTime = Instant.parse(commit.timestamp)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())

        val result = StringBuilder()

        // Show commit header
        result.appendLine("${ColorUtils.bold("Commit:")} ${ColorUtils.hash(commit.hash)}")
        result.appendLine("${ColorUtils.bold("Author:")} ${commit.author}")
        result.appendLine("${ColorUtils.bold("Date:")} ${formatter.format(commitTime)}")
        if (commit.parent != null) {
            result.appendLine("${ColorUtils.bold("Parent:")} ${ColorUtils.hash(commit.parent)}")
        }
        result.appendLine()
        result.appendLine("    ${commit.message}")
        result.appendLine()

        // Get files at this commit
        val filesToShow = if (targetFile != null) {
            listOf(targetFile)
        } else {
            getTrackedFilesAtCommit(repo, commitTime)
        }

        if (filesToShow.isEmpty()) {
            result.appendLine("${ColorUtils.dim("No files found at this commit")}")
            return result.toString()
        }

        // Show changes for each file
        result.appendLine("${ColorUtils.bold("Changes:")}")
        result.appendLine()

        var totalAdded = 0
        var totalModified = 0
        var totalDeleted = 0

        filesToShow.forEach { filePath ->
            val currentSnapshot = blockStore.getLatestBlockSnapshotBefore(filePath, commitTime)
            
            if (currentSnapshot != null) {
                // Find parent commit to compare against
                val parentSnapshot = if (commit.parent != null) {
                    val parentCommit = findCommit(repo, commit.parent)
                    if (parentCommit != null) {
                        val parentTime = Instant.parse(parentCommit.timestamp)
                        blockStore.getLatestBlockSnapshotBefore(filePath, parentTime)
                    } else null
                } else null

                val changes = blockStore.compareBlocks(parentSnapshot, currentSnapshot)

                if (changes.isNotEmpty()) {
                    result.appendLine("${ColorUtils.filename(filePath)}:")
                    
                    val added = changes.count { it.type == BlockChangeType.ADDED }
                    val modified = changes.count { it.type == BlockChangeType.MODIFIED }
                    val deleted = changes.count { it.type == BlockChangeType.DELETED }

                    totalAdded += added
                    totalModified += modified
                    totalDeleted += deleted

                    if (added > 0) result.appendLine("  ${ColorUtils.success("+")} $added ${if (added == 1) "block" else "blocks"} added")
                    if (modified > 0) result.appendLine("  ${ColorUtils.warning("~")} $modified ${if (modified == 1) "block" else "blocks"} modified")
                    if (deleted > 0) result.appendLine("  ${ColorUtils.error("-")} $deleted ${if (deleted == 1) "block" else "blocks"} deleted")
                    
                    result.appendLine()

                    // Show detailed changes
                    changes.sortedBy { it.blockId }.forEach { change ->
                        val headingText = change.heading.replace(Regex("^#+\\s*"), "").trim()
                        val blockId = change.blockId.take(8)

                        when (change.type) {
                            BlockChangeType.ADDED -> {
                                result.appendLine("    ${ColorUtils.success("+")} ${ColorUtils.heading(headingText)} ${ColorUtils.dim("[$blockId]")}")
                            }
                            BlockChangeType.DELETED -> {
                                result.appendLine("    ${ColorUtils.error("-")} ${ColorUtils.heading(headingText)} ${ColorUtils.dim("[$blockId]")}")
                            }
                            BlockChangeType.MODIFIED -> {
                                result.appendLine("    ${ColorUtils.warning("~")} ${ColorUtils.heading(headingText)} ${ColorUtils.dim("[$blockId]")}")
                            }
                        }
                    }
                    result.appendLine()
                }
            }
        }

        // Summary
        if (totalAdded + totalModified + totalDeleted > 0) {
            result.appendLine("${ColorUtils.bold("Summary:")}")
            result.appendLine("  ${ColorUtils.success("+")} $totalAdded added, ${ColorUtils.warning("~")} $totalModified modified, ${ColorUtils.error("-")} $totalDeleted deleted")
        }

        return result.toString()
    }

    private fun findCommit(repo: Repository, commitHash: String): CommitEntry? {
        val timelineFile = repo.path.resolve("${Repository.NOTEVC_DIR}/timeline.json")

        if (!timelineFile.exists()) {
            return null
        }

        val content = Files.readString(timelineFile)
        if (content.trim() == "[]") {
            return null
        }

        val commits = Json.decodeFromString<List<CommitEntry>>(content)
        return commits.find { it.hash.startsWith(commitHash) }
    }

    private fun getTrackedFilesAtCommit(repo: Repository, commitTime: Instant): List<String> {
        val blocksDir = repo.path.resolve("${Repository.NOTEVC_DIR}/blocks")
        if (!blocksDir.exists()) return emptyList()

        val files = mutableSetOf<String>()
        val json = Json { ignoreUnknownKeys = true }
        val timeRange = 60L // seconds

        Files.walk(blocksDir)
            .filter { it.isRegularFile() && it.fileName.toString().startsWith("blocks-") }
            .forEach { snapshotFile ->
                try {
                    val content = Files.readString(snapshotFile)
                    val snapshot = json.decodeFromString<BlockSnapshot>(content)
                    val snapshotTime = Instant.parse(snapshot.timestamp)

                    val timeDiff = kotlin.math.abs(commitTime.epochSecond - snapshotTime.epochSecond)
                    if (timeDiff <= timeRange) {
                        files.add(snapshot.filePath)
                    }
                } catch (e: Exception) {
                    // Skip corrupted snapshots
                }
            }

        return files.toList()
    }

    private fun showBlock(repo: Repository, options: ShowOptions): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))

        val commit = findCommit(repo, options.commitHash)
            ?: throw Exception("Commit ${ColorUtils.hash(options.commitHash)} not found")

        val commitTime = Instant.parse(commit.timestamp)
        
        if (options.targetFile == null) {
            throw Exception("--file is required when showing a specific block")
        }

        val snapshot = blockStore.getLatestBlockSnapshotBefore(options.targetFile, commitTime)
            ?: throw Exception("No snapshot found for ${options.targetFile} at commit ${options.commitHash}")

        val block = snapshot.blocks.find { it.id.startsWith(options.blockHash!!) }
            ?: throw Exception("Block ${ColorUtils.hash(options.blockHash!!)} not found")

        val content = objectStore.getContent(block.contentHash)
            ?: throw Exception("Content not found for block")

        val headingText = block.heading.replace(Regex("^#+\\s*"), "").trim()
        val result = StringBuilder()

        result.appendLine("${ColorUtils.bold("Block:")} ${ColorUtils.hash(block.id.take(8))}")
        result.appendLine("${ColorUtils.bold("Heading:")} ${ColorUtils.heading(headingText)}")
        result.appendLine("${ColorUtils.bold("File:")} ${ColorUtils.filename(options.targetFile)}")
        result.appendLine("${ColorUtils.bold("Commit:")} ${ColorUtils.hash(commit.hash)}")
        result.appendLine()
        result.appendLine("${ColorUtils.dim("─".repeat(70))}")
        result.appendLine()
        result.append(content)

        return result.toString()
    }

    private fun showFileContent(repo: Repository, options: ShowOptions): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))
        val blockParser = BlockParser()

        val commit = findCommit(repo, options.commitHash)
            ?: throw Exception("Commit ${ColorUtils.hash(options.commitHash)} not found")

        val commitTime = Instant.parse(commit.timestamp)

        if (options.targetFile == null) {
            throw Exception("--file is required when showing file content")
        }

        val snapshot = blockStore.getLatestBlockSnapshotBefore(options.targetFile, commitTime)
            ?: throw Exception("No snapshot found for ${options.targetFile} at commit ${options.commitHash}")

        val blocks = snapshot.blocks.map { blockState ->
            val content = objectStore.getContent(blockState.contentHash)
                ?: throw IllegalStateException("Missing content for block ${blockState.id}")

            Block(
                id = blockState.id,
                heading = blockState.heading,
                content = content,
                type = blockState.type,
                order = blockState.order
            )
        }

        val parsedFile = ParsedFile(
            path = options.targetFile,
            frontMatter = snapshot.frontMatter,
            blocks = blocks
        )

        val reconstructedContent = blockParser.reconstructFile(parsedFile)

        val result = StringBuilder()
        result.appendLine("${ColorUtils.bold("File:")} ${ColorUtils.filename(options.targetFile)}")
        result.appendLine("${ColorUtils.bold("Commit:")} ${ColorUtils.hash(commit.hash)}")
        result.appendLine("${ColorUtils.bold("Blocks:")} ${blocks.size}")
        result.appendLine()
        result.appendLine("${ColorUtils.dim("─".repeat(70))}")
        result.appendLine()
        result.append(reconstructedContent)

        return result.toString()
    }
}

data class ShowOptions(
    val commitHash: String,
    val targetFile: String?,
    val blockHash: String? = null,
    val showContent: Boolean = false
)
