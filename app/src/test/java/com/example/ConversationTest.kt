package com.example

import com.example.data.local.AppSettings
import com.example.data.local.CallLogEntity
import com.example.data.local.DEFAULT_DAILY_GOAL
import com.example.data.local.isConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationTest {
    private fun call(dir: String = "OUTGOING", seconds: Int = 0, disposition: String? = null) = CallLogEntity(
        phoneNumber = "9", normalizedPhone = "9", direction = dir, durationSeconds = seconds,
        timestampMs = 1L, disposition = disposition
    )

    @Test fun defaultGoalIsFifty() {
        assertEquals(50, DEFAULT_DAILY_GOAL)
        assertEquals(50, AppSettings().dailyCallGoal)
    }

    @Test fun answeredCallOfTenSecondsOrMoreCounts() {
        assertTrue(call("OUTGOING", 10).isConversation())
        assertTrue(call("INCOMING", 95).isConversation())
    }

    @Test fun unansweredAndVeryShortCallsDoNotCount() {
        assertFalse(call("OUTGOING", 0).isConversation())
        assertFalse(call("OUTGOING", 9).isConversation())
    }

    @Test fun missedCallsNeverCount() {
        assertFalse(call("MISSED", 0).isConversation())
        assertFalse(call("MISSED", 60).isConversation())
    }

    @Test fun repMarkingConnectedCountsAShortRealConversation() {
        assertTrue(call("OUTGOING", 6, "Connected").isConversation())
        assertFalse(call("OUTGOING", 6, "Busy").isConversation())
    }
}
