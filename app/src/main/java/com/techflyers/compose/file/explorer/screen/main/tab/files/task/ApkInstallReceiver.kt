package com.techflyers.compose.file.explorer.screen.main.tab.files.task

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.techflyers.compose.file.explorer.App.Companion.logger

/**
 * Receives status callbacks from [PackageInstaller.Session.commit] for split APK / APK bundle installations.
 *
 * Updates [ApkBundleInstaller]'s live status state and launches the confirmation [Intent] carried in
 * [Intent.EXTRA_INTENT] when [PackageInstaller.STATUS_PENDING_USER_ACTION] is received.
 */
class ApkInstallReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_COMMIT =
            "com.techflyers.compose.file.explorer.action.APK_INSTALL_COMMIT"
        const val EXTRA_BUNDLE_PATH = "extra_bundle_path"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        if (status == Int.MIN_VALUE) return

        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val bundlePath = intent.getStringExtra(EXTRA_BUNDLE_PATH)

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                ApkBundleInstaller.onUserConfirmationRequired(sessionId)

                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmIntent != null) {
                    try {
                        confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(confirmIntent)
                    } catch (e: Exception) {
                        logger.logError(e)
                        if (!bundlePath.isNullOrEmpty()) {
                            ApkBundleInstaller.fallbackMergeAndInstallAsync(context, bundlePath, e.message)
                        } else {
                            ApkBundleInstaller.onSessionFailed(sessionId, e.message)
                        }
                    }
                } else if (!bundlePath.isNullOrEmpty()) {
                    ApkBundleInstaller.fallbackMergeAndInstallAsync(context, bundlePath)
                } else {
                    ApkBundleInstaller.onSessionFailed(sessionId, "Missing confirmation intent")
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                ApkBundleInstaller.onSessionSuccess(sessionId)
            }

            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                ApkBundleInstaller.onSessionCancelled(sessionId)
            }

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                val rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val friendlyReason = formatInstallFailureReason(status, rawMessage)

                // If split session installation failed due to unsigned/incompatible splits,
                // automatically fall back to merging & signing the bundle into a universal APK.
                if (!bundlePath.isNullOrEmpty() &&
                    (status == PackageInstaller.STATUS_FAILURE ||
                        status == PackageInstaller.STATUS_FAILURE_INVALID ||
                        status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE)
                ) {
                    ApkBundleInstaller.fallbackMergeAndInstallAsync(context, bundlePath, friendlyReason)
                } else {
                    ApkBundleInstaller.onSessionFailed(sessionId, friendlyReason)
                }
            }
        }
    }

    private fun formatInstallFailureReason(status: Int, rawMessage: String?): String {
        val prefix = when (status) {
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation blocked by device policy or Play Protect"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflicting signature or package already installed with different key"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "Incompatible APK ABI or SDK version for this device"
            PackageInstaller.STATUS_FAILURE_INVALID -> "Invalid or corrupted split APK file"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Insufficient storage space to install package"
            else -> "Installation failed"
        }
        return if (!rawMessage.isNullOrBlank()) "$prefix ($rawMessage)" else prefix
    }
}
