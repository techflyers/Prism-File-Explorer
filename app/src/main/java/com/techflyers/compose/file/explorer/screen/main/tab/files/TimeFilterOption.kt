package com.techflyers.compose.file.explorer.screen.main.tab.files

import java.util.Calendar

enum class TimeFilterOption(val label: String) {
    ALL("All time"),
    LAST_HOUR("Last hour"),
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    THIS_YEAR("This year");

    fun matches(lastModifiedMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (this == ALL) return true
        if (lastModifiedMillis <= 0L) return false
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = cal.timeInMillis
        return when (this) {
            ALL -> true
            LAST_HOUR -> lastModifiedMillis >= (nowMillis - 3_600_000L)
            TODAY -> lastModifiedMillis >= startOfToday
            YESTERDAY -> {
                val startOfYesterday = startOfToday - 86_400_000L
                lastModifiedMillis in startOfYesterday until startOfToday
            }
            LAST_7_DAYS -> lastModifiedMillis >= (startOfToday - 6L * 86_400_000L)
            LAST_30_DAYS -> lastModifiedMillis >= (startOfToday - 29L * 86_400_000L)
            THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                lastModifiedMillis >= cal.timeInMillis
            }
        }
    }
}
