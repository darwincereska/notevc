package org.notevc.commands

import org.notevc.core.*
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.*
import org.kargs.*

class DiffCommand : Subcommand("diff", description = "Show differences between commits or working directory") {
    val commitHash1 by Argument(ArgType.String, name = "commit1", description = "First commit", required = false)
    val commitHash2 by Argument(ArgType.String, name = "commit2", description = "Second commit", required = false)
    val targetFile by Option(ArgType.readableFile(), longName = "file", shortName = "f", description = "Show diff for specific file only") 
    val blockHash by Option(ArgType.String, longName = "block", shortName = "b", description = "Compare specific block only")

    override fun execute() {
        val result: Result<String> = runCatching {
            val repo = Repository.find() ?: throw Exception("Not in a notevc repository. Run `notevc init` first.")

            when {
                blockHash != null && commitHash1 != null -> {
                    // Compare specific block to commit
                    if (targetFile == null) {
                        throw Exception("--file is required when using --block option")
                    }
                    compareSpecificBlock(repo, commitHash1, blockHash!!, targetFile!!.toString())
                }

                blockHash != null -> {
                    // Compare specific block in working directory
                    if (targetFile == null) {
                        throw Exception("--file is required when using --block option")
                    }
                    compareSpecificBlock(repo, null, blockHash!!, targetFile!!.toString())
                }

                commitHash1 != null && commitHash2 != null -> {
                    // Compare two commits
                    compareCommits(repo, commitHash1!!, commitHash2!!, targetFile?.toString())
                }

                commitHash1 != null -> {
                    // Compare working directory to a commit
                    compareWorkingDirectoryToCommit(repo, commitHash1!!, targetFile?.toString())
                }

                else -> {
                    // Show changes in working directory (not committed)
                    showWorkingDirectoryChanges(repo, targetFile?.toString())
                }
            }
        }

        result.onSuccess { message -> println(message) }
        result.onFailure { error -> println("${Colors.error("Error:")} ${error.message}") }
    }

