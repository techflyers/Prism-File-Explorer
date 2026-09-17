package com.techflyers.compose.file.explorer.screen.logs

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.techflyers.compose.file.explorer.base.BaseActivity
import com.techflyers.compose.file.explorer.screen.logs.ui.LogsScreen
import com.techflyers.compose.file.explorer.theme.FileExplorerTheme

class LogsActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkPermissions()
    }

    override fun onPermissionGranted() {
        setContent {
            FileExplorerTheme {
                LogsScreen { onBackPressedDispatcher.onBackPressed() }
            }
        }
    }
}