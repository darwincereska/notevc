package io.notevc

import io.notevc.core.Repository
import io.notevc.commands.*

fun main(args: Array<String>) {
    // Args logic
    when (args.firstOrNull()) {
        "init" -> {
            val initCommand = InitCommand()
            val result = initCommand.execute(args.getOrNull(1))

            result.fold(
                onSuccess = { message -> println(message) },
                onFailure = { error -> println("Error: ${error.message}") }
            )
        }

        "commit" -> {
            println("Not implemented yet")
        }

        "status" -> {
            println("Not implemented yet")
        }

        "version", "--version", "-v" -> {
            println("notevc version ${Repository.VERSION}")
        }
    }
}
