package com.raival.compose.file.explorer.screen.main.startup

import com.raival.compose.file.explorer.common.emptyString
import java.util.UUID

enum class PlusButtonOverride {
    DEFAULT, // First startup tab in order
    HOME,
    APPS,
    FILES,
    CUSTOM_FOLDER
}

data class StartupTabs(
    val tabs: List<StartupTab>,
    // Nullable for backward compatibility with legacy JSON that may contain null.
    val plusButtonOverride: PlusButtonOverride? = PlusButtonOverride.DEFAULT,
    val plusButtonCustomPath: String = emptyString
) {
    companion object {
        fun default() = StartupTabs(arrayListOf(StartupTab(StartupTabType.HOME)))
    }
}

data class StartupTab(
    val type: StartupTabType,
    val extra: String = emptyString,
    var id: UUID = UUID.randomUUID()
)

enum class StartupTabType {
    HOME,
    APPS,
    FILES
}