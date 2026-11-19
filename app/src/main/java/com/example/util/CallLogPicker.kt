package com.example.util

import android.provider.CallLog
import com.example.data.local.CallDirection

/** One row of Android's own call history, trimmed to what the app needs. */
data class CallLogRow(
    val number: String,
    val type: Int,
    val durationSeconds: Int,
    val dateMs: Long,
    val accountId: String
)

object CallLogPicker {

    /** Voicemail, blocked and "answered on another device" rows are not calls this rep had. */
    fun isNotACall(type: Int): Boolean = type in NOT_A_CALL

    /**
     * How a standard call-history type is recorded in the CRM. Null means the type is not one of the
     * standard ones: phone makers add their own (OPPO/ColorOS uses 100 and 101, for example), so the
     * caller must fall back to what it saw happen on the phone instead of dropping the call.
     */
    fun directionFor(type: Int): CallDirection? = when (type) {
        CallLog.Calls.INCOMING_TYPE -> CallDirection.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallDirection.OUTGOING
        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallDirection.MISSED
        else -> null
    }

    /**
     * Picks this call's row from the newest-first [rows]. A row belongs to the call when it started
     * no earlier than [callStartMs] (with slack for clock rounding). Among those, prefer the one for
     * [activeNumber]; otherwise the newest, so two calls in quick succession are not mixed up.
     */
    fun pick(rows: List<CallLogRow>, callStartMs: Long, activeNumber: String): CallLogRow? {
        val fresh = rows.filter { it.dateMs >= callStartMs - START_SLACK_MS }
        if (fresh.isEmpty()) return null
        val key = PhoneNormalizer.normalize(activeNumber)
        if (key.isNotBlank()) {
            fresh.firstOrNull { PhoneNormalizer.normalize(it.number) == key }?.let { return it }
        }
        return fresh.first()
    }

    private val NOT_A_CALL = setOf(
        CallLog.Calls.VOICEMAIL_TYPE,
        CallLog.Calls.BLOCKED_TYPE,
        CallLog.Calls.ANSWERED_EXTERNALLY_TYPE
    )

    private const val START_SLACK_MS = 15_000L
}
