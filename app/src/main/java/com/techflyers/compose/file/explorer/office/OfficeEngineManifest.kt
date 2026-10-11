package com.techflyers.compose.file.explorer.office

import android.os.Build
import android.os.Process

/**
 * Pinned description of the optional Office engine (powered by LibreOffice).
 *
 * Source: https://github.com/vasuki-re/LibreOffice-Lite release v2.0, a LibreOffice core build
 * (MPL-2.0). Values verified against GitHub release API digests and SHA-256 hashes.
 */
object OfficeEngineManifest {

    const val VERSION = "lo-lite-2.0"
    const val DISPLAY_VERSION = "2.0"

    /** Rough install footprint. */
    const val APPROX_DOWNLOAD_BYTES = 47_521_152L

    data class Bundle(val abi: String, val url: String, val sizeBytes: Long, val sha256: String)

    val bundles: List<Bundle> = listOf(
        Bundle(
            abi = "arm64-v8a",
            url = "https://github.com/vasuki-re/LibreOffice-Lite/releases/download/v2.0/LibreOffice-arm64.tar.xz",
            sizeBytes = 47_521_152L,
            sha256 = "6665ad47db41d116248c40e6fc608a3425b9eb4a304f5ad5e5d5c80dbea61e30"
        ),
        Bundle(
            abi = "armeabi-v7a",
            url = "https://github.com/vasuki-re/LibreOffice-Lite/releases/download/v2.0/LibreOffice-arm.tar.xz",
            sizeBytes = 46_666_612L,
            sha256 = "7546e244f66c8c1b5a5a4ede7cb26c8f7e767879bbf3b39d405a5d8fa148b045"
        )
    )

    const val RELOCATE_FROM: String = "/data/data/vasuki.istanpdf/files"

    fun processAbi(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Process.is64Bit()) {
        Build.SUPPORTED_64_BIT_ABIS.firstOrNull()
    } else {
        Build.SUPPORTED_32_BIT_ABIS.firstOrNull()
    }

    fun bundleForDevice(): Bundle? = processAbi()?.let { abi -> bundles.firstOrNull { it.abi == abi } }
}
