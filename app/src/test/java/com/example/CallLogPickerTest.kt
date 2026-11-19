package com.example

import android.provider.CallLog
import com.example.data.local.CallDirection
import com.example.util.CallLogPicker
import com.example.util.CallLogRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLogPickerTest {
    private val start = 1_000_000_000_000L
    private fun row(number: String, type: Int, date: Long, dur: Int = 0) =
        CallLogRow(number, type, dur, date, "1")

    @Test fun typesMapToCrmDirections() {
        assertEquals(CallDirection.INCOMING, CallLogPicker.directionFor(CallLog.Calls.INCOMING_TYPE))
        assertEquals(CallDirection.OUTGOING, CallLogPicker.directionFor(CallLog.Calls.OUTGOING_TYPE))
        assertEquals(CallDirection.MISSED, CallLogPicker.directionFor(CallLog.Calls.MISSED_TYPE))
        assertEquals(CallDirection.MISSED, CallLogPicker.directionFor(CallLog.Calls.REJECTED_TYPE))
    }

    @Test fun voicemailBlockedAndAnsweredExternallyAreNotCalls() {
        listOf(CallLog.Calls.VOICEMAIL_TYPE, CallLog.Calls.BLOCKED_TYPE, CallLog.Calls.ANSWERED_EXTERNALLY_TYPE).forEach {
            assertTrue("type $it", CallLogPicker.isNotACall(it))
        }
    }

    @Test fun vendorTypesAreStillCallsButHaveNoStandardDirection() {
        // OPPO/ColorOS writes 100 and 101; they must be logged, using the direction seen on the phone.
        listOf(27, 99, 100, 101, 102).forEach {
            assertFalse("type $it", CallLogPicker.isNotACall(it))
            assertNull("type $it", CallLogPicker.directionFor(it))
        }
    }

    @Test fun ignoresRowsFromBeforeThisCall() {
        val rows = listOf(row("+919876543210", 2, start - 600_000))
        assertNull(CallLogPicker.pick(rows, start, "9876543210"))
    }

    @Test fun prefersTheRowForTheActiveNumberEvenIfAnotherIsNewer() {
        val rows = listOf(
            row("+911111111111", 1, start + 20_000),   // newer, different caller
            row("+919876543210", 3, start + 1_000)     // this call
        )
        assertEquals("+919876543210", CallLogPicker.pick(rows, start, "09876543210")!!.number)
    }

    @Test fun fallsBackToNewestFreshRowWhenTheNumberIsUnknown() {
        val rows = listOf(row("+912222222222", 3, start + 5_000), row("+913333333333", 1, start + 1_000))
        assertEquals("+912222222222", CallLogPicker.pick(rows, start, "")!!.number)
    }

    @Test fun toleratesSmallClockSlackAtTheStart() {
        val rows = listOf(row("+919876543210", 3, start - 5_000))
        assertEquals(3, CallLogPicker.pick(rows, start, "9876543210")!!.type)
    }
}
