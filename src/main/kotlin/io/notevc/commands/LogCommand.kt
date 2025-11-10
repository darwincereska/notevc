package io.notevc.commands

import io.notevc.core.*
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.time.Instant
import io.notevc.utils.ColorUtils
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

class LogCommand {
    
    fun execute(args: List<String>): Result<String> {
        return try {
            val repo = Repository.find()
                ?: return Result.failure(Exception("Not in a notevc repository. Run 'notevc init' first."))
            
            val options = parseArgs(args)
            val logOutput = generateLog(repo, options)
            
            Result.success(logOutput)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun parseArgs(args: List<String>): LogOptions {
        var maxCount: Int? = null
        var since: String? = null
        var oneline = false
        var showFiles = false
        var targetFile: String? = null
        
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--max-count", "-n" -> {
                    if (i + 1 < args.size) {
                        maxCount = args[i + 1].toIntOrNull()
                        i += 2
                    } else {
                        i++
                    }
                }
                "--since" -> {
                    if (i + 1 < args.size) {
                        since = args[i + 1]
                        i += 2
                    } else {
                        i++
                    }
                }
                "--oneline" -> {
                    oneline = true
                    i++
                }
                "--file", "-f" -> {
                    if (i + 1 < args.size) {
                        targetFile = args[i + 1]
                        showFiles = true
                        i += 2
                    } else {
                        showFiles = true
                        i++
                    }
                }
                else -> i++
            }
        }
        
