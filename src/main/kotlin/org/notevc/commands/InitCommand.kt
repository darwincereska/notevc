package org.notevc.commands

import org.notevc.core.Repository
import java.nio.file.Path
import org.notevc.utils.ColorUtils

class InitCommand {
    fun execute(path: String?): Result<String> {
        return try {
            val repo = if (path != null) {
                Repository.at(path).getOrElse { return Result.failure(it) }
            } else Repository.current()

            repo.init().fold(
                onSuccess = {
                    val absolutePath = repo.path.toAbsolutePath().toString()
                    Result.success("${ColorUtils.success("Initialized notevc repository")} in ${ColorUtils.filename(repo.path.toAbsolutePath().toString())}")
                },
                onFailure = {
                    error -> Result.failure(error)
                }
            )
        }
        catch (e: Exception) {
            Result.failure(e)
        }
    }
}
