package io.notevc.core

import kotlinx.serialization.Serializable

class BlockParser {
    // Parse markdown file into blocks based on headings
    fun parseFile(content: String, filePath: String): ParsedFile {
        val lines = content.lines()
        val blocks = mutableListOf<Block>()
        val frontMatter = extractFrontMatter(lines)

        var currentBlock: MutableList<String>? = null
        var currentHeading: String? = null
        var blockIndex = 0

        val contentLines = if (frontMatter != null) {
            lines.drop(frontMatter.endLine + 1)
        } else lines

        for (line in contentLines) {
            when {
                line.startsWith("#") -> {
                    // Save previous block if exists
                    if (currentBlock != null && currentHeading != null) {
                        blocks.add(Block(
                            id = generateBlockId(filePath, currentHeading, blockIndex),
                            heading = currentHeading,
                            content = currentBlock.joinToString("\n"),
                            type = BlockType.HEADING_SECTION,
                            order = blockIndex++
                        ))
                    }

                    // Start new block
                    currentHeading = line
                    currentBlock = mutableListOf(line)
                }
                else -> {
                    // Add to current block or create content-only block
                    if (currentBlock != null) currentBlock.add(line) 
                    else {
                        // Content before any heading
                        currentBlock = mutableListOf()
                        currentHeading = "<!-- Content -->"
                        currentBlock.add(line)
                    }
                }
            }
        }
        
        // Save final block
        if (currentBlock != null && currentHeading != null) {
            blocks.add(Block(
                id = generateBlockId(filePath, currentHeading, blockIndex),
                heading = currentHeading,
                content = currentBlock.joinToString("\n"),
                type = BlockType.HEADING_SECTION,
                order = blockIndex
            ))
        }

        return ParsedFile(
            path = filePath,
            frontMatter = frontMatter,
            blocks = blocks
        )
    }

    // Extract YAML front matter
    private fun extractFrontMatter(lines: List<String>): FrontMatter? {
        if (lines.isEmpty() || lines[0] != "---") return null

        val endIndex = lines.drop(1).indexOfFirst { it == "---"}
        if (endIndex == -1) return null

        val yamlLines = lines.subList(1, endIndex + 1)
        val properties = mutableMapOf<String, String>()
        var currentKey: String? = null
        val arrayValues = mutableListOf<String>()

        yamlLines.forEach { line ->
            when {
                // Handle array items (lines starting with -)
                line.trim().startsWith("- ") && currentKey != null -> {
                    val value = line.trim().substring(2).trim().removeSurrounding("\"")
                    arrayValues.add(value)
                }
                // Handle key-value pairs
                line.contains(":") -> {
                    // Save previous array if exists
                    if (currentKey != null && arrayValues.isNotEmpty()) {
                        properties[currentKey] = arrayValues.joinToString(", ")
                        arrayValues.clear()
                    }
                    
                    val colonIndex = line.indexOf(":")
                    val key = line.take(colonIndex).trim()
                    val value = line.substring(colonIndex + 1).trim().removeSurrounding("\"")
                    
                    if (value.isEmpty()) {
                        // This might be an array key
                        currentKey = key
                    } else {
                        properties[key] = value
                        currentKey = null
                    }
                }
            }
        }
        
        // Save final array if exists
        if (currentKey != null && arrayValues.isNotEmpty()) {
            properties[currentKey] = arrayValues.joinToString(", ")
        }

        return FrontMatter(
            properties = properties,
            endLine = endIndex + 1
        )
    }

    // Generate stable block id
    private fun generateBlockId(filePath: String, heading: String, order: Int): String {
        val cleanHeading = heading.replace(Regex("^#+\\s*"), "").trim()
        val baseId = "$filePath:$cleanHeading:$order"
        return io.notevc.utils.HashUtils.sha256(baseId).take(12)
    }
    
    // Reconstruct file from blocks
    fun reconstructFile(parsedFile: ParsedFile): String {
        val result = StringBuilder()

        // Add front matter if exists
        parsedFile.frontMatter?.let { fm -> 
            result.appendLine("---")
            fm.properties.forEach { (key, value) -> 
                // Handle tags as array
                if (key == "tags" && value.contains(",")) {
                    result.appendLine("$key:")
                    value.split(",").forEach { tag ->
                        result.appendLine("  - ${tag.trim()}")
                    }
                } else {
                    result.appendLine("$key: \"$value\"")
                }
            }
            result.appendLine("---")
            result.appendLine()
        }

        // Add blocks in order
        val sortedBlocks = parsedFile.blocks.sortedBy { it.order }
        sortedBlocks.forEachIndexed { index, block -> 
            result.append(block.content)
            // Add separator between blocks (not after last block)
            if (index < sortedBlocks.size - 1) {
                result.append("\n")
            }
        }

        return result.toString()
    }
}

@Serializable
data class ParsedFile(
    val path: String,
    val frontMatter: FrontMatter?,
    val blocks: List<Block>
)

@Serializable
data class Block(
    val id: String, // Stable block identifier
    val heading: String, // The heading text
    val content: String, // Full block content including heading
    val type: BlockType,
    val order: Int // Order within file
)

@Serializable
data class FrontMatter(
    val properties: Map<String, String>,
    val endLine: Int
) {
    // Default to true if not specified
    val isEnabled: Boolean get() = properties["enabled"]?.lowercase() != "false"
    val isAutomatic: Boolean get() = properties["automatic"]?.lowercase() == "true"
    val title: String? get() = properties["title"]
    val tags: List<String> get() = properties["tags"]?.split(",")?.map { it.trim() } ?: emptyList()
}

enum class BlockType {
    HEADING_SECTION, // # Heading with content
    CONTENT_ONLY // Content without heading
}
