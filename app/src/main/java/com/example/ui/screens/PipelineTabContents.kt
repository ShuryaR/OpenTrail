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
import com.example.ui.theme.SignalGreen
import com.example.util.PhoneNormalizer

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
    LazyColumn(
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tasks, key = { it.name }) { task ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = task.referenceDoctype.isNotBlank() && task.referenceDocname.isNotBlank()) {
                        onOpenRecord(task.referenceDoctype, task.referenceDocname)
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onToggleDone(task) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("task_done_${task.name}")
                    ) {
                        Icon(
                            imageVector = if (task.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                            contentDescription = "Mark task done",
                            tint = if (task.isCompleted) SignalGreen else MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val meta = listOf(
                            task.dueDate.ifBlank { "Today" },
                            task.priority,
                            task.referenceDocname
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
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("task_call_${task.name}")
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = "Call task contact")
                        }
                    }
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
    onRefresh: () -> Unit
) {
    val statuses = listOf("All", "New", "Contacted", "Qualified", "Converted", "Lost")

    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
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
            contentPadding = PaddingValues(bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Business,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = lead.organization,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (callableNumber.isNotBlank()) {
                                Text(
                                    text = PhoneNormalizer.formatDisplay(callableNumber),
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMono),
                                    color = MaterialTheme.colorScheme.primary
                                )
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
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        text = "${stageDeals.size} deals",
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
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
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
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
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
                        .padding(16.dp),
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
                        verticalArrangement = Arrangement.spacedBy(4.dp)
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
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
