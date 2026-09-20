package com.techflyers.compose.file.explorer.screen.main.tab.files.search.ai

import android.content.Context
import com.google.firebase.components.ComponentRegistrar
import com.google.mlkit.common.internal.CommonComponentRegistrar
import com.google.mlkit.common.sdkinternal.MlKitContext
import com.google.mlkit.vision.common.internal.VisionCommonRegistrar
import com.google.mlkit.vision.text.internal.TextRegistrar

/**
 * Initializes Google ML Kit programmatically without requiring manifest declarations.
 *
 * Both [com.google.mlkit.common.internal.MlKitInitProvider] and
 * [com.google.mlkit.common.internal.MlKitComponentDiscoveryService] have been removed
 * from AndroidManifest.xml via `tools:node="remove"` to prevent tracker warnings in
 * privacy audits (such as Exodus Privacy).
 *
 * This initializer explicitly supplies the required [ComponentRegistrar] instances
 * directly to [MlKitContext.initializeIfNeeded], allowing on-device OCR and AI Semantic
 * Search to function offline without manifest services or content providers.
 */
object MlKitInitializer {
    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                val registrars = listOf<ComponentRegistrar>(
                    CommonComponentRegistrar(),
                    VisionCommonRegistrar(),
                    TextRegistrar()
                )
                MlKitContext.initializeIfNeeded(context.applicationContext, registrars)
                initialized = true
            } catch (e: Exception) {
                // If already initialized or failed, prevent crashes
            }
        }
    }
}
