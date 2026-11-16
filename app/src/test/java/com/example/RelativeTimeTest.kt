package com.example

import com.example.util.RelativeTime
import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeTest {
    private val now = 1_000_000_000L
    @Test fun never() = assertEquals("never", RelativeTime.format(now, 0))
    @Test fun justNow() = assertEquals("just now", RelativeTime.format(now, now - 30_000))
    @Test fun minutes() = assertEquals("5 min ago", RelativeTime.format(now, now - 5 * 60_000))
    @Test fun hours() = assertEquals("3 h ago", RelativeTime.format(now, now - 3 * 3_600_000L))
    @Test fun days() = assertEquals("2 d ago", RelativeTime.format(now, now - 2 * 86_400_000L))
    @Test fun clockSkewNeverNegative() = assertEquals("just now", RelativeTime.format(now, now + 60_000))
}
