package com.example

import com.example.data.local.CallDirection
import com.example.data.repository.CrmRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CallLogPayloadTest {
    private fun payload(direction: CallDirection, url: String? = null) =
        CrmRepository.buildCallLogBasePayload(
            callUniqueId = "onebook-1-1",
            direction = direction,
            customerNumber = "+91 98410 28966",
            repNumber = "0000000000",
            durationSeconds = 24,
            startTime = "2026-11-16 23:11:00",
            endTime = "2026-11-16 23:11:24",
            recordingUrl = url
        )

    @Test fun outgoingHasRepFromAndCustomerTo() {
        val p = payload(CallDirection.OUTGOING)
        assertEquals("0000000000", p["from"])
        assertEquals("+91 98410 28966", p["to"])
    }

    @Test fun incomingHasCustomerFromAndRepTo() {
        val p = payload(CallDirection.INCOMING)
        assertEquals("+91 98410 28966", p["from"])
        assertEquals("0000000000", p["to"])
    }

    @Test fun neverContainsEmailInPhoneFields() {
        CallDirection.entries.forEach { d ->
            val p = payload(d, "/private/files/a.m4a")
            listOf("from", "to").forEach { k -> assertFalse(p[k]?.toString()?.contains("@") == true) }
            assertEquals("/private/files/a.m4a", p["recording_url"])
        }
    }

    @Test fun missedCallIsNoAnswer() {
        assertEquals("No Answer", payload(CallDirection.MISSED)["status"])
    }
}
