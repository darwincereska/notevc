package io.notevc

import io.notevc.core.Repository
import io.notevc.commands.*
import io.notevc.utils.ColorUtils

fun main(args: Array<String>) {
    // Parse global flags first (like --no-color)
    val filteredArgs = args.filter { arg ->
        when (arg) {
            "--no-color" -> {
                ColorUtils.disableColors()
                false  // Remove this arg from the list
            }
            else -> true  // Keep this arg
        }
    }.toTypedArray()

    // Args logic
    when (filteredArgs.firstOrNull()) {
        "init", "i" -> {
            val initCommand = InitCommand()
            val result = initCommand.execute(filteredArgs.getOrNull(1))

            result.fold(
                onSuccess = { message -> println(message) },
                onFailure = { error -> println("${ColorUtils.error("Error:")} ${error.message}") }
            )
        }

        "log" -> {
            val logArgs = filteredArgs.drop(1)
            val logCommand = LogCommand()
            val result = logCommand.execute(logArgs)

            result.fold(
                onSuccess = { output -> println(output) },
                onFailure = { error -> println("${ColorUtils.error("Error:")} ${error.message}") }
            )
        }

        "commit" -> {
            val commitArgs = filteredArgs.drop(1)
            val commitCommand = CommitCommand()
            val result = commitCommand.execute(commitArgs)

            result.fold(
                onSuccess = { output -> println(output) },
                onFailure = { error -> println("${ColorUtils.error("Error:")} ${error.message}") }
            )
        }

        "status", "st" -> {
            val statusCommand = StatusCommand()
            val result = statusCommand.execute()

            result.fold(
                onSuccess = { output -> println(output) },
                onFailure = { error -> println("${ColorUtils.error("Error:")} ${error.message}") }
            )
        }

        "version", "--version", "-v" -> {
            println("notevc version ${Repository.VERSION}")
        }

        "restore" -> {
            val restoreArgs = filteredArgs.drop(1)
            val restoreCommand = RestoreCommand()
            val result = restoreCommand.execute(restoreArgs)

            result.fold(
                onSuccess = { output -> println(output) },
                onFailure = { error -> println("${ColorUtils.error("Error:")} ${error.message}") }
            )
        }

        "diff" -> {
            val diffArgs = filteredArgs.drop(1)
            val diffCommand = DiffCommand()
            val result = diffCommand.execute(diffArgs)

            result.fold(
                onSuccess = { output -> println(output) },
                onFailure = { error -> println("${ColorUtils.error("Error:")} ${error.message}") }
            )
        }

        "show" -> {
            val showArgs = filteredArgs.drop(1)
            val showCommand = ShowCommand()
            val result = showCommand.execute(showArgs)

            result.fold(
                onSuccess = { output -> println(output) },
                onFailure = { error -> println("${ColorUtils.error("Error:")} ${error.message}") }
            )
        }
        
        else -> {
            println("Usage: notevc [--no-color] init|commit|status|log|restore|diff|show|version")
        }
    }
}
