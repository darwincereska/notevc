package io.notevc.commands

import io.notevc.core.Repository
import java.nio.file.Path

class InitCommand {
    fun execute(path: String?): Result<String> {
        return try {
            val repo = if (path != null) {
                Repository.at(path).getOrElse { return Result.failure(it) }
            } else Repository.current()

            repo.init().fold(
                onSuccess = {
                    Result.success("Initialized notevc repository in ${repo.path.toAbsolutePath()}")
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
