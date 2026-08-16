package com.raival.compose.file.explorer.screen.terminal

import android.content.Context
import android.content.Intent

fun uninstallTerminal(context: Context) {
    runCatching {
        context.stopService(Intent(context, TerminalSessionService::class.java))
    }
    TerminalActivity.instance?.finish()

    runCatching {
        localBinDir(context).deleteRecursively()
        localLibDir(context).deleteRecursively()
        sandboxDir(context).deleteRecursively()
        localDir(context).child(".terminal_setup_ok_DO_NOT_REMOVE").delete()
        getTempDir(context).deleteRecursively()
    }
}

