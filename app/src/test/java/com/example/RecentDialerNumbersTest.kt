package com.example

import com.example.data.local.CallLogEntity
import com.example.ui.viewmodel.recentDialerNumbers
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentDialerNumbersTest {
    private val now = 10_000_000_000L
    private fun call(n: String, ts: Long, name: String? = null, dir: String = "OUTGOING") = CallLogEntity(
        phoneNumber = n, normalizedPhone = n, direction = dir, durationSeconds = 1,
        timestampMs = ts, matchedName = name
    )

    @Test fun dedupesByNumberKeepingNewestAndNamesKnownCallers() {
        val out = recentDialerNumbers(
            listOf(call("111", now - 60_000, "Asha"), call("222", now - 3_600_000), call("111", now - 9_000_000)),
            now
        )
        assertEquals(listOf("Asha", "222"), out.map { it.displayName })
        assertEquals("Outgoing · 1 min ago", out[0].subtitle)
    }

    @Test fun capsAtLimit() {
        val calls = (1..10).map { call("$it$it", now - it * 1000L) }
        assertEquals(6, recentDialerNumbers(calls, now).size)
    }

    @Test fun missedCallsAreLabelled() {
        assertEquals("Missed · just now", recentDialerNumbers(listOf(call("5", now, dir = "MISSED")), now)[0].subtitle)
    }
}
