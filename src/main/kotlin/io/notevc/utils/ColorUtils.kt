package io.notevc.utils

object ColorUtils {
    // ANSI color codes
    private const val RESET = "\u001B[0m"
    private const val BOLD = "\u001B[1m"
    private const val DIM = "\u001B[2m"

    // Colors
    private const val BLACK = "\u001B[30m"
    private const val RED = "\u001B[31m"
    private const val GREEN = "\u001B[32m"
    private const val YELLOW = "\u001B[33m"
    private const val BLUE = "\u001B[34m"
    private const val MAGENTA = "\u001B[35m"
    private const val CYAN = "\u001B[36m"
    private const val WHITE = "\u001B[37m"

    // Bright colors
    private const val BRIGHT_RED = "\u001B[91m"
    private const val BRIGHT_GREEN = "\u001B[92m"
    private const val BRIGHT_YELLOW = "\u001B[93m"
    private const val BRIGHT_BLUE = "\u001B[94m"
    private const val BRIGHT_MAGENTA = "\u001B[95m"
    private const val BRIGHT_CYAN = "\u001B[96m"

    // Flag to force disable colors via --no-color flag
    var forceDisableColors = false

    // Check if colors should be enabled (disable in CI/pipes or via flag)
    private val colorsEnabled: Boolean
        get() = !forceDisableColors &&
                System.getenv("NO_COLOR") == null && 
                System.getenv("CI") == null &&
                System.console() != null

    // Function to disable colors programmatically
    fun disableColors() {
        forceDisableColors = true
    }

    // Public color functions
    fun red(text: String): String = if (colorsEnabled) "$RED$text$RESET" else text
    fun green(text: String): String = if (colorsEnabled) "$GREEN$text$RESET" else text
    fun yellow(text: String): String = if (colorsEnabled) "$YELLOW$text$RESET" else text
    fun blue(text: String): String = if (colorsEnabled) "$BLUE$text$RESET" else text
    fun magenta(text: String): String = if (colorsEnabled) "$MAGENTA$text$RESET" else text
    fun cyan(text: String): String = if (colorsEnabled) "$CYAN$text$RESET" else text

    fun brightRed(text: String): String = if (colorsEnabled) "$BRIGHT_RED$text$RESET" else text
    fun brightGreen(text: String): String = if (colorsEnabled) "$BRIGHT_GREEN$text$RESET" else text
    fun brightYellow(text: String): String = if (colorsEnabled) "$BRIGHT_YELLOW$text$RESET" else text
    fun brightBlue(text: String): String = if (colorsEnabled) "$BRIGHT_BLUE$text$RESET" else text
    fun brightMagenta(text: String): String = if (colorsEnabled) "$BRIGHT_MAGENTA$text$RESET" else text
    fun brightCyan(text: String): String = if (colorsEnabled) "$BRIGHT_CYAN$text$RESET" else text

    fun bold(text: String): String = if (colorsEnabled) "$BOLD$text$RESET" else text
    fun dim(text: String): String = if (colorsEnabled) "$DIM$text$RESET" else text

    // Semantic colors for version control
    fun success(text: String): String = brightGreen(text)
    fun error(text: String): String = brightRed(text)
    fun warning(text: String): String = brightYellow(text)
    fun info(text: String): String = brightBlue(text)
    fun hash(text: String): String = yellow(text)
    fun filename(text: String): String = cyan(text)
    fun heading(text: String): String = brightMagenta(text)
    fun author(text: String): String = green(text)
    fun date(text: String): String = dim(text)

    // Status-specific colors
    fun added(text: String): String = brightGreen(text)
    fun modified(text: String): String = brightYellow(text)
    fun deleted(text: String): String = brightRed(text)
    fun untracked(text: String): String = red(text)
}

