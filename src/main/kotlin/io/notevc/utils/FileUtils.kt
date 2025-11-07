package io.notevc.utils

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*
import io.notevc.core.Repository.Companion.NOTEVC_DIR

object FileUtils {
    // Find all markdown files in directory (recursively)
    fun findMarkdownFiles(rootPath: Path): List<Path> {
        if (!rootPath.exists() || !rootPath.isDirectory()) return emptyList()

        return Files.walk(rootPath)
            .filter { path ->
                path.isRegularFile() &&
                path.extension.lowercase() == "md" &&
                !isInNotevcDir(path, rootPath)
            }
            .toList()
    }

    // Get file state information
    fun getFileState(filePath: Path, rootPath: Path): FileState {
        val content = Files.readString(filePath)
        val contentHash = HashUtils.sha256(content)
        val relativePath = rootPath.relativize(filePath).toString()

        return FileState(
            path = relativePath,
            contentHash = contentHash,
            size = filePath.fileSize(),
            lastModified = filePath.getLastModifiedTime().toInstant()
        )
    }

    // Get current state of all markdown files
    fun getCurrentFileStates(rootPath: Path): Map<String, FileState> {
        return findMarkdownFiles(rootPath)
            .associate { filePath -> 
                val fileState = getFileState(filePath, rootPath)
                fileState.path to fileState
            }
    }

    // Create relative path string for display
    fun getDisplayPath(filePath: Path, rootPath: Path): String = rootPath.relativize(filePath).toString()

    // Ensure directory exists
    fun ensureDirectoryExists(path: Path) {
        if (!path.exists()) {
            Files.createDirectories(path)
        }
    }

    // Check if path is inside .notevc directory
    private fun isInNotevcDir(filePath: Path, rootPath: Path): Boolean {
        val relativePath = rootPath.relativize(filePath)
        return relativePath.toString().startsWith(NOTEVC_DIR)
    }
}

// Represents the state of a file at a point in time
data class FileState(
    val path: String, // Relative path from repo root
    val contentHash: String, // SHA-256 of content
    val size: Long, // File size in bytes
    val lastModified: Instant // Last modification time
)

// Represents a change to a file
data class FileChange(
    val path: String,
    val type: ChangeType,
    val oldHash: String? = null,
    val newHash: String? = null
)

enum class ChangeType {
    ADDED, // New file
    MODIFIED, // Content changed
    DELETED // File removed
}
