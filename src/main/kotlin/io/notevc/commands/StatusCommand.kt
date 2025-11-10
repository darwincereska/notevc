package io.notevc.commands

import io.notevc.core.*
import io.notevc.utils.FileUtils
import io.notevc.utils.ColorUtils
import io.notevc.core.Repository.Companion.NOTEVC_DIR
import java.time.Instant

class StatusCommand {
    fun execute(): Result<String> {
        return try {
            val repo = Repository.find()
                ?: return Result.failure(Exception("Not in notevc repository. Run `notevc init` first."))

            val status = getRepositoryStatus(repo)
            val output = formatStatusOutput(status)

            Result.success(output)
        }
        catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getRepositoryStatus(repo: Repository): RepositoryStatus {
        val objectStore = ObjectStore(repo.path.resolve("$NOTEVC_DIR/objects"))
        val blockStore = BlockStore(objectStore, repo.path.resolve("$NOTEVC_DIR/blocks"))
        val blockParser = BlockParser()

        // Find all markdown files in the repository
        val currentFiles = FileUtils.findMarkdownFiles(repo.path)
        val trackedFiles = blockStore.getTrackedFiles()

        val fileStatuses = mutableListOf<FileStatus>()

        // Check tracked files (files that have previous snapshots)
        trackedFiles.forEach { filePath -> 
            val fullPath = repo.path.resolve(filePath)

            // File exists - check for changes
            val currentContent = java.nio.file.Files.readString(fullPath)
            val parsedFile = blockParser.parseFile(currentContent, filePath)

            // Get latest snapshot for comparison
            val latestSnapshot = blockStore.getLatestBlockSnapshot(filePath)

            if (latestSnapshot != null) {
                // Create snapshot to compare
                val currentSnapshot = createCurrentSnapshot(parsedFile, objectStore)
                val changes = blockStore.compareBlocks(latestSnapshot, currentSnapshot)

                if (changes.isNotEmpty()) {
                    fileStatuses.add(FileStatus(
                        path = filePath,
                        type = FileStatusType.MODIFIED,
                        blockChanges = changes
                    ))
                }
            } else {
                // File was deleted
                fileStatuses.add(FileStatus(
                    path = filePath,
                    type = FileStatusType.DELETED
                ))
            }
        }

        // Check for untracked files (new files)
        currentFiles.forEach { filePath -> 
            val relativePath = repo.path.relativize(filePath).toString() 
            
            if (relativePath !in trackedFiles) {
                val content = java.nio.file.Files.readString(filePath)
                val parsedFile = blockParser.parseFile(content, relativePath)

                fileStatuses.add(FileStatus(
                    path = relativePath,
                    type = FileStatusType.UNTRACKED,
                    blockCount = parsedFile.blocks.size,
                ))
            }
        }

        return RepositoryStatus(fileStatuses)
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

    private fun formatStatusOutput(status: RepositoryStatus): String {
        if (status.files.isEmpty()) return "Working directory clean - no changes detected"

        val output = StringBuilder()
        val grouped = status.files.groupBy { it.type }

        // Modified files
        grouped[FileStatusType.MODIFIED]?.let { modifiedFiles -> 
            output.appendLine(ColorUtils.bold("Modified files:"))
            modifiedFiles.forEach { fileStatus -> 
                output.appendLine("  ${ColorUtils.filename(fileStatus.path)}")
                fileStatus.blockChanges?.forEach { change -> 
                    val symbol = when (change.type) {
                        BlockChangeType.MODIFIED -> ColorUtils.modified("M") 
                        BlockChangeType.ADDED -> ColorUtils.added("+")
                        BlockChangeType.DELETED -> ColorUtils.deleted("-")
                    }
                    val heading = change.heading.replace(Regex("^#+\\s*"), "").trim()
                    output.appendLine("    $symbol ${ColorUtils.heading(heading)}")
                }
            }
        }

        // Untracked files
        grouped[FileStatusType.UNTRACKED]?.let { untrackedFiles -> 
            output.appendLine(ColorUtils.bold("Untracked files:"))
            untrackedFiles.forEach { fileStatus -> 
                output.appendLine("  ${ColorUtils.untracked(fileStatus.path)} ${ColorUtils.dim("(${fileStatus.blockCount} blocks)")}")
            }
            output.appendLine()
        }

        // Deleted files
        grouped[FileStatusType.DELETED]?.let { deletedFiles -> 
            output.appendLine(ColorUtils.bold("Deleted files:"))
            deletedFiles.forEach { fileStatus -> 
                output.appendLine("  ${ColorUtils.deleted(fileStatus.path)}")
            }
            output.appendLine()
        }

        return output.toString().trim()
    }
}

// Data classes for status information
data class RepositoryStatus(
    val files: List<FileStatus>
)

data class FileStatus(
    val path: String,
    val type: FileStatusType,
    val blockChanges: List<BlockChange>? = null,
    val blockCount: Int? = null
)

enum class FileStatusType {
    MODIFIED, // File has changes
    UNTRACKED, // File was not yet commited
    DELETED // File was removed
}
