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
        assertEquals("+919841028966", p["to"])
    }

    @Test fun incomingHasCustomerFromAndRepTo() {
        val p = payload(CallDirection.INCOMING)
        assertEquals("+919841028966", p["from"])
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

    @Test fun writesBothNumbersInPlus91FormatWhateverTheSource() {
        val p = CrmRepository.buildCallLogBasePayload(
            callUniqueId = "x", direction = CallDirection.OUTGOING,
            customerNumber = "919841028966", repNumber = "917904183832",
            durationSeconds = 5, startTime = "s", endTime = "e", recordingUrl = null
        )
        assertEquals("+917904183832", p["from"])
        assertEquals("+919841028966", p["to"])
    }

    @Test fun unansweredOutgoingCallIsNoAnswer() {
        assertEquals("No Answer", CrmRepository.callLogStatus(CallDirection.OUTGOING, 0))
        assertEquals("Completed", CrmRepository.callLogStatus(CallDirection.OUTGOING, 12))
    }

    @Test fun missedIsNoAnswerAndAnsweredIncomingIsCompleted() {
        assertEquals("No Answer", CrmRepository.callLogStatus(CallDirection.MISSED, 0))
        assertEquals("Completed", CrmRepository.callLogStatus(CallDirection.INCOMING, 45))
    }

    @Test fun missedCallIsAnIncomingTypeInTheCrm() {
        assertEquals("Incoming", payload(CallDirection.MISSED)["type"])
    }
}
