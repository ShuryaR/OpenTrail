package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.ContactEntity
import com.example.data.local.DealEntity
import com.example.data.local.LeadEntity
import com.example.data.local.TaskEntity
import com.example.ui.theme.JetBrainsMono
import com.example.ui.theme.LocalDimens
import com.example.ui.theme.SignalGreen
import com.example.util.PhoneNormalizer

enum class TaskBucket(val label: String) {
    OVERDUE("Overdue"),
    TODAY("Today"),
    UPCOMING("Upcoming"),
    ANYTIME("No due date"),
    DONE("Done")
}

/** Groups tasks by due date. [todayIso] is `yyyy-MM-dd`; due dates may carry a time suffix. */
fun bucketTasks(tasks: List<TaskEntity>, todayIso: String): Map<TaskBucket, List<TaskEntity>> {
    fun bucketOf(task: TaskEntity): TaskBucket {
        if (task.isCompleted) return TaskBucket.DONE
        val due = task.dueDate.take(10)
        return when {
            due.isBlank() -> TaskBucket.ANYTIME
            due < todayIso -> TaskBucket.OVERDUE
            due == todayIso -> TaskBucket.TODAY
            else -> TaskBucket.UPCOMING
        }
    }
    val grouped = tasks.groupBy(::bucketOf)
    return TaskBucket.entries.mapNotNull { bucket ->
        grouped[bucket]?.let { list ->
            bucket to if (bucket == TaskBucket.DONE) list else list.sortedBy { it.dueDate }
        }
    }.toMap()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksTabContent(
    tasks: List<TaskEntity>,
    onCall: (String) -> Unit,
    onToggleDone: (TaskEntity) -> Unit,
    onOpenRecord: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    if (tasks.isEmpty()) {
        EmptyPipelineState(
            title = "No Follow-Up Tasks",
            subtitle = "Tasks assigned to you in Frappe CRM will appear here for one-tap calling.",
            onRefresh = onRefresh
        )
        return
    }
    val todayIso = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()) }
    val groups = remember(tasks, todayIso) { bucketTasks(tasks, todayIso) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.listGap)
    ) {
        groups.forEach { (bucket, bucketTasks) ->
            item(key = "header_${bucket.name}") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = bucket.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (bucket == TaskBucket.OVERDUE) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "${bucketTasks.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(bucketTasks, key = { it.name }) { task ->
                SwipeableTaskCard(task, bucket, onCall, onToggleDone, onOpenRecord)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskCard(
    task: TaskEntity,
    bucket: TaskBucket,
    onCall: (String) -> Unit,
    onToggleDone: (TaskEntity) -> Unit,
    onOpenRecord: (String, String) -> Unit
) {
    // Swiping right toggles done but never removes the row itself: the list re-groups when the
    // task state changes, so the card moves to Done instead of vanishing.
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) onToggleDone(task)
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SignalGreen.copy(alpha = 0.85f))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = if (task.isCompleted) "Reopen task" else "Mark task done",
                    tint = Color.White
                )
            }
        }
    ) {
        TaskCard(task, bucket, onCall, onToggleDone, onOpenRecord)
    }
}

