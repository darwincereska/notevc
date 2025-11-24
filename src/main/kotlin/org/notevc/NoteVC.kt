package org.notevc

import org.notevc.core.Repository
import org.notevc.commands.*
import org.kargs.*

fun main(args: Array<String>) {
    val argsList = args.toMutableList()

    // Create argument parser
    val parser = Parser("notevc", ParserConfig(programVersion = Repository.VERSION, colorsEnabled = true))

    if (argsList.remove("--no-color")) {
        Colors.setGlobalColorsEnabled(false)
    } else Colors.setGlobalColorsEnabled(true)

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
        parser.parse(argsList.toTypedArray())
    } catch (e: Exception) {
        kotlin.system.exitProcess(1)
    }
}

