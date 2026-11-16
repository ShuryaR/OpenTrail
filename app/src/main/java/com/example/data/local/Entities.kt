package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class CallDirection(val frappeType: String, val label: String) {
    OUTGOING("Outgoing", "Outgoing"),
    INCOMING("Incoming", "Incoming"),
    MISSED("Incoming", "Missed")
}

enum class SyncStatus(val label: String) {
    PENDING("Pending Sync"),
    SYNCING("Uploading..."),
    SYNCED("Synced"),
    FAILED("Sync Failed")
}

@Entity(
    tableName = "leads",
    indices = [Index(value = ["normalizedPhone"])]
)
data class LeadEntity(
    @PrimaryKey val name: String,
    val leadName: String,
    val organization: String,
    val status: String,
    val email: String,
    val phone: String,
    val mobileNo: String,
    val normalizedPhone: String,
    val source: String,
    val lastNote: String,
    val modified: String
)

@Entity(
    tableName = "deals",
    indices = [Index(value = ["normalizedPhone"])]
)
data class DealEntity(
    @PrimaryKey val name: String,
    val dealTitle: String,
    val organization: String,
    val status: String,
    val dealStage: String,
    val dealValue: Double,
    val currency: String,
    val phone: String,
    val normalizedPhone: String,
    val contactName: String,
    val lastNote: String,
    val modified: String
)

@Entity(
    tableName = "contacts",
    indices = [Index(value = ["normalizedPhone"])]
)
data class ContactEntity(
    @PrimaryKey val name: String,
    val fullName: String,
    val companyName: String,
    val email: String,
    val phone: String,
    val mobileNo: String,
    val normalizedPhone: String,
    val designation: String,
    val modified: String
)

@Entity(
    tableName = "tasks",
    indices = [Index(value = ["dueDate"])]
)
data class TaskEntity(
    @PrimaryKey val name: String,
    val title: String,
    val description: String,
    val status: String,
    val priority: String,
    val dueDate: String,
    val referenceDoctype: String,
    val referenceDocname: String,
    val contactName: String,
    val phone: String,
    val normalizedPhone: String,
    val isCompleted: Boolean
)

@Entity(
    tableName = "call_logs",
    indices = [Index(value = ["normalizedPhone"]), Index(value = ["syncStatus"])]
)
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val frappeDocName: String = "",
    val phoneNumber: String,
    val normalizedPhone: String,
    val direction: String,
    val durationSeconds: Int,
    val timestampMs: Long,
    val localAudioPath: String? = null,
    val remoteAudioUrl: String? = null,
    val referenceDoctype: String? = null,
    val referenceDocname: String? = null,
    val matchedName: String? = null,
    val matchedCompany: String? = null,
    val disposition: String? = null,
    val note: String? = null,
    val nextFollowUpDate: String? = null,
    val syncStatus: String = SyncStatus.PENDING.name,
    val syncError: String? = null,
    val simSlotIndex: Int = -1,
    val retryCount: Int = 0
)

@Entity(tableName = "excluded_numbers")
data class ExcludedNumberEntity(
    @PrimaryKey val normalizedPhone: String,
    val displayPhone: String,
    val label: String
)

@Entity(
    tableName = "crm_notes",
    indices = [Index(value = ["referenceDoctype", "referenceDocname"])]
)
data class CrmNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val frappeDocName: String = "",
    val referenceDoctype: String,
    val referenceDocname: String,
    val title: String,
    val content: String,
    val createdBy: String,
    val creationTimestamp: String
)


private const val UNLOGGED_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

/**
 * A call the rep still needs to wrap up: made through this app (queued locally or logged under a
 * `onebook-` id), not missed, no note or disposition yet, and recent. Calls other reps or the web
 * app put in the CRM are not the rep's backlog.
 */
fun CallLogEntity.isUnlogged(nowMs: Long = System.currentTimeMillis()): Boolean =
    direction != CallDirection.MISSED.name &&
        note.isNullOrBlank() &&
        disposition.isNullOrBlank() &&
        (frappeDocName.isBlank() || frappeDocName.startsWith("onebook-")) &&
        nowMs - timestampMs <= UNLOGGED_WINDOW_MS
