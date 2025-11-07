package io.notevc

import io.notevc.core.Repository
import io.notevc.commands.*

fun main(args: Array<String>) {
    // Args logic
    when (args.firstOrNull()) {
        "init", "i" -> {
            val initCommand = InitCommand()
            val result = initCommand.execute(args.getOrNull(1))

            result.fold(
                onSuccess = { message -> println(message) },
                onFailure = { error -> println("Error: ${error.message}") }
            )
        }

        "commit" -> {
            val commitArgs = args.drop(1)
            val commitCommand = CommitCommand()
            val result = commitCommand.execute(commitArgs)

            result.fold(
                onSuccess = { output -> println(output) },
                onFailure = { error -> println("Error: ${error.message}") }
            )
        }

        "status", "st" -> {
            val statusCommand = StatusCommand()
            val result = statusCommand.execute()

            result.fold(
                onSuccess = { output -> println(output) },
                onFailure = { error -> println("Error: ${error.message}") }
            )
        }

        "version", "--version", "-v" -> {
            println("notevc version ${Repository.VERSION}")
        }
        
        else -> {
            println("Usage: notevc init|commit|status|version")
        }
    }
}
