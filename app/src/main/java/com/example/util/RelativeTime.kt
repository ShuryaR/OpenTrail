package com.example.util

object RelativeTime {
    /** "just now", "5 min ago", "3 h ago", "2 d ago", or "never" when [thenMs] is 0. */
    fun format(nowMs: Long, thenMs: Long): String {
        if (thenMs <= 0L) return "never"
        val minutes = ((nowMs - thenMs).coerceAtLeast(0L)) / 60_000L
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "$minutes min ago"
            minutes < 60 * 24 -> "${minutes / 60} h ago"
            else -> "${minutes / (60 * 24)} d ago"
        }
    }
}
