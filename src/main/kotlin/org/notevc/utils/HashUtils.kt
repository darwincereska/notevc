package org.notevc.utils

import java.security.MessageDigest

object HashUtils {
    // Generate SHA-256 hash of string content
    fun sha256(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(content.toByteArray())
        return hashBytes.joinToString("") { "%02x" .format(it) }
    }

    // Generate SHA-256 hash of file content
    fun sha256File(filePath: java.nio.file.Path): String {
        val content = java.nio.file.Files.readString(filePath)
        return sha256(content)
    }

    // Generate short hash (first 8 characters) for display
    fun shortHash(hash: String): String = hash.take(8)

    // Validate hash format
    fun isValidHash(hash: String): Boolean = hash.matches(Regex("^[a-f0-9]{64}$"))
}
