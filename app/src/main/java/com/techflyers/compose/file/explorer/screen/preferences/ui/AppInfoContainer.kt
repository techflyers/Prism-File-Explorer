package com.techflyers.compose.file.explorer.screen.preferences.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.techflyers.compose.file.explorer.App.Companion.globalClass
import com.techflyers.compose.file.explorer.R
import com.techflyers.compose.file.explorer.common.emptyString
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
                } catch (e: Exception) {
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
                } catch (e: Exception) {
                    showMsg(globalClass.getString(R.string.invalid_preferences_file))
                }
            }
        }
    }

    AppInfoDialog(
        show = showAppInfoDialog,
        onDismiss = { showAppInfoDialog = false },
        hasNewUpdate = globalClass.mainActivityManager.newUpdate != null
    )

    Container(title = stringResource(R.string.other)) {
        PreferenceItem(
            label = stringResource(R.string.export_preferences),
            supportingText = "Export settings to any custom directory",
            icon = Icons.Rounded.Upload,
            onClick = {
                createDocumentLauncher.launch("preferences.prismPrefs")
            }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.import_preferences),
            supportingText = "Restore settings from a custom file location",
            icon = Icons.Rounded.Download,
            onClick = {
                openDocumentLauncher.launch(arrayOf("*/*"))
            }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = stringResource(R.string.about),
            supportingText = emptyString,
            icon = Icons.Rounded.Info,
            onClick = {
                showAppInfoDialog = true
            }
        )
    }
}