        return LogOptions(maxCount, since, oneline, showFiles, targetFile)
    }
    
    private fun generateLog(repo: Repository, options: LogOptions): String {
        val timelineFile = repo.path.resolve("${Repository.NOTEVC_DIR}/timeline.json")
        
        if (!timelineFile.exists()) {
            return "No commits yet"
        }
        
        val content = Files.readString(timelineFile)
        if (content.trim() == "[]") {
            return "No commits yet"
        }
        
        val commits = Json.decodeFromString<List<CommitEntry>>(content)
        
        // Filter commits based on options
        val filteredCommits = filterCommits(commits, options)
        
        return if (options.oneline) {
            formatOnelineLog(filteredCommits, repo, options)
        } else {
            formatDetailedLog(filteredCommits, repo, options)
        }
    }
    
    private fun filterCommits(commits: List<CommitEntry>, options: LogOptions): List<CommitEntry> {
        var filtered = commits
        
        // Filter by date if --since is provided
        options.since?.let { sinceStr ->
            val sinceTime = parseSinceTime(sinceStr)
            filtered = filtered.filter { commit ->
                val commitTime = Instant.parse(commit.timestamp)
                commitTime.isAfter(sinceTime)
            }
        }
        
        // Limit count if --max-count is provided
        options.maxCount?.let { count ->
            filtered = filtered.take(count)
        }
        
        return filtered
    }
    
    private fun parseSinceTime(since: String): Instant {
        return when {
            since.endsWith("h") -> {
                val hours = since.dropLast(1).toLongOrNull() ?: 1
                Instant.now().minusSeconds(hours * 3600)
            }
            since.endsWith("d") -> {
                val days = since.dropLast(1).toLongOrNull() ?: 1
                Instant.now().minusSeconds(days * 24 * 3600)
            }
            since.endsWith("w") -> {
                val weeks = since.dropLast(1).toLongOrNull() ?: 1
                Instant.now().minusSeconds(weeks * 7 * 24 * 3600)
            }
            else -> {
                try {
                    Instant.parse(since)
                } catch (e: Exception) {
                    Instant.now().minusSeconds(24 * 3600)
                }
            }
        }
    }
    
    private fun formatOnelineLog(commits: List<CommitEntry>, repo: Repository, options: LogOptions): String {
        return commits.joinToString("\n") { commit ->
            val fileInfo = if (options.showFiles) {
                val stats = getCommitStats(repo, commit, options.targetFile)
                ColorUtils.dim(" (${stats.filesChanged} files, ${stats.totalBlocks} blocks)")
            } else ""

            "${ColorUtils.hash(commit.hash)} ${commit.message}$fileInfo"
        }
    }
    
    private fun formatDetailedLog(commits: List<CommitEntry>, repo: Repository, options: LogOptions): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

        return commits.joinToString("\n\n") { commit ->
            val timestamp = Instant.parse(commit.timestamp)
            val formattedDate = formatter.format(timestamp)

            buildString {
                appendLine("${ColorUtils.bold("commit")} ${ColorUtils.hash(commit.hash)}")
                appendLine("${ColorUtils.bold("Author:")} ${ColorUtils.author(commit.author)}")
                appendLine("${ColorUtils.bold("Date:")} ${ColorUtils.date(formattedDate)}")

                if (options.showFiles) {
                    val stats = getCommitStats(repo, commit, options.targetFile)
                    appendLine("${ColorUtils.info("Files changed:")} ${stats.filesChanged}, ${ColorUtils.info("Total blocks:")} ${stats.totalBlocks}")

                    if (stats.fileDetails.isNotEmpty()) {
                        appendLine()
                        stats.fileDetails.forEach { (file, blocks) ->
                            appendLine("    ${ColorUtils.filename(file)} ${ColorUtils.dim("(${blocks.size} blocks)")}")
                            blocks.forEach { block ->
                                val heading = block.heading.replace(Regex("^#+\\s*"), "").trim()
                                appendLine("      - ${ColorUtils.hash(block.id.take(8))}: ${ColorUtils.heading(heading)}")
                            }
                        }
                    }
                }

                appendLine()
                appendLine("    ${commit.message}")
            }
        }
    }
    
    private fun getCommitStats(repo: Repository, commit: CommitEntry, targetFile: String?): CommitStats {
        val blockStore = BlockStore(
            ObjectStore(repo.path.resolve("${Repository.NOTEVC_DIR}/objects")),
            repo.path.resolve("${Repository.NOTEVC_DIR}/blocks")
        )
        
        // Find all block snapshots for this commit timestamp
        val commitTime = Instant.parse(commit.timestamp)
        val snapshots = findSnapshotsForCommit(repo, commitTime, targetFile)
        
        val fileDetails = mutableMapOf<String, List<BlockState>>()
        var totalBlocks = 0
        
        snapshots.forEach { snapshot ->
            if (targetFile == null || snapshot.filePath == targetFile) {
                fileDetails[snapshot.filePath] = snapshot.blocks
                totalBlocks += snapshot.blocks.size
            }
        }
        
        return CommitStats(
            filesChanged = fileDetails.size,
            totalBlocks = totalBlocks,
            fileDetails = fileDetails
        )
    }
    
    private fun findSnapshotsForCommit(repo: Repository, commitTime: Instant, targetFile: String?): List<BlockSnapshot> {
        val blocksDir = repo.path.resolve("${Repository.NOTEVC_DIR}/blocks")
        if (!blocksDir.exists()) return emptyList()
        
        val snapshots = mutableListOf<BlockSnapshot>()
        val json = Json { ignoreUnknownKeys = true }
        
        // Look for snapshots around the commit time (within 1 minute)
        val timeRange = 60L // seconds
        
        Files.walk(blocksDir)
            .filter { it.isRegularFile() && it.fileName.toString().startsWith("blocks-") }
            .forEach { snapshotFile ->
                try {
                    val content = Files.readString(snapshotFile)
                    val snapshot = json.decodeFromString<BlockSnapshot>(content)
                    val snapshotTime = Instant.parse(snapshot.timestamp)
                    
                    // Check if snapshot is within time range of commit
                    val timeDiff = kotlin.math.abs(commitTime.epochSecond - snapshotTime.epochSecond)
                    if (timeDiff <= timeRange) {
                        if (targetFile == null || snapshot.filePath == targetFile) {
                            snapshots.add(snapshot)
                        }
                    }
                } catch (e: Exception) {
                    // Skip corrupted snapshots
                }
            }
        
        return snapshots
    }
}

data class LogOptions(
    val maxCount: Int? = null,
    val since: String? = null,
    val oneline: Boolean = false,
    val showFiles: Boolean = false,
    val targetFile: String? = null
)

data class CommitStats(
    val filesChanged: Int,
    val totalBlocks: Int,
    val fileDetails: Map<String, List<BlockState>>
)
