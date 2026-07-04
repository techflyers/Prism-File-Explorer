package com.raival.compose.file.explorer.screen.main.tab.nfile_tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.screen.main.tab.Tab
import com.raival.compose.file.explorer.screen.main.tab.files.service.remote.NetworkConnectionModel

class VaultTab : Tab() {
    override val id = globalClass.generateUid()
    override val header = "Vault"
    var isPinVerified by mutableStateOf(false)
    var activePassword by mutableStateOf("")

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = "Private Wallet"
    override suspend fun getSubtitle() = if (isPinVerified) "Secure Sandbox" else "Locked"
}

class FtpServerTab : Tab() {
    override val id = globalClass.generateUid()
    override val header = "FTP Server"

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = "FTP Server"
    override suspend fun getSubtitle() = "Local File Server"
}

class WebSharingTab : Tab() {
    override val id = globalClass.generateUid()
    override val header = "Web Share"

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = "Web Sharing"
    override suspend fun getSubtitle() = "Share over Wi-Fi/Internet"
}

class NetworkConnectionWizardTab : Tab() {
    override val id = globalClass.generateUid()
    override val header = "Add Connection"

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = "Add Connection"
    override suspend fun getSubtitle() = "Remote server connection wizard"
}

class RemoteExplorerTab(val connection: NetworkConnectionModel) : Tab() {
    override val id = globalClass.generateUid()
    override val header = connection.name

    override fun onTabStarted() {
        super.onTabStarted()
        requestHomeToolbarUpdate()
    }

    override fun onTabResumed() {
        super.onTabResumed()
        requestHomeToolbarUpdate()
    }

    override suspend fun getTitle() = connection.name
    override suspend fun getSubtitle() = connection.host
}
