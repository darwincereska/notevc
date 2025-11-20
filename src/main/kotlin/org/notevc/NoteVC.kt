package org.notevc

import org.notevc.core.Repository
import org.notevc.commands.*
import org.notevc.utils.ColorUtils
import org.kargs.*

fun main(args: Array<String>) {
    // Create argument parser
    val parser = Parser("notevc", ParserConfig(programVersion = Repository.VERSION))

    // Register subcommands
    parser.subcommands(
        StatusCommand(),
        InitCommand(),
        CommitCommand(),
        LogCommand(),
        DiffCommand(),
        ShowCommand(),
        RestoreCommand()
    )

    // Parse arguments
    try {
        parser.parse(args)
    } catch (e: Exception) {
        kotlin.system.exitProcess(1)
    }
}

