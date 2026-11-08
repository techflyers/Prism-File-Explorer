package com.techflyers.compose.file.explorer.screen.preferences.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.showMsg
import com.techflyers.compose.file.explorer.screen.main.ui.AppInfoDialog
import com.techflyers.compose.file.explorer.screen.preferences.misc.exportPreferences
import com.techflyers.compose.file.explorer.screen.preferences.misc.importPreferences
import kotlinx.coroutines.launch

@Composable
fun AppInfoContainer() {
    var showAppInfoDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val data = exportPreferences()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(data.toByteArray())
                    }
                    showMsg(globalClass.getString(R.string.exported_prism_preferences))
                } catch (_: Exception) {
                    showMsg("Failed to export preferences")
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.reader().readText()
                    }
                    if (!text.isNullOrEmpty()) {
                        importPreferences(text)
                        showMsg(globalClass.getString(R.string.preferences_imported_successfully))
                    } else {
                        showMsg(globalClass.getString(R.string.invalid_preferences_file))
                    }
                } catch (_: Exception) {
                    showMsg(globalClass.getString(R.string.invalid_preferences_file))
                }
            }
        }
    }

    if (showAppInfoDialog) {
        AppInfoDialog(
            show = true,
            onDismiss = { showAppInfoDialog = false },
            hasNewUpdate = globalClass.mainActivityManager.newUpdate != null
        )
    }

    Container(
        title = stringResource(R.string.other),
        icon = Icons.Rounded.SettingsBackupRestore
    ) {
        PreferenceItem(
            label = stringResource(R.string.export_preferences),
            supportingText = "Save all Prism settings and filter configurations to a backup file",
            icon = Icons.Rounded.Upload,
            onClick = {
                createDocumentLauncher.launch("preferences.prismPrefs")
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.import_preferences),
            supportingText = "Restore Prism settings and filter configurations from a backup file",
            icon = Icons.Rounded.Download,
            onClick = {
                openDocumentLauncher.launch(arrayOf("*/*"))
            }
        )

        PreferenceDivider()

        PreferenceItem(
            label = stringResource(R.string.about),
            supportingText = "View version details, open-source licenses, and check for app updates",
            icon = Icons.Rounded.Info,
            onClick = {
                showAppInfoDialog = true
            }
        )
    }
}
