package com.example

import com.example.data.local.CallLogEntity
import com.example.data.local.DealEntity
import com.example.data.local.LeadEntity
import com.example.data.local.TaskEntity
import com.example.ui.screens.buildLeadInsights
import com.example.ui.screens.stageTotalLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeadInsightsTest {
    private val now = 10_000_000_000L
    private fun lead(name: String, phone: String) = LeadEntity(
        name = name, leadName = name, organization = "", status = "New", email = "", phone = "",
        mobileNo = phone, normalizedPhone = phone, source = "", lastNote = "", modified = ""
    )
    private fun call(phone: String, ts: Long) = CallLogEntity(
        phoneNumber = phone, normalizedPhone = phone, direction = "OUTGOING",
        durationSeconds = 5, timestampMs = ts
    )
    private fun task(ref: String, due: String, done: Boolean = false) = TaskEntity(
        name = "t-$ref-$due", title = "call", description = "", status = "Todo", priority = "Low",
        dueDate = due, referenceDoctype = "CRM Lead", referenceDocname = ref, contactName = "",
        phone = "", normalizedPhone = "", isCompleted = done
    )
    private fun deal(value: Double, cur: String = "INR") = DealEntity(
        name = "d$value", dealTitle = "d", organization = "", status = "", dealStage = "Qualification",
        dealValue = value, currency = cur, phone = "", normalizedPhone = "", contactName = "",
        lastNote = "", modified = ""
    )

    @Test fun combinesLastCallAndNextTask() {
        val out = buildLeadInsights(
            listOf(lead("L1", "9876543210")),
            listOf(call("9876543210", now - 2 * 86_400_000L), call("9876543210", now - 5 * 86_400_000L)),
            listOf(task("L1", "2026-12-01 10:00:00"), task("L1", "2026-11-20")),
            now
        )
        assertEquals("Called 2 d ago · Task due 2026-11-20", out["L1"])
    }

    @Test fun skipsLeadsWithNothingToSay() {
        val out = buildLeadInsights(listOf(lead("L2", "111")), emptyList(), listOf(task("L2", "", false)), now)
        assertNull(out["L2"])
    }

    @Test fun ignoresCompletedTasks() {
        val out = buildLeadInsights(listOf(lead("L3", "222")), emptyList(), listOf(task("L3", "2026-11-20", done = true)), now)
        assertNull(out["L3"])
    }

    @Test fun stageTotals() {
        assertEquals(" · INR 1,500", stageTotalLabel(listOf(deal(1000.0), deal(500.0))))
        assertEquals("", stageTotalLabel(listOf(deal(0.0))))
    }
}
