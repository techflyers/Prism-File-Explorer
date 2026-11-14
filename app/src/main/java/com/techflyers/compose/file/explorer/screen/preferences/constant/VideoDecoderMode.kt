package com.techflyers.compose.file.explorer.screen.preferences.constant

enum class VideoDecoderMode {
    AUTO,
    SOFTWARE,
    HARDWARE;

    companion object {
        fun fromOrdinal(ordinal: Int): VideoDecoderMode {
            return entries.getOrElse(ordinal) { AUTO }
        }
    }
}
