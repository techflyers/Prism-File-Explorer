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
        val isInstalled = isTerminalInstalled(context)
        return@withContext if (!isInstalled && sandboxTar.exists()) {
            NEXT_STAGE.EXTRACTION
        } else {
            NEXT_STAGE.NONE
        }
    }

