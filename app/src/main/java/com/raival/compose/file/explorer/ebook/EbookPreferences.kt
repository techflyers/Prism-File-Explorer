package com.raival.compose.file.explorer.ebook

import android.content.Context
import com.raival.compose.file.explorer.epub.EpubTheme
import org.json.JSONArray
import org.json.JSONObject

object EbookPreferences {

    private const val PREFS_NAME = "ebook_preferences"

    private const val KEY_THEME = "reader_theme"
    private const val KEY_FONT_SIZE = "reader_font_size"
    private const val KEY_FONT_FAMILY = "reader_font_family"
    private const val KEY_LINE_HEIGHT = "reader_line_height"
    private const val KEY_IS_PAGED = "reader_is_paged"
    private const val KEY_MARGIN = "reader_margin"
    private const val KEY_TEXT_ALIGN = "reader_text_align"

    private const val PREFIX_POS_CHAPTER = "pos_ch_"
    private const val PREFIX_POS_SCROLL = "pos_scroll_"
    private const val PREFIX_BOOKMARKS = "bookmarks_"

    fun getTheme(context: Context): EpubTheme {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_THEME, EpubTheme.LIGHT.id) ?: EpubTheme.LIGHT.id
        return EpubTheme.entries.firstOrNull { it.id == id } ?: EpubTheme.LIGHT
    }

    fun saveTheme(context: Context, theme: EpubTheme) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, theme.id)
            .apply()
    }

    fun getFontSizePercent(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_FONT_SIZE, 115)
    }

    fun saveFontSizePercent(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_FONT_SIZE, percent.coerceIn(70, 250))
            .apply()
    }

    fun getFontFamily(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_FONT_FAMILY, "sans-serif") ?: "sans-serif"
    }

    fun saveFontFamily(context: Context, fontFamily: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FONT_FAMILY, fontFamily)
            .apply()
    }

    fun getLineHeight(context: Context): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_LINE_HEIGHT, 1.65f)
    }

    fun saveLineHeight(context: Context, lineHeight: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_LINE_HEIGHT, lineHeight)
            .apply()
    }

    fun isPaged(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_IS_PAGED, false)
    }

    fun savePaged(context: Context, isPaged: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_PAGED, isPaged)
            .apply()
    }

    fun getMarginHorizontal(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_MARGIN, 20)
    }

    fun saveMarginHorizontal(context: Context, margin: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MARGIN, margin)
            .apply()
    }

    fun getTextAlign(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_TEXT_ALIGN, "justify") ?: "justify"
    }

    fun saveTextAlign(context: Context, align: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEXT_ALIGN, align)
            .apply()
    }

    fun getReadingPosition(context: Context, bookKey: String): Pair<Int, Float> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ch = prefs.getInt(PREFIX_POS_CHAPTER + bookKey, 0)
        val scroll = prefs.getFloat(PREFIX_POS_SCROLL + bookKey, 0f)
        return Pair(ch, scroll)
    }

    fun saveReadingPosition(context: Context, bookKey: String, chapterIndex: Int, scrollPercentage: Float) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREFIX_POS_CHAPTER + bookKey, chapterIndex)
            .putFloat(PREFIX_POS_SCROLL + bookKey, scrollPercentage.coerceIn(0f, 1f))
            .apply()
    }

    fun getBookmarks(context: Context, bookKey: String): List<EbookBookmark> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(PREFIX_BOOKMARKS + bookKey, null) ?: return emptyList()
        val list = mutableListOf<EbookBookmark>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    EbookBookmark(
                        chapterIndex = obj.optInt("chapterIndex", 0),
                        scrollPercentage = obj.optDouble("scrollPercentage", 0.0).toFloat(),
                        title = obj.optString("title", "Bookmark"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun addBookmark(context: Context, bookKey: String, bookmark: EbookBookmark) {
        val existing = getBookmarks(context, bookKey).toMutableList()
        // Avoid identical duplicates
        existing.removeAll { it.chapterIndex == bookmark.chapterIndex && kotlin.math.abs(it.scrollPercentage - bookmark.scrollPercentage) < 0.02f }
        existing.add(0, bookmark)
        saveBookmarksList(context, bookKey, existing)
    }

    fun removeBookmark(context: Context, bookKey: String, bookmark: EbookBookmark) {
        val existing = getBookmarks(context, bookKey).toMutableList()
        existing.removeAll { it.chapterIndex == bookmark.chapterIndex && it.timestamp == bookmark.timestamp }
        saveBookmarksList(context, bookKey, existing)
    }

    private fun saveBookmarksList(context: Context, bookKey: String, bookmarks: List<EbookBookmark>) {
        val arr = JSONArray()
        for (b in bookmarks) {
            val obj = JSONObject().apply {
                put("chapterIndex", b.chapterIndex)
                put("scrollPercentage", b.scrollPercentage.toDouble())
                put("title", b.title)
                put("timestamp", b.timestamp)
            }
            arr.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFIX_BOOKMARKS + bookKey, arr.toString())
            .apply()
    }
}
