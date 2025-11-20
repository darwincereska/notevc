package org.notevc.commands

import org.notevc.core.Repository
import java.nio.file.Path
import org.notevc.utils.ColorUtils
import org.kargs.Subcommand
import org.kargs.Argument
import org.kargs.ArgType

class InitCommand : Subcommand("init", description = "Initialize a repository", aliases = listOf("i")) {
    val path by Argument(ArgType.existingDirectory(), "path", description = "Initialize in a specified directory", required = false)

    override fun execute() {
        val result: Result<String> = runCatching {
            val repo = if (path != null) {
                Repository.at(path!!.toString()).getOrElse { throw Exception(it) }
            } else Repository.current()

            repo.init().fold(
                onSuccess = {
                    val absolutePath = repo.path.toAbsolutePath().toString()
                    "Initialized notevc repository in ${ColorUtils.filename(absolutePath)}"
                },
                onFailure = { error -> throw Exception(error) }
            )
        }

        result.onSuccess { message -> println(message) }
        result.onFailure { error -> println("${ColorUtils.error("Error:")} ${error.message}") }
    }
}
