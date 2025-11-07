package io.notevc.core

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.*
import io.notevc.BuildConfig
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.nio.file.Files
import java.time.Instant

class Repository private constructor(private val rootPath: Path) {
    private val notevcDir = rootPath.resolve(NOTEVC_DIR)
    private val objectStore = ObjectStore(notevcDir.resolve("objects"))
    val path: Path get() = rootPath
    val isInitialized: Boolean get() = notevcDir.exists()

    companion object {
        // Factory methods - these create Repository instances
        
        // Create repository at a specified path
        fun at(path: String): Result<Repository> {
            return try {
                val absolutePath = Path.of(path).toAbsolutePath()
                when {
                    !absolutePath.exists() -> Result.failure(Exception("Directory does not exist: $path"))
                    !absolutePath.isDirectory() -> Result.failure(Exception("Path is not directory: $path"))
                    !absolutePath.isWritable() -> Result.failure(Exception("Directory is not writable: $path"))
                    else -> Result.success(Repository(absolutePath))
                }
            }
            catch (e: Exception) {
                Result.failure(e)
            }
        }

        // Create repository at current directory
        fun current(): Repository = Repository(Path.of(System.getProperty("user.dir")).toAbsolutePath())

        // Find existing repository by walking up
        fun find(): Repository? {
            var current = Path.of(System.getProperty("user.dir")).toAbsolutePath()
            while (current != null) {
                if (current.resolve(NOTEVC_DIR).exists()) return Repository(current)
                current = current.parent // Go up one level
            }
            return null // No repository found
        }

        // Constants
        const val NOTEVC_DIR = ".notevc"
        val VERSION = BuildConfig.VERSION
        val BUILD_TIME = BuildConfig.BUILD_TIME

    }

    override fun toString(): String = "Repository(path=${rootPath.toAbsolutePath()}, initialized=$isInitialized)"

    fun init(): Result<Unit> {
        return try {
            // Check if already initialized
            if (isInitialized) return Result.failure(Exception("Repository already initialized at ${rootPath.toAbsolutePath()}"))

            // Create .notevc directory structure
            Files.createDirectories(notevcDir)
            Files.createDirectories(notevcDir.resolve("objects"))

            // Create initial metadata
            val metadata = RepoMetadata(
                version = VERSION,
                created = Instant.now().toString(),
                head = null
            )

            // Save metadata to .notevc/metadata.json
            val metadataFile = notevcDir.resolve("metadata.json")
            Files.writeString(metadataFile, Json.encodeToString(metadata))

            // Create empty timeline
            val timelineFile = notevcDir.resolve("timeline.json")
            Files.writeString(timelineFile, "[]")

            Result.success(Unit)
        }
        catch (e: Exception) {
            Result.failure(e)
        }
    }
}

@Serializable
data class RepoMetadata(
    val version: String,
    val created: String,
    var head: String?,
    val config: RepoConfig = RepoConfig(),
    val lastCommit: CommitInfo? = null
)

@Serializable
data class CommitInfo(
    val hash: String,
    val message: String,
    val timestamp: String,
    val author: String
)

@Serializable
data class RepoConfig(
    val autoCommit: Boolean = false,
    val compressionEnabled: Boolean = false,
    val maxSnapshots: Int = 100
)

@Serializable
data class CommitEntry(
    val hash: String,
    val message: String,
    val timestamp: String,
    val author: String,
    val parent: String? = null
)
