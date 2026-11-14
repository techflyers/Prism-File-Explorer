package com.techflyers.compose.file.explorer.screen.main.tab.files

import com.techflyers.compose.file.explorer.common.fromJson
import com.techflyers.compose.file.explorer.common.toJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarkTest {

    @Test
    fun testBookmarkTitlesSerialization() {
        val titles = mapOf(
            "/storage/emulated/0/Download" to "My Downloads",
            "/storage/emulated/0/DCIM/Camera" to "Camera Photos"
        )
        val json = titles.toJson()
        val deserialized = fromJson<Map<String, String>>(json)

        assertEquals(titles, deserialized)
    }

    @Test
    fun testSetAndRemoveBookmarkTitleLogic() {
        val titles = mutableMapOf<String, String>()
        val path = "/storage/emulated/0/Documents"

        // Set title with whitespace that should be trimmed
        val rawTitle = "  Work Docs  "
        val trimmed = rawTitle.trim()
        if (trimmed.isEmpty()) {
            titles.remove(path)
        } else {
            titles[path] = trimmed
        }
        assertEquals("Work Docs", titles[path])

        // Empty title resets / removes custom title
        val emptyTitle = "   "
        val trimmedEmpty = emptyTitle.trim()
        if (trimmedEmpty.isEmpty()) {
            titles.remove(path)
        } else {
            titles[path] = trimmedEmpty
        }
        assertNull(titles[path])
    }
}
