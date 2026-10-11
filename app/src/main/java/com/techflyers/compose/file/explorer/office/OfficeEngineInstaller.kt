package com.techflyers.compose.file.explorer.office

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

sealed interface OfficeEngineState {
    data object Unsupported : OfficeEngineState

    data object NotInstalled : OfficeEngineState

    data class Downloading(val downloaded: Long, val total: Long, val waitingForNetwork: Boolean = false) : OfficeEngineState

    data object Installing : OfficeEngineState

    data object NeedsConfirmation : OfficeEngineState

    data class Installed(val version: String, val sizeBytes: Long, val updateAvailable: Boolean) : OfficeEngineState

    data class Failed(val message: String) : OfficeEngineState
}

interface OfficeEngineInstaller {
    val state: StateFlow<OfficeEngineState>

    val usesNotification: Boolean

    fun install()

    fun cancel()

    fun uninstall()

    fun refresh()

    fun confirm(activity: Activity) {}
}
