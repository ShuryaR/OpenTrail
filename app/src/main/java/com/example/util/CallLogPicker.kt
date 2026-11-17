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

    /**
     * How a call-history type is recorded in the CRM. Null means "do not log": voicemail, blocked,
     * answered on another device and vendor-specific types are not calls this rep had.
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

    private const val START_SLACK_MS = 15_000L
}
