package org.notevc.commands

import org.notevc.core.*
import org.notevc.utils.ColorUtils
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.*
import org.kargs.*

class RestoreCommand : Subcommand("restore", description = "Restore files or blocks from a specific commit") {
    val blockHash by Option(ArgType.String, longName = "block", shortName = "b", description = "Restore specific block only")
    val commitHash by Argument(ArgType.String, name = "commit-hash", description = "Commit to restore from", required = true)
    val targetFile by Argument(ArgType.writableFile(), name = "file", description = "Specific file to restore to", required = false)

    override fun execute() {
        val result: Result<String> = runCatching {
            val options = RestoreOptions(commitHash!!, blockHash, targetFile?.toString())

            val repo = Repository.find() ?: throw Exception("Not in a notevc repository. Run `notevc init` first.")

            when {
                options.blockHash != null && options.targetFile != null -> {
                    restoreSpecificBlock(repo, options.blockHash, options.blockHash, options.targetFile)
                }

                options.targetFile != null -> {
                    restoreSpecificFile(repo, options.commitHash, options.targetFile)
                }

                else -> {
                    restoreEntireRepository(repo, options.commitHash)
                }
            }
        }

        result.onSuccess { message -> println(message) }
        result.onFailure { error -> println("${ColorUtils.error("Error:")} ${error.message}") }
    }

    private fun restoreSpecificBlock(repo: Repository, commitHash: String, blockHash: String, targetFile: String): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))
        val blockParser = BlockParser()

        // Find the commit
        val commit = findCommit(repo, commitHash)
        ?: throw Exception("Commit ${ColorUtils.hash(commitHash)} not found")

        // Find the block snapshot for this file at the commit time
        val commitTime = Instant.parse(commit.timestamp)
        val snapshot = blockStore.getBlocksAtTime(targetFile, commitTime)
        ?: throw Exception("No snapshot found for ${ColorUtils.filename(targetFile)} at commit ${ColorUtils.hash(commitHash)}")

        // Find the specific block
        val targetBlock = snapshot.find { it.id.startsWith(blockHash) }
        ?: throw Exception("Block ${ColorUtils.hash(blockHash)} not found in ${ColorUtils.filename(targetFile)} at commit ${ColorUtils.hash(commitHash)}")

        // Read current file
        val filePath = repo.path.resolve(targetFile)
        if (!filePath.exists()) {
            throw Exception("File ${ColorUtils.filename(targetFile)} does not exist")
        }

        val currentContent = Files.readString(filePath)
        val currentParsedFile = blockParser.parseFile(currentContent, targetFile)

        // Find the block to replace in current file
        val currentBlockIndex = currentParsedFile.blocks.indexOfFirst { it.id.startsWith(blockHash) }
        if (currentBlockIndex == -1) {
            throw Exception("Block ${ColorUtils.hash(blockHash)} not found in current ${ColorUtils.filename(targetFile)}")
        }

        // Replace the block
        val updatedBlocks = currentParsedFile.blocks.toMutableList()
        updatedBlocks[currentBlockIndex] = targetBlock

        val updatedParsedFile = currentParsedFile.copy(blocks = updatedBlocks)
        val restoredContent = blockParser.reconstructFile(updatedParsedFile)

        // Write the updated file
        Files.writeString(filePath, restoredContent)

        val blockHeading = targetBlock.heading.replace(Regex("^#+\\s*"), "").trim()
        return "${ColorUtils.success("Restored block")} ${ColorUtils.hash(blockHash.take(8))} ${ColorUtils.heading("\"$blockHeading\"")} in ${ColorUtils.filename(targetFile)} from commit ${ColorUtils.hash(commitHash)}"
    }

    private fun restoreSpecificFile(repo: Repository, commitHash: String, targetFile: String): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))
        val blockParser = BlockParser()

        // Find the commit
        val commit = findCommit(repo, commitHash)
        ?: throw Exception("Commit ${ColorUtils.hash(commitHash)} not found")

        // Find the block snapshot for this file at the commit time
        val commitTime = Instant.parse(commit.timestamp)
        val snapshot = blockStore.getLatestBlockSnapshotBefore(targetFile, commitTime)
        ?: throw Exception("No snapshot found for ${ColorUtils.filename(targetFile)} at commit ${ColorUtils.hash(commitHash)}")

        val blocks = blockStore.getBlocksAtTime(targetFile, commitTime)
        ?: throw Exception("No blocks found for ${ColorUtils.filename(targetFile)} at commit ${ColorUtils.hash(commitHash)}")

        // Reconstruct the file from blocks with frontmatter from snapshot
        val parsedFile = ParsedFile(
            path = targetFile,
            frontMatter = snapshot.frontMatter,
            blocks = blocks
        )

        val restoredContent = blockParser.reconstructFile(parsedFile)

        // Write the restored file
        val filePath = repo.path.resolve(targetFile)
        Files.createDirectories(filePath.parent)
        Files.writeString(filePath, restoredContent)

        return "${ColorUtils.success("Restored file")} ${ColorUtils.filename(targetFile)} ${ColorUtils.dim("(${blocks.size} blocks)")} from commit ${ColorUtils.hash(commitHash)}"
    }

    private fun restoreEntireRepository(repo: Repository, commitHash: String): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))
        val blockParser = BlockParser()

        // Find the commit
        val commit = findCommit(repo, commitHash)
        ?: throw Exception("Commit ${ColorUtils.hash(commitHash)} not found")

        val commitTime = Instant.parse(commit.timestamp)

        // Get all files that were tracked at this commit
        val trackedFiles = getTrackedFilesAtCommit(repo, commitTime)

        if (trackedFiles.isEmpty()) {
            throw Exception("No files found at commit ${ColorUtils.hash(commitHash)}")
        }

        var restoredFiles = 0
        var totalBlocks = 0

        trackedFiles.forEach { filePath ->
            val snapshot = blockStore.getLatestBlockSnapshotBefore(filePath, commitTime)
            val blocks = blockStore.getBlocksAtTime(filePath, commitTime)
            if (blocks != null) {
                val parsedFile = ParsedFile(
                    path = filePath,
                    frontMatter = snapshot?.frontMatter,
                    blocks = blocks
                )

                val restoredContent = blockParser.reconstructFile(parsedFile)
                val fullPath = repo.path.resolve(filePath)

                Files.createDirectories(fullPath.parent)
                Files.writeString(fullPath, restoredContent)

                restoredFiles++
                totalBlocks += blocks.size
            }
        }

        return buildString {
            appendLine("${ColorUtils.success("Restored repository")} to commit ${ColorUtils.hash(commitHash)}")
            appendLine("${ColorUtils.bold("Files restored:")} $restoredFiles")
            appendLine("${ColorUtils.bold("Total blocks:")} $totalBlocks")
            appendLine("${ColorUtils.bold("Commit message:")} ${commit.message}")
        }
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
}

data class RestoreOptions(
    val commitHash: String,
    val blockHash: String?,
    val targetFile: String?
)

