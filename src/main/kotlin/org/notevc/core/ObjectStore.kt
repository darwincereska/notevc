package org.notevc.core

import java.nio.file.Files
import org.notevc.utils.HashUtils
import java.nio.file.Path
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream
import kotlin.io.path.*

class ObjectStore(private val objectsDir: Path) {
    companion object {
        private const val COMPRESSION_ENABLED = true
        private const val MIN_COMPRESSION_SIZE = 100 // bytes
    }

    // Store content and return its hash
    // Uses git-like storage: objects/ab/cdef123... (first 2 characters as directory)
    // Content is compressed if it exceeds MIN_COMPRESSION_SIZE
    fun storeContent(content: String): String {
        val hash = HashUtils.sha256(content)
        val objectPath = getObjectPath(hash)

        // Only store if it doesn't already exist
        if (!objectPath.exists()) {
            Files.createDirectories(objectPath.parent)
            
            if (COMPRESSION_ENABLED && content.length > MIN_COMPRESSION_SIZE) {
                val compressed = compressString(content)
                Files.write(objectPath, compressed)
            } else {
                Files.writeString(objectPath, content)
            }
        }

        return hash
    }

    // Retrieve content by hash
    fun getContent(hash: String): String? {
        val objectPath = getObjectPath(hash)
        if (!objectPath.exists()) return null
        
        return try {
            val bytes = Files.readAllBytes(objectPath)
            
            // Try to decompress first, fall back to plain text
            try {
                if (COMPRESSION_ENABLED && bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                    // This is a GZIP file (magic bytes: 0x1f 0x8b)
                    decompressString(bytes)
                } else {
                    String(bytes, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                // If decompression fails, try as plain text
                String(bytes, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun compressString(content: String): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { gzip ->
            gzip.write(content.toByteArray(Charsets.UTF_8))
        }
        return outputStream.toByteArray()
    }

    private fun decompressString(compressed: ByteArray): String {
        return GZIPInputStream(compressed.inputStream()).use { gzip ->
            gzip.readBytes().toString(Charsets.UTF_8)
        }
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
