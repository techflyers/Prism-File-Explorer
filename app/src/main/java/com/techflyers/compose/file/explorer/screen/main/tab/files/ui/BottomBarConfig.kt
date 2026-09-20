package com.techflyers.compose.file.explorer.screen.main.tab.files.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.ui.graphics.vector.ImageVector
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.techflyers.compose.file.explorer.R

data class BottomBarActionConfig(
    val id: String,
    val isEnabled: Boolean = true
)

enum class BottomBarSelectionAction(
    val id: String,
    val labelResId: Int,
    val icon: ImageVector
) {
    SELECT_ALL("select_all", R.string.select_all, Icons.Rounded.SelectAll),
    DELETE("delete", R.string.delete, Icons.Rounded.Delete),
    CUT("cut", R.string.cut, Icons.Rounded.ContentCut),
    COPY("copy", R.string.copy, Icons.Rounded.FileCopy),
    RENAME("rename", R.string.rename, Icons.Rounded.FormatColorText),
    SHARE("share", R.string.share, Icons.Rounded.Share),
    OPEN_WITH("open_with", R.string.open_with, Icons.AutoMirrored.Rounded.OpenInNew),
    PROPERTIES("properties", R.string.file_properties, Icons.Rounded.Info),
    MORE("more", R.string.options, Icons.Rounded.MoreHoriz);

    companion object {
        fun fromId(id: String): BottomBarSelectionAction? = entries.find { it.id == id }

        fun defaultConfigs(): List<BottomBarActionConfig> = listOf(
            BottomBarActionConfig(SELECT_ALL.id, true),
            BottomBarActionConfig(DELETE.id, true),
            BottomBarActionConfig(CUT.id, true),
            BottomBarActionConfig(COPY.id, true),
            BottomBarActionConfig(RENAME.id, true),
            BottomBarActionConfig(SHARE.id, true),
            BottomBarActionConfig(OPEN_WITH.id, true),
            BottomBarActionConfig(PROPERTIES.id, true),
            BottomBarActionConfig(MORE.id, true)
        )
    }
}

object BottomBarConfigUtils {
    private val gson = Gson()
    private val listType = object : TypeToken<List<BottomBarActionConfig>>() {}.type

    fun parseSelectionActions(json: String): List<BottomBarActionConfig> {
        return try {
            val parsed: List<BottomBarActionConfig>? = gson.fromJson(json, listType)
            if (parsed.isNullOrEmpty()) {
                BottomBarSelectionAction.defaultConfigs()
            } else {
                val existingIds = parsed.map { it.id }.toSet()
                val missing = BottomBarSelectionAction.defaultConfigs().filter { it.id !in existingIds }
                parsed + missing
            }
        } catch (_: Exception) {
            BottomBarSelectionAction.defaultConfigs()
        }
    }

    fun serialize(configs: List<BottomBarActionConfig>): String {
        return gson.toJson(configs)
    }
}
