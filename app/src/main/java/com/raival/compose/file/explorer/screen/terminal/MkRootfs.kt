package com.raival.compose.file.explorer.screen.terminal

import android.content.Context
import com.raival.compose.file.explorer.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class NEXT_STAGE { NONE, EXTRACTION }

suspend fun getNextStage(context: Context = App.appContext): NEXT_STAGE =
    withContext(Dispatchers.IO) {
        val sandboxTar = File(getTempDir(context), "sandbox.tar.gz")
        val rootfsFiles = sandboxDir(context).listFiles()?.filter {
            it.absolutePath != sandboxHomeDir(context).absolutePath &&
                it.absolutePath != File(sandboxDir(context), "tmp").absolutePath
        } ?: emptyList()

        return@withContext if (!sandboxTar.exists() || rootfsFiles.isNotEmpty() || isTerminalInstalled(context)) {
            NEXT_STAGE.NONE
        } else {
            NEXT_STAGE.EXTRACTION
        }
    }

