package com.raival.compose.file.explorer.screen.preferences.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raival.compose.file.explorer.App.Companion.globalClass
import com.raival.compose.file.explorer.R
import com.raival.compose.file.explorer.common.toFormattedSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FileOperationContainer() {
    val preferences = globalClass.preferencesManager
    val scope = rememberCoroutineScope()

    Container(title = stringResource(R.string.file_operation)) {
        PreferenceItem(
            label = stringResource(R.string.auto_sign_merged_apk_bundle_files),
            supportingText = stringResource(R.string.auto_sign_merged_apk_bundle_files_description),
            icon = Icons.Rounded.Key,
            switchState = preferences.signMergedApkBundleFiles,
            onSwitchChange = { preferences.signMergedApkBundleFiles = it }
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            thickness = 3.dp
        )

        PreferenceItem(
            label = "Clear Temporary Files",
            supportingText = "Delete cached and temporary files created by Prism",
            icon = Icons.Rounded.DeleteSweep,
            onClick = {
                scope.launch {
                    val freed = withContext(Dispatchers.IO) {
                        preferences.clearTemporaryFiles()
                    }
                    globalClass.showMsg("Cleared ${freed.toFormattedSize()} of temporary files")
                }
            }
        )
    }
}