package com.raival.compose.file.explorer.screen.terminal

/** Singleton holding the next command to run when the terminal opens. */
var pendingTerminalCommand: TerminalCommand? = null

/**
 * Describes a command to be executed in the terminal.
 *
 * @param sandbox  Whether to run inside the proot Ubuntu sandbox (true) or as a raw /system/bin/sh session (false).
 * @param exe      The executable path.
 * @param args     Arguments to pass to [exe].
 * @param id       A unique session identifier (shown in the session drawer).
 * @param workingDir  Directory to cd into on start (null = use sandbox home / host home).
 * @param env      Additional environment variables as "KEY=value" strings.
 */
data class TerminalCommand(
    val sandbox: Boolean = true,
    val exe: String = "/system/bin/sh",
    val args: Array<String> = arrayOf(),
    val id: String,
    val workingDir: String? = null,
    val env: Array<String> = arrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (javaClass != other?.javaClass) return false
        other as TerminalCommand
        if (exe != other.exe) return false
        if (!args.contentEquals(other.args)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = exe.hashCode()
        result = 31 * result + args.contentHashCode()
        return result
    }
}
