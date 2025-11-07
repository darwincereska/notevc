package io.notevc.core

import java.nio.file.Files
import io.notevc.utils.HashUtils
import java.nio.file.Path
import kotlin.io.path.*

class ObjectStore(private val objectsDir: Path) {
    // Store content and return its hash
    // Uses git-like storage: objects/ab/cdef123... (first 2 characters as directory)
    fun storeContent(content: String): String {
        val hash = HashUtils.sha256(content)
        val objectPath = getObjectPath(hash)

        // Only store if it doesn't already exist
        if (!objectPath.exists()) {
            Files.createDirectories(objectPath.parent)
            Files.writeString(objectPath, content)
        }

        return hash
    }

    // Retrieve content by hash
    fun getContent(hash: String): String? {
        val objectPath = getObjectPath(hash)
        return if (objectPath.exists()) {
            Files.readString(objectPath)
        } else null
    }

    // Check if content exists
    fun hasContent(hash: String): Boolean = getObjectPath(hash).exists()

    // Get all stored object hashes
    fun getAllHashes(): List<String> {
        if (!objectsDir.exists()) return emptyList()

        return Files.walk(objectsDir, 2)
            .filter { it.isRegularFile() }
            .map { path ->
                val parent = path.parent.fileName.toString()
                val filename = path.fileName.toString()
                parent + filename
            }
            .toList()
    }

    // Get storage statistics
    fun getStats(): ObjectStoreStats {
        val hashes = getAllHashes()
        val totalSize = hashes.sumOf { hash -> 
            getObjectPath(hash).fileSize()
        }

        return ObjectStoreStats(
            objectCount = hashes.size,
            totalSize = totalSize
        )
    }

    // Convert hash to file path
    private fun getObjectPath(hash: String): Path {
        require(hash.length >= 3) { "Hash too short: $hash" }
        val dir = hash.take(2)
        val filename = hash.drop(2)
        return objectsDir.resolve(dir).resolve(filename)
    }
}

data class ObjectStoreStats(
    val objectCount: Int,
    val totalSize: Long
)
