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

        yamlLines.forEach { line ->
            val colonIndex = line.indexOf(":")
            if (colonIndex != -1) {
                val key = line.take(colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim().removeSurrounding("\"")
                properties[key] = value
            }
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
                result.appendLine("$key: \"$value\"")
            }
            result.appendLine("---")
            result.appendLine()
        }

        // Add blocks in order
        parsedFile.blocks.sortedBy { it.order }.forEach { block -> 
            result.appendLine(block.content)
            if (block != parsedFile.blocks.last()) {
                result.appendLine()
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
    val isEnabled: Boolean get() = properties["enabled"]?.lowercase() == "true"
    val isAutomatic: Boolean get() = properties["automatic"]?.lowercase() == "true"
}

enum class BlockType {
    HEADING_SECTION, // # Heading with content
    CONTENT_ONLY // Content without heading
}