    private fun compareSpecificBlock(repo: Repository, commitHash: String?, blockHash: String, targetFile: String?): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))
        val blockParser = BlockParser()

        if (targetFile == null) {
            throw Exception("--file is required when comparing a specific block")
        }

        val result = StringBuilder()
        result.appendLine("${Colors.bold("Block comparison:")} ${Colors.yellow(blockHash.take(8))}")
        result.appendLine()

        // Get the commit snapshot if provided, otherwise use latest
        val commitSnapshot = if (commitHash != null) {
            val commit = findCommit(repo, commitHash)
                ?: throw Exception("Commit ${Colors.yellow(commitHash)} not found")
            val commitTime = Instant.parse(commit.timestamp)
            blockStore.getLatestBlockSnapshotBefore(targetFile, commitTime)
        } else {
            blockStore.getLatestBlockSnapshot(targetFile)
        }

        // Get current snapshot
        val filePath = repo.path.resolve(targetFile)
        if (!filePath.exists()) {
            throw Exception("File not found: $targetFile")
        }

        val content = Files.readString(filePath)
        val parsedFile = blockParser.parseFile(content, targetFile)
        val currentSnapshot = createCurrentSnapshot(parsedFile, objectStore)

        // Find the specific block in both snapshots
        val oldBlock = commitSnapshot?.blocks?.find { it.id.startsWith(blockHash) }
        val newBlock = currentSnapshot.blocks.find { it.id.startsWith(blockHash) }

        if (oldBlock == null && newBlock == null) {
            throw Exception("Block ${Colors.yellow(blockHash)} not found")
        }

        val headingText = (newBlock?.heading ?: oldBlock?.heading ?: "").replace(Regex("^#+\\s*"), "").trim()
        
        result.appendLine("${Colors.heading(headingText)} ${Colors.dim("[${blockHash.take(8)}]")}")
        result.appendLine(Colors.dim("─".repeat(70)))
        result.appendLine()

        when {
            oldBlock == null && newBlock != null -> {
                result.appendLine(Colors.green("This block was ADDED"))
                result.appendLine()
                val newContent = objectStore.getContent(newBlock.contentHash)
                if (newContent != null) {
                    newContent.lines().forEach { line ->
                        result.appendLine("${Colors.green("+ ")} $line")
                    }
                }
            }
            oldBlock != null && newBlock == null -> {
                result.appendLine(Colors.error("This block was DELETED"))
                result.appendLine()
                val oldContent = objectStore.getContent(oldBlock.contentHash)
                if (oldContent != null) {
                    oldContent.lines().forEach { line ->
                        result.appendLine("${Colors.error("- ")} $line")
                    }
                }
            }
            oldBlock != null && newBlock != null -> {
                if (oldBlock.contentHash == newBlock.contentHash) {
                    result.appendLine(Colors.dim("No changes"))
                } else {
                    result.appendLine(Colors.warn("Block was MODIFIED"))
                    result.appendLine()
                    val oldContent = objectStore.getContent(oldBlock.contentHash)
                    val newContent = objectStore.getContent(newBlock.contentHash)
                    
                    if (oldContent != null && newContent != null) {
                        val diff = computeDetailedDiff(oldContent, newContent)
                        diff.forEach { line ->
                            result.appendLine(line)
                        }
                    }
                }
            }
        }

        return result.toString()
    }

    private fun compareCommits(repo: Repository, hash1: String, hash2: String, targetFile: String?): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))

        // Find commits
        val commit1 = findCommit(repo, hash1)
            ?: throw Exception("Commit ${Colors.yellow(hash1)} not found")
        val commit2 = findCommit(repo, hash2)
            ?: throw Exception("Commit ${Colors.yellow(hash2)} not found")

        val time1 = Instant.parse(commit1.timestamp)
        val time2 = Instant.parse(commit2.timestamp)

        val filesToCompare = if (targetFile != null) {
            listOf(targetFile)
        } else {
            getTrackedFilesAtTime(repo, time1, time2)
        }

        if (filesToCompare.isEmpty()) {
            return "No files to compare"
        }

        val result = StringBuilder()
        result.appendLine(Colors.bold("Comparing commits:"))
        result.appendLine("  ${Colors.yellow(hash1.take(8))} ${Colors.dim(commit1.message)}")
        result.appendLine("  ${Colors.yellow(hash2.take(8))} ${Colors.dim(commit2.message)}")
        result.appendLine()

        var totalChanges = 0

        filesToCompare.forEach { filePath ->
            val snapshot1 = blockStore.getLatestBlockSnapshotBefore(filePath, time1)
            val snapshot2 = blockStore.getLatestBlockSnapshotBefore(filePath, time2)

            if (snapshot1 != null || snapshot2 != null) {
                val changes = blockStore.compareBlocks(snapshot1, snapshot2)
                if (changes.isNotEmpty()) {
                    result.appendLine("${Colors.filename(filePath)}:")
                    result.append(formatBlockChanges(changes, objectStore))
                    result.appendLine()
                    totalChanges += changes.size
                }
            }
        }

        if (totalChanges == 0) {
            result.appendLine(Colors.dim("No differences found"))
        } else {
            result.appendLine("${Colors.bold("Total changes:")} $totalChanges")
        }

        return result.toString()
    }

    private fun compareWorkingDirectoryToCommit(repo: Repository, hash: String, targetFile: String?): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))
        val blockParser = BlockParser()

        // Find commit
        val commit = findCommit(repo, hash)
            ?: throw Exception("Commit ${Colors.yellow(hash)} not found")

        val commitTime = Instant.parse(commit.timestamp)

        val filesToCompare = if (targetFile != null) {
            val filePath = repo.path.resolve(targetFile)
            if (!filePath.exists()) {
                throw Exception("File not found: $targetFile")
            }
            listOf(targetFile)
        } else {
            org.notevc.utils.FileUtils.findMarkdownFiles(repo.path).map {
                repo.path.relativize(it).toString()
            }
        }

        val result = StringBuilder()
        result.appendLine(Colors.bold("Comparing working directory to commit:"))
        result.appendLine("  ${Colors.yellow(hash.take(8))} ${Colors.lightGray(commit.message)}")
        result.appendLine()

        var totalChanges = 0

        filesToCompare.forEach { filePath ->
            val fullPath = repo.path.resolve(filePath)
            if (fullPath.exists()) {
                val content = Files.readString(fullPath)
                val parsedFile = blockParser.parseFile(content, filePath)

                // Skip disabled files
                if (parsedFile.frontMatter?.isEnabled == false) {
                    return@forEach
                }

                val commitSnapshot = blockStore.getLatestBlockSnapshotBefore(filePath, commitTime)
                val currentSnapshot = createCurrentSnapshot(parsedFile, objectStore)

                val changes = blockStore.compareBlocks(commitSnapshot, currentSnapshot)
                if (changes.isNotEmpty()) {
                    result.appendLine("${Colors.filename(filePath)}:")
                    result.append(formatBlockChanges(changes, objectStore))
                    result.appendLine()
                    totalChanges += changes.size
                }
            }
        }

        if (totalChanges == 0) {
            result.appendLine(Colors.dim("No differences found"))
        } else {
            result.appendLine("${Colors.bold("Total changes:")} $totalChanges")
        }

        return result.toString()
    }

    private fun showWorkingDirectoryChanges(repo: Repository, targetFile: String?): String {
        val objectStore = ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("${Repository.NOTEVC_DIR}/blocks"))
        val blockParser = BlockParser()

        val filesToCheck = if (targetFile != null) {
            val filePath = repo.path.resolve(targetFile)
            if (!filePath.exists()) {
                throw Exception("File not found: $targetFile")
            }
            listOf(targetFile)
        } else {
            org.notevc.utils.FileUtils.findMarkdownFiles(repo.path).map {
                repo.path.relativize(it).toString()
            }
        }

        val result = StringBuilder()
        result.appendLine(Colors.bold("Changes in working directory:"))
        result.appendLine()

        var totalChanges = 0

        filesToCheck.forEach { filePath ->
            val fullPath = repo.path.resolve(filePath)
            if (fullPath.exists()) {
                val content = Files.readString(fullPath)
                val parsedFile = blockParser.parseFile(content, filePath)

                // Skip disabled files
                if (parsedFile.frontMatter?.isEnabled == false) return@forEach

                val latestSnapshot = blockStore.getLatestBlockSnapshot(filePath)
                val currentSnapshot = createCurrentSnapshot(parsedFile, objectStore)

                val changes = blockStore.compareBlocks(latestSnapshot, currentSnapshot)
                if (changes.isNotEmpty()) {
                    result.appendLine("${Colors.filename(filePath)}:")
                    result.append(formatBlockChanges(changes, objectStore))
                    result.appendLine()
                    totalChanges += changes.size
                }
            }
        }

        if (totalChanges == 0) {
            result.appendLine(Colors.lightGray("No changes detected - working directory clean"))
        } else {
            result.appendLine("${Colors.bold("Total changes:")} $totalChanges")
        }

        return result.toString()
    }

    private fun formatBlockChanges(changes: List<BlockChange>, objectStore: ObjectStore): String {
        val result = StringBuilder()

        changes.sortedBy { it.blockId }.forEach { change ->
            val headingText = change.heading.replace(Regex("^#+\\s*"), "").trim()
            val blockId = change.blockId.take(8)

            when (change.type) {
                BlockChangeType.ADDED -> {
                    result.appendLine()
                    result.appendLine("  ${Colors.green("+++")} ${Colors.bold("ADDED")} ${Colors.green("+++")} ${Colors.heading(headingText)} ${Colors.dim("[$blockId]")}")
                    result.appendLine("  ${Colors.dim("─".repeat(60))}")
                    
                    if (change.newHash != null) {
                        val content = objectStore.getContent(change.newHash)
                        if (content != null) {
                            content.lines().take(5).forEach { line ->
                                result.appendLine("  ${Colors.green("+")} $line")
                            }
                            if (content.lines().size > 5) {
                                result.appendLine("  ${Colors.dim("  ... ${content.lines().size - 5} more lines")}")
                            }
                        }
                    }
                }
                BlockChangeType.DELETED -> {
                    result.appendLine()
                    result.appendLine("  ${Colors.error("---")} ${Colors.bold("DELETED")} ${Colors.error("---")} ${Colors.heading(headingText)} ${Colors.dim("[$blockId]")}")
                    result.appendLine("  ${Colors.dim("─".repeat(60))}")
                    
                    if (change.oldHash != null) {
                        val content = objectStore.getContent(change.oldHash)
                        if (content != null) {
                            content.lines().take(5).forEach { line ->
                                result.appendLine("  ${Colors.error("-")} $line")
                            }
                            if (content.lines().size > 5) {
                                result.appendLine("  ${Colors.dim("  ... ${content.lines().size - 5} more lines")}")
                            }
                        }
                    }
                }
                BlockChangeType.MODIFIED -> {
                    result.appendLine()
                    result.appendLine("  ${Colors.warn("~~~")} ${Colors.bold("MODIFIED")} ${Colors.warn("~~~")} ${Colors.heading(headingText)} ${Colors.dim("[$blockId]")}")
                    result.appendLine("  ${Colors.dim("─".repeat(60))}")
                    
                    // Show detailed diff
                    if (change.oldHash != null && change.newHash != null) {
                        val oldContent = objectStore.getContent(change.oldHash)
                        val newContent = objectStore.getContent(change.newHash)
                        
                        if (oldContent != null && newContent != null) {
                            val diff = computeDetailedDiff(oldContent, newContent)
                            diff.forEach { line ->
                                result.appendLine("  $line")
                            }
                        }
                    }
                }
            }
        }

        return result.toString()
    }

    private fun computeDetailedDiff(oldContent: String, newContent: String): List<String> {
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        val diff = mutableListOf<String>()
        
        // Use a simple line-by-line diff algorithm
        val maxLines = maxOf(oldLines.size, newLines.size)
        var oldIndex = 0
        var newIndex = 0
        var displayedLines = 0
        val maxDisplayLines = 15
        
        while ((oldIndex < oldLines.size || newIndex < newLines.size) && displayedLines < maxDisplayLines) {
            val oldLine = oldLines.getOrNull(oldIndex)
            val newLine = newLines.getOrNull(newIndex)
            
            when {
                oldLine == null && newLine != null -> {
                    // Addition
                    diff.add("${Colors.green("+ ")} $newLine")
                    newIndex++
                    displayedLines++
                }
                oldLine != null && newLine == null -> {
                    // Deletion
                    diff.add("${Colors.boldRed("- ")} $oldLine")
                    oldIndex++
                    displayedLines++
                }
                oldLine == newLine -> {
                    // Unchanged line (context)
                    diff.add("${Colors.dim("  ")} ${Colors.dimWhite(oldLine ?: "")}")
                    oldIndex++
                    newIndex++
                    displayedLines++
                }
                else -> {
                    // Modified line
                    diff.add("${Colors.boldRed("- ")} $oldLine")
                    diff.add("${Colors.green("+ ")} $newLine")
                    oldIndex++
                    newIndex++
                    displayedLines += 2
                }
            }
        }
        
        val remainingLines = (oldLines.size - oldIndex) + (newLines.size - newIndex)
        if (remainingLines > 0) {
            diff.add(Colors.dim("  ... $remainingLines more lines"))
        }
        
        return diff
    }

    private fun createCurrentSnapshot(parsedFile: ParsedFile, objectStore: ObjectStore): BlockSnapshot {
        return BlockSnapshot(
            filePath = parsedFile.path,
            timestamp = Instant.now().toString(),
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

    private fun getTrackedFilesAtTime(repo: Repository, time1: Instant, time2: Instant): List<String> {
        val blocksDir = repo.path.resolve("${Repository.NOTEVC_DIR}/blocks")
        if (!blocksDir.exists()) return emptyList()

        val files = mutableSetOf<String>()
        val json = Json { ignoreUnknownKeys = true }

        Files.walk(blocksDir)
            .filter { it.isRegularFile() && it.fileName.toString().startsWith("blocks-") }
            .forEach { snapshotFile ->
                try {
                    val content = Files.readString(snapshotFile)
                    val snapshot = json.decodeFromString<BlockSnapshot>(content)
                    val snapshotTime = Instant.parse(snapshot.timestamp)

                    // Include files that existed around either time
                    if (!snapshotTime.isAfter(time1) || !snapshotTime.isAfter(time2)) {
                        files.add(snapshot.filePath)
                    }
                } catch (e: Exception) {
                    // Skip corrupted snapshots
                }
            }

        return files.toList()
    }
}

data class DiffOptions(
    val commitHash1: String?,
    val commitHash2: String?,
    val targetFile: String?,
    val blockHash: String? = null
)
