package org.notevc.core

import org.notevc.utils.HashUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*

class BlockStore(
    private val objectStore: ObjectStore,
    private val blocksDir: Path
) {
    private val json = Json { prettyPrint = true }

    // Store blocks from a parsed file and return block snapshot
    fun storeBlocks(parsedFile: ParsedFile, timestamp: Instant): BlockSnapshot {
        val blockStates = parsedFile.blocks.map { block -> 
            val contentHash = objectStore.storeContent(block.content)

            BlockState(
                id = block.id,
                heading = block.heading,
                contentHash = contentHash,
                type = block.type,
                order = block.order
            )
        }

        val snapshot = BlockSnapshot(
            filePath = parsedFile.path,
            timestamp = timestamp.toString(),
            blocks = blockStates,
            frontMatter = parsedFile.frontMatter
        )

        // Store block snapshot with time-based structure (yyyy/mm/dd)
        storeBlockSnapshot(snapshot)

        return snapshot
    }

    // Get blocks for a file at a specific time
    fun getBlocksAtTime(filePath: String, timestamp: Instant): List<Block>? {
        val snapshot = getLatestBlockSnapshotBefore(filePath, timestamp)
        return snapshot?.let { reconstructBlocks(it) }
    }

    // Get current blocks for a file
    fun getCurrentBlocks(filePath: String): List<Block>? {
        val snapshot = getLatestBlockSnapshot(filePath)
        return snapshot?.let { reconstructBlocks(it) }
    }

    // Compare blocks between two snapshots
    fun compareBlocks(oldSnapshot: BlockSnapshot?, newSnapshot: BlockSnapshot?): List<BlockChange> {
        val changes = mutableListOf<BlockChange>()

        val oldBlocks = oldSnapshot?.blocks?.associateBy { it.id } ?: emptyMap()
        val newBlocks = newSnapshot?.blocks?.associateBy { it.id } ?: emptyMap()

        // Find added and modified blocks
        newBlocks.forEach { (id, newBlock) -> 
            val oldBlock = oldBlocks[id]
            when {
                oldBlock == null -> {
                    changes.add(BlockChange(
                        blockId = id,
                        type = BlockChangeType.ADDED,
                        heading = newBlock.heading,
                        newHash = newBlock.contentHash
                    ))
                }
                oldBlock.contentHash != newBlock.contentHash -> {
                    changes.add(BlockChange(
                        blockId = id,
                        type = BlockChangeType.MODIFIED,
                        heading = newBlock.heading,
                        oldHash = oldBlock.contentHash,
                        newHash = newBlock.contentHash
                    ))
                }
            }
        }

        // Find deleted blocks
        oldBlocks.forEach { (id, oldBlock) -> 
            if (id !in newBlocks) {
                changes.add(BlockChange(
                    blockId = id,
                    type = BlockChangeType.DELETED,
                    heading = oldBlock.heading,
                    oldHash = oldBlock.contentHash
                ))
            }
        }

        return changes
    }

    private fun storeBlockSnapshot(snapshot: BlockSnapshot) {
        val datePath = getDatePath(Instant.parse(snapshot.timestamp))
        val timeString: String = getTimeString(Instant.parse(snapshot.timestamp))

        Files.createDirectories(blocksDir.resolve(datePath))

        val filename = "blocks-$timeString-${snapshot.filePath.replace("/","_")}.json"
        val snapshotPath: Path = blocksDir.resolve(datePath).resolve(filename)

        Files.writeString(snapshotPath, json.encodeToString(snapshot))
    }

    private fun reconstructBlocks(snapshot: BlockSnapshot): List<Block> {
        return snapshot.blocks.map { blockState -> 
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
    }

    fun getLatestBlockSnapshot(filePath: String): BlockSnapshot? {
        // Implementation to find latest snapshot for file
        // Walk through time directories and find most recent
        if (!blocksDir.exists()) return null

        // Walk through all time directories to find snapshot for this file
        val snapshots = mutableListOf<Pair<BlockSnapshot, Instant>>()

        Files.walk(blocksDir)
            .filter { it.isRegularFile() && it.fileName.toString().startsWith("blocks-") }
            .filter { filePath.replace("/","_") in it.fileName.toString()}
            .forEach { snapshotFile -> 
                try {
                    val content = Files.readString(snapshotFile)
                    val snapshot = json.decodeFromString<BlockSnapshot>(content)
                    val timestamp = Instant.parse(snapshot.timestamp)
                    snapshots.add(snapshot to timestamp)
                }
                catch (e: Exception) {
                    // Skip corrupted snapshots
                }
            }
        // Return the most recent snapshot
        return snapshots
            .maxByOrNull { it.second }
            ?.first
    }

    fun getLatestBlockSnapshotBefore(filePath: String, timestamp: Instant): BlockSnapshot? {
        if (!blocksDir.exists()) return null

        val snapshots = mutableListOf<Pair<BlockSnapshot, Instant>>()

        Files.walk(blocksDir)
            .filter { it.isRegularFile() && it.fileName.toString().startsWith("blocks-") }
            .filter { filePath.replace("/","_") in it.fileName.toString()}
            .forEach { snapshotFile ->
                try {
                    val content = Files.readString(snapshotFile)
                    val snapshot = json.decodeFromString<BlockSnapshot>(content)
                    val snapshotTime = Instant.parse(snapshot.timestamp)

                    // Only include snapshots before or at the given timestamp
                    if (!snapshotTime.isAfter(timestamp)) {
                        snapshots.add(snapshot to snapshotTime)
                    }
                } catch (e: Exception) {
                    // Skip corrupted snapshots
                }
            }

        // Return the most recent snapshot before the timestamp
        return snapshots
        .maxByOrNull { it.second }
        ?.first
    }

    // Get all snapshots for specific file 
    fun getSnapshotForFile(filePath: String): List<BlockSnapshot> {
        if (!blocksDir.exists()) return emptyList()

        val snapshots = mutableListOf<Pair<BlockSnapshot, Instant>>()

        Files.walk(blocksDir)
            .filter { it.isRegularFile() && it.fileName.toString().startsWith("blocks-") }
            .filter { filePath.replace("/","_") in it.fileName.toString() }
            .forEach { snapshotFile -> 
                try {
                    val content = Files.readString(snapshotFile)
                    val snapshot = json.decodeFromString<BlockSnapshot>(content)
                    val timestamp = Instant.parse(snapshot.timestamp)
                    snapshots.add(snapshot to timestamp)
                }
                catch (e: Exception) {
                    // Skip corrupted snapshots
                }
            }

        return snapshots
            .sortedByDescending { it.second }
            .map { it.first }
    }

    // Check if any snapshots exist for a file
    fun hasSnapshots(filePath: String): Boolean {
        if (!blocksDir.exists()) return false

        return Files.walk(blocksDir)
            .filter { it.isRegularFile() && it.fileName.toString().startsWith("blocks-") }
            .anyMatch { filePath.replace("/", "_") in it.fileName.toString() }
    }

    // Get all files that have snapshots
    fun getTrackedFiles(): List<String> {
        if (!blocksDir.exists()) return emptyList()

        val files = mutableSetOf<String>()

        Files.walk(blocksDir)
            .filter { it.isRegularFile() && it.fileName.toString().startsWith("blocks-") }
            .forEach { snapshotFile -> 
                try {
                    val content = Files.readString(snapshotFile)
                    val snapshot = json.decodeFromString<BlockSnapshot>(content)
                    files.add(snapshot.filePath)
                }
                catch (e: Exception) {
                    // Skip corrupted snapshots
                }
            }

        return files.toList()
    }

    private fun getDatePath(timestamp: Instant): String {
        val date = java.time.LocalDateTime.ofInstant(timestamp, java.time.ZoneId.systemDefault())
        return "${date.year}/${date.monthValue.toString().padStart(2, '0')}/${date.dayOfMonth.toString().padStart(2, '0')}"
    }

    private fun getTimeString(timestamp: Instant): String {
        val time = java.time.LocalDateTime.ofInstant(timestamp, java.time.ZoneId.systemDefault())
        return "${time.hour.toString().padStart(2, '0')}-${time.minute.toString().padStart(2, '0')}-${time.second.toString().padStart(2, '0')}"
    }
}

@Serializable
data class BlockSnapshot(
    val filePath: String,
    val timestamp: String,
    val blocks: List<BlockState>,
    val frontMatter: FrontMatter?
)

@Serializable
data class BlockState(
    val id: String,
    val heading: String,
    val contentHash: String,
    val type: BlockType,
    val order: Int
)

@Serializable
data class BlockChange(
    val blockId: String,
    val type: BlockChangeType,
    val heading: String,
    val oldHash: String? = null,
    val newHash: String? = null
)

enum class BlockChangeType {
    ADDED, MODIFIED, DELETED
}