@Composable
private fun TaskCard(
    task: TaskEntity,
    bucket: TaskBucket,
    onCall: (String) -> Unit,
    onToggleDone: (TaskEntity) -> Unit,
    onOpenRecord: (String, String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (bucket == TaskBucket.OVERDUE) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = task.referenceDoctype.isNotBlank() && task.referenceDocname.isNotBlank()) {
                onOpenRecord(task.referenceDoctype, task.referenceDocname)
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(LocalDimens.current.cardPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onToggleDone(task) },
                modifier = Modifier.size(48.dp).testTag("task_done_${task.name}")
            ) {
                Icon(
                    imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                    contentDescription = if (task.isCompleted) "Reopen task" else "Mark task done",
                    tint = if (task.isCompleted) SignalGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOf(
                    task.dueDate.take(10).ifBlank { "No date" },
                    task.priority,
                    task.contactName.ifBlank { task.referenceDocname }
                ).filter { it.isNotBlank() }.joinToString(" • ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.phone.isNotBlank()) {
                    Text(
                        text = PhoneNormalizer.formatDisplay(task.phone),
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (task.phone.isNotBlank()) {
                Spacer(modifier = Modifier.width(10.dp))
                FilledIconButton(
                    onClick = { onCall(task.phone) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = SignalGreen,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(48.dp).testTag("task_call_${task.name}")
                ) {
                    Icon(Icons.Filled.Call, contentDescription = "Call ${task.contactName.ifBlank { "task contact" }}")
                }
            }
        }
    }
}

@Composable
fun LeadsTabContent(
    leads: List<LeadEntity>,
    selectedStatus: String,
    onSelectStatus: (String) -> Unit,
    onCall: (String) -> Unit,
    onOpenLead: (LeadEntity) -> Unit,
    onRefresh: () -> Unit,
    insights: Map<String, String> = emptyMap()
) {
    val statuses = listOf("All", "New", "Contacted", "Qualified", "Converted", "Lost")

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            items(statuses) { status ->
                FilterChip(
                    selected = selectedStatus.equals(status, ignoreCase = true),
                    onClick = { onSelectStatus(status) },
                    label = { Text(status) },
                    leadingIcon = if (selectedStatus.equals(status, ignoreCase = true)) {
                        { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }
        }

        if (leads.isEmpty()) {
            EmptyPipelineState(
                title = "No Matching Leads",
                subtitle = "Pull down or tap Sync to fetch your assigned CRM Leads from Frappe.",
                onRefresh = onRefresh
            )
            return
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(LocalDimens.current.listGap)
        ) {
            items(leads, key = { it.name }) { lead ->
                val callableNumber = lead.mobileNo.ifBlank { lead.phone }
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lead_card_${lead.name}")
                        .clickable { onOpenLead(lead) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LocalDimens.current.cardPad),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = lead.leadName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                StatusPill(label = lead.status)
                            }
                            if (lead.organization.isNotBlank()) {
                                Text(
                                    text = lead.organization,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // Number and the last-call / next-task hint share one line.
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (callableNumber.isNotBlank()) {
                                    Text(
                                        text = PhoneNormalizer.formatDisplay(callableNumber),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1
                                    )
                                }
                                insights[lead.name]?.let { line ->
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        FilledIconButton(
                            onClick = { onCall(callableNumber) },
                            enabled = callableNumber.isNotBlank(),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = SignalGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("lead_call_${lead.name}")
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Call ${lead.leadName}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DealsTabContent(
    deals: List<DealEntity>,
    onCall: (String) -> Unit,
    onOpenDeal: (DealEntity) -> Unit,
    onRefresh: () -> Unit
) {
    if (deals.isEmpty()) {
        EmptyPipelineState(
            title = "No Active Deals",
            subtitle = "Deals from Frappe CRM will group automatically by Deal Stage here.",
            onRefresh = onRefresh
        )
        return
    }

    val grouped = remember(deals) { deals.groupBy { it.dealStage.ifBlank { "Qualification" } } }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.listGap)
    ) {
        grouped.forEach { (stage, stageDeals) ->
            item(key = "header_$stage") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stage.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${stageDeals.size} deals" + stageTotalLabel(stageDeals),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(stageDeals, key = { it.name }) { deal ->
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("deal_card_${deal.name}")
                        .clickable { onOpenDeal(deal) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LocalDimens.current.cardPad),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap)
                        ) {
                            Text(
                                text = deal.dealTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val sub = listOf(deal.organization, deal.contactName)
                                .filter { it.isNotBlank() }
                                .joinToString(" • ")
                            if (sub.isNotBlank()) {
                                Text(
                                    text = sub,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (deal.dealValue > 0.0) {
                                    Text(
                                        text = "${deal.currency} ${"%,.0f".format(deal.dealValue)}",
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMono),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                if (deal.phone.isNotBlank()) {
                                    Text(
                                        text = PhoneNormalizer.formatDisplay(deal.phone),
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))
                        FilledIconButton(
                            onClick = { onCall(deal.phone) },
                            enabled = deal.phone.isNotBlank(),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = SignalGreen,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("deal_call_${deal.name}")
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Call ${deal.dealTitle}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactsTabContent(
    contacts: List<ContactEntity>,
    onCall: (String) -> Unit,
    onOpenContact: (ContactEntity) -> Unit,
    onRefresh: () -> Unit
) {
    if (contacts.isEmpty()) {
        EmptyPipelineState(
            title = "No Contacts Synced",
            subtitle = "Frappe Contacts sync locally for instant search and incoming Caller ID matching.",
            onRefresh = onRefresh
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.listGap)
    ) {
        items(contacts, key = { it.name }) { contact ->
            val callable = contact.mobileNo.ifBlank { contact.phone }
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("contact_card_${contact.name}")
                    .clickable { onOpenContact(contact) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(LocalDimens.current.cardPad),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = contact.fullName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(LocalDimens.current.cardGap)
                    ) {
                        Text(
                            text = contact.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = listOf(contact.designation, contact.companyName)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                        if (meta.isNotBlank()) {
                            Text(
                                text = meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (callable.isNotBlank()) {
                            Text(
                                text = PhoneNormalizer.formatDisplay(callable),
                                style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))
                    FilledIconButton(
                        onClick = { onCall(callable) },
                        enabled = callable.isNotBlank(),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = SignalGreen,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("contact_call_${contact.name}")
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = "Call ${contact.fullName}")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun EmptyPipelineState(
    title: String,
    subtitle: String,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.testTag("empty_state_refresh_button")
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sync from Frappe CRM")
        }
    }
}


/**
 * Frappe tasks only carry a reference doctype/name. Resolve the linked Lead/Deal/Contact from the
 * local cache so the Tasks tab can show a real name and a one-tap call button.
 */
fun enrichTasks(
    tasks: List<TaskEntity>,
    leads: List<com.example.data.local.LeadEntity>,
    deals: List<com.example.data.local.DealEntity>,
    contacts: List<com.example.data.local.ContactEntity>
): List<TaskEntity> {
    val leadMap = leads.associateBy { it.name }
    val dealMap = deals.associateBy { it.name }
    val contactMap = contacts.associateBy { it.name }
    return tasks.map { task ->
        val (name, phone) = when (task.referenceDoctype) {
            "CRM Lead", "Lead" -> leadMap[task.referenceDocname]?.let { it.leadName to it.mobileNo.ifBlank { it.phone } }
            "CRM Deal", "Opportunity" -> dealMap[task.referenceDocname]?.let { it.dealTitle to it.phone }
            "Contact" -> contactMap[task.referenceDocname]?.let { it.fullName to it.mobileNo.ifBlank { it.phone } }
            else -> null
        } ?: ("" to "")
        task.copy(contactName = name, phone = task.phone.ifBlank { phone })
    }
}


/** " · INR 12,34,000" for a stage's open value, or "" when no deal carries a value. */
fun stageTotalLabel(deals: List<DealEntity>): String {
    val byCurrency = deals.filter { it.dealValue > 0.0 }.groupBy { it.currency }
        .mapValues { (_, list) -> list.sumOf { it.dealValue } }
    if (byCurrency.isEmpty()) return ""
    return " · " + byCurrency.entries.joinToString(" + ") { (cur, total) -> "$cur ${"%,.0f".format(total)}" }
}

/**
 * One line per lead for the Leads list: when you last called and what is next. Built from the
 * local call log and tasks so it works offline.
 */
fun buildLeadInsights(
    leads: List<LeadEntity>,
    calls: List<com.example.data.local.CallLogEntity>,
    tasks: List<TaskEntity>,
    nowMs: Long
): Map<String, String> {
    val lastCallByPhone = HashMap<String, Long>()
    calls.forEach { c ->
        if (c.normalizedPhone.isNotBlank()) {
            lastCallByPhone.merge(c.normalizedPhone, c.timestampMs) { a, b -> maxOf(a, b) }
        }
    }
    val nextTaskByLead = tasks.filter { !it.isCompleted && it.dueDate.isNotBlank() }
        .groupBy { it.referenceDocname }
        .mapValues { (_, list) -> list.minOf { it.dueDate.take(10) } }
    return buildMap {
        leads.forEach { lead ->
            val parts = buildList {
                lastCallByPhone[lead.normalizedPhone]?.let {
                    add("Called " + com.example.util.RelativeTime.format(nowMs, it))
                }
                nextTaskByLead[lead.name]?.let { add("Task due $it") }
            }
            if (parts.isNotEmpty()) put(lead.name, parts.joinToString(" · "))
        }
    }
}
