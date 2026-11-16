package com.example.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.AppDatabase
import com.example.data.local.AuthSession
import com.example.data.local.CallDirection
import com.example.data.local.CallLogEntity
import com.example.data.local.ContactEntity
import com.example.data.local.CrmNoteEntity
import com.example.data.local.DealEntity
import com.example.data.local.ExcludedNumberEntity
import com.example.data.local.LeadEntity
import com.example.data.local.PrivacyFilterMode
import com.example.data.local.SecureCredentialStore
import com.example.data.local.SyncStatus
import com.example.data.local.TaskEntity
import com.example.data.remote.FrappeClientFactory
import com.example.data.remote.FrappeLoginRequest
import com.example.data.remote.dbl
import com.example.data.remote.integer
import com.example.data.remote.str
import com.example.util.PhoneNormalizer
import com.example.worker.CallSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CrmMatch(
    val doctype: String,
    val docname: String,
    val displayName: String,
    val company: String,
    val stageOrStatus: String,
    val lastNote: String
)

sealed class RepoResult<out T> {
    data class Success<T>(val data: T) : RepoResult<T>()
    data class Error(val message: String) : RepoResult<Nothing>()
}

class CrmRepository(
    private val context: Context,
    private val database: AppDatabase,
    val credentialStore: SecureCredentialStore
) {
    private val leadDao = database.leadDao()
    private val dealDao = database.dealDao()
    private val contactDao = database.contactDao()
    private val taskDao = database.taskDao()
    private val callLogDao = database.callLogDao()
    private val excludedNumberDao = database.excludedNumberDao()
    private val crmNoteDao = database.crmNoteDao()

    val leadsFlow: Flow<List<LeadEntity>> = leadDao.observeAll()
    val dealsFlow: Flow<List<DealEntity>> = dealDao.observeAll()
    val contactsFlow: Flow<List<ContactEntity>> = contactDao.observeAll()
    val tasksFlow: Flow<List<TaskEntity>> = taskDao.observeAll()
    val allCallLogsFlow: Flow<List<CallLogEntity>> = callLogDao.observeAll()
    val pendingCallLogsFlow: Flow<List<CallLogEntity>> = callLogDao.observePendingOrFailed()
    val pendingCountFlow: Flow<Int> = callLogDao.observePendingCount()
    val excludedNumbersFlow: Flow<List<ExcludedNumberEntity>> = excludedNumberDao.observeAll()

    fun observeLead(name: String): Flow<LeadEntity?> = leadDao.observeByName(name)
    fun observeDeal(name: String): Flow<DealEntity?> = dealDao.observeByName(name)
    fun observeContact(name: String): Flow<ContactEntity?> = contactDao.observeByName(name)

    fun observeCallLogsForRecord(
        doctype: String,
        docname: String,
        normalizedPhone: String
    ): Flow<List<CallLogEntity>> = callLogDao.observeForRecord(doctype, docname, normalizedPhone)

    fun observeNotesForRecord(doctype: String, docname: String): Flow<List<CrmNoteEntity>> =
        crmNoteDao.observeForRecord(doctype, docname)

    suspend fun loginWithApiToken(
        rawUrl: String,
        apiKey: String,
        apiSecret: String
    ): RepoResult<String> = withContext(Dispatchers.IO) {
        val cleanUrl = FrappeClientFactory.normalizeBaseUrl(rawUrl)
        val tempSession = AuthSession(
            baseUrl = cleanUrl,
            apiKey = apiKey.trim(),
            apiSecret = apiSecret.trim()
        )
        runCatching {
            val service = FrappeClientFactory.createService(tempSession)
            val resp = service.getLoggedUser()
            if (resp.isSuccessful) {
                val user = resp.body()?.message ?: "CRM User"
                credentialStore.saveApiTokenCredentials(cleanUrl, apiKey, apiSecret, user)
                RepoResult.Success(user)
            } else {
                RepoResult.Error("Authentication failed (HTTP ${resp.code()}). Verify URL and API credentials.")
            }
        }.getOrElse { e ->
            RepoResult.Error(e.localizedMessage ?: "Unable to connect to Frappe CRM server.")
        }
    }

    suspend fun loginWithPassword(
        rawUrl: String,
        email: String,
        password: String
    ): RepoResult<String> = withContext(Dispatchers.IO) {
        val cleanUrl = FrappeClientFactory.normalizeBaseUrl(rawUrl)
        runCatching {
            val service = FrappeClientFactory.createUnauthenticatedService(cleanUrl)
            val resp = service.login(FrappeLoginRequest(usr = email.trim(), pwd = password))
            if (resp.isSuccessful) {
                val cookies = resp.headers().values("Set-Cookie")
                val sidCookie = cookies
                    .mapNotNull { header -> header.split(';').firstOrNull { it.trim().startsWith("sid=") } }
                    .lastOrNull()
                    ?.trim()
                if (sidCookie.isNullOrBlank()) {
                    RepoResult.Error("Login succeeded but no session cookie (sid) was returned.")
                } else {
                    credentialStore.saveSessionCredentials(cleanUrl, sidCookie, email.trim())
                    RepoResult.Success(email.trim())
                }
            } else {
                RepoResult.Error("Invalid Frappe credentials (HTTP ${resp.code()}).")
            }
        }.getOrElse { e ->
            RepoResult.Error(e.localizedMessage ?: "Connection error reaching Frappe server.")
        }
    }

    suspend fun refreshAllPipeline(): RepoResult<Unit> = withContext(Dispatchers.IO) {
        val session = credentialStore.getAuthSession()
        if (!session.isAuthenticated) {
            return@withContext RepoResult.Error("Not authenticated.")
        }
        val service = FrappeClientFactory.createService(session)

        runCatching {
            // 1. Leads (CRM Lead with Lead fallback)
            val leadResp = service.listResources("CRM Lead").let {
                if (it.isSuccessful) it else service.listResources("Lead")
            }
            if (leadResp.isSuccessful) {
                val leads = leadResp.body()?.data.orEmpty().map { row ->
                    val rawPhone = row.str("mobile_no", "phone", "whatsapp_no")
                    val fullName = row.str("lead_name", "first_name").ifBlank {
                        listOf(row.str("first_name"), row.str("last_name"))
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    }.ifBlank { row.str("name") }
                    LeadEntity(
                        name = row.str("name"),
                        leadName = fullName,
                        organization = row.str("organization", "company_name"),
                        status = row.str("status").ifBlank { "New" },
                        email = row.str("email", "email_id"),
                        phone = row.str("phone"),
                        mobileNo = rawPhone,
                        normalizedPhone = PhoneNormalizer.normalize(rawPhone),
                        source = row.str("source"),
                        lastNote = row.str("last_note", "notes"),
                        modified = row.str("modified")
                    )
                }.filter { it.name.isNotBlank() }
                leadDao.clearAll()
                leadDao.upsertAll(leads)
            }

            // 2. Deals (CRM Deal with Opportunity fallback)
            val dealResp = service.listResources("CRM Deal").let {
                if (it.isSuccessful) it else service.listResources("Opportunity")
            }
            if (dealResp.isSuccessful) {
                val deals = dealResp.body()?.data.orEmpty().map { row ->
                    val rawPhone = row.str("mobile_no", "phone", "contact_mobile")
                    DealEntity(
                        name = row.str("name"),
                        dealTitle = row.str("title", "organization", "customer_name", "name"),
                        organization = row.str("organization", "party_name", "customer_name"),
                        status = row.str("status").ifBlank { "Qualification" },
                        dealStage = row.str("status", "sales_stage").ifBlank { "Qualification" },
                        dealValue = row.dbl("deal_value", "opportunity_amount", "annual_revenue"),
                        currency = row.str("currency").ifBlank { "INR" },
                        phone = rawPhone,
                        normalizedPhone = PhoneNormalizer.normalize(rawPhone),
                        contactName = row.str("lead_name", "contact_person", "contact_display"),
                        lastNote = row.str("next_step", "notes"),
                        modified = row.str("modified")
                    )
                }.filter { it.name.isNotBlank() }
                dealDao.clearAll()
                dealDao.upsertAll(deals)
            }

            // 3. Contacts
            val contactResp = service.listResources("Contact")
            if (contactResp.isSuccessful) {
                val contacts = contactResp.body()?.data.orEmpty().map { row ->
                    val rawPhone = row.str("mobile_no", "phone")
                    val fullName = row.str("full_name").ifBlank {
                        listOf(row.str("first_name"), row.str("last_name"))
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                    }.ifBlank { row.str("name") }
                    ContactEntity(
                        name = row.str("name"),
                        fullName = fullName,
                        companyName = row.str("company_name"),
                        email = row.str("email_id"),
                        phone = row.str("phone"),
                        mobileNo = rawPhone,
                        normalizedPhone = PhoneNormalizer.normalize(rawPhone),
                        designation = row.str("designation"),
                        modified = row.str("modified")
                    )
                }.filter { it.name.isNotBlank() }
                contactDao.clearAll()
                contactDao.upsertAll(contacts)
            }

            // 4. Tasks (CRM Task with ToDo fallback)
            val taskResp = service.listResources("CRM Task", orderBy = "due_date asc").let {
                if (it.isSuccessful) it else service.listResources("ToDo", orderBy = "date asc")
            }
            if (taskResp.isSuccessful) {
                val tasks = taskResp.body()?.data.orEmpty().map { row ->
                    val status = row.str("status").ifBlank { "Todo" }
                    val completed = status.equals("Done", true) || status.equals("Closed", true)
                    val refType = row.str("reference_doctype", "reference_type")
                    val refName = row.str("reference_docname", "reference_name")
                    val rawPhone = row.str("phone", "mobile_no")
                    TaskEntity(
                        name = row.str("name"),
                        title = row.str("title", "description", "name"),
                        description = row.str("description"),
                        status = status,
                        priority = row.str("priority").ifBlank { "Medium" },
                        dueDate = row.str("due_date", "date"),
                        referenceDoctype = refType,
                        referenceDocname = refName,
                        contactName = row.str("assigned_to", "reference_name"),
                        phone = rawPhone,
                        normalizedPhone = PhoneNormalizer.normalize(rawPhone),
                        isCompleted = completed
                    )
                }.filter { it.name.isNotBlank() }
                taskDao.clearAll()
                taskDao.upsertAll(tasks)
            }

            // 5. Recent CRM Call Logs
            val callResp = service.listResources("CRM Call Log", orderBy = "creation desc")
            if (callResp.isSuccessful) {
                val remoteLogs = callResp.body()?.data.orEmpty().mapNotNull { row ->
                    val docName = row.str("name")
                    if (docName.isBlank()) return@mapNotNull null
                    val type = row.str("type")
                    val caller = row.str("from", "caller")
                    val receiver = row.str("to", "receiver")
                    val num = if (type.equals("Incoming", true)) caller else receiver.ifBlank { caller }
                    CallLogEntity(
                        frappeDocName = docName,
                        phoneNumber = num,
                        normalizedPhone = PhoneNormalizer.normalize(num),
                        direction = if (type.equals("Incoming", true)) {
                            CallDirection.INCOMING.name
                        } else {
                            CallDirection.OUTGOING.name
                        },
                        durationSeconds = row.integer("duration"),
                        timestampMs = parseFrappeDate(row.str("creation", "start_time")),
                        remoteAudioUrl = row.str("recording_url"),
                        referenceDoctype = row.str("reference_doctype").ifBlank { null },
                        referenceDocname = row.str("reference_docname").ifBlank { null },
                        note = row.str("note").ifBlank { null },
                        syncStatus = SyncStatus.SYNCED.name
                    )
                }
                callLogDao.clearSyncedRemoteCache()
                if (remoteLogs.isNotEmpty()) {
                    callLogDao.insertAll(remoteLogs)
                }
            }

            credentialStore.setLastSyncEpochMs(System.currentTimeMillis())
            RepoResult.Success(Unit)
        }.getOrElse { e ->
            RepoResult.Error(e.localizedMessage ?: "Failed to sync Frappe CRM records.")
        }
    }

    suspend fun fetchNotesForRecord(doctype: String, docname: String) = withContext(Dispatchers.IO) {
        val session = credentialStore.getAuthSession()
        if (!session.isAuthenticated) return@withContext
        val service = FrappeClientFactory.createService(session)
        val filters = "[[\"reference_doctype\",\"=\",\"$doctype\"],[\"reference_docname\",\"=\",\"$docname\"]]"
        runCatching {
            val resp = service.listResources("CRM Note", filters = filters, orderBy = "creation desc")
            if (resp.isSuccessful) {
                val notes = resp.body()?.data.orEmpty().map { row ->
                    CrmNoteEntity(
                        frappeDocName = row.str("name"),
                        referenceDoctype = doctype,
                        referenceDocname = docname,
                        title = row.str("title").ifBlank { "Call Note" },
                        content = row.str("content"),
                        createdBy = row.str("owner"),
                        creationTimestamp = row.str("creation")
                    )
                }
                crmNoteDao.clearForRecord(doctype, docname)
                crmNoteDao.upsertAll(notes)
            }
        }
    }

    suspend fun matchPhoneToCrm(rawPhone: String): CrmMatch? {
        val normalized = PhoneNormalizer.normalize(rawPhone)
        if (normalized.isBlank()) return null

        leadDao.findByNormalizedPhone(normalized)?.let { lead ->
            return CrmMatch(
                doctype = "CRM Lead",
                docname = lead.name,
                displayName = lead.leadName,
                company = lead.organization,
                stageOrStatus = lead.status,
                lastNote = lead.lastNote
            )
        }
        dealDao.findByNormalizedPhone(normalized)?.let { deal ->
            return CrmMatch(
                doctype = "CRM Deal",
                docname = deal.name,
                displayName = deal.dealTitle,
                company = deal.organization,
                stageOrStatus = deal.dealStage,
                lastNote = deal.lastNote
            )
        }
        contactDao.findByNormalizedPhone(normalized)?.let { contact ->
            return CrmMatch(
                doctype = "Contact",
                docname = contact.name,
                displayName = contact.fullName,
                company = contact.companyName,
                stageOrStatus = contact.designation.ifBlank { "Contact" },
                lastNote = ""
            )
        }
        return null
    }

    suspend fun shouldRecordAndSyncCall(rawPhone: String, simSlotIndex: Int): Boolean {
        val settings = credentialStore.getSettings()
        if (settings.workSimPreference.slotIndex != -1 &&
            simSlotIndex != -1 &&
            simSlotIndex != settings.workSimPreference.slotIndex
        ) {
            return false
        }
        val normalized = PhoneNormalizer.normalize(rawPhone)
        if (normalized.isNotBlank() && excludedNumberDao.isExcluded(normalized)) {
            return false
        }
        if (settings.privacyFilterMode == PrivacyFilterMode.CRM_ONLY) {
            return matchPhoneToCrm(rawPhone) != null
        }
        return true
    }

    suspend fun queueCallLog(
        phoneNumber: String,
        direction: CallDirection,
        durationSeconds: Int,
        timestampMs: Long,
        localAudioPath: String?,
        simSlotIndex: Int
    ): Long {
        val normalized = PhoneNormalizer.normalize(phoneNumber)
        val match = matchPhoneToCrm(phoneNumber)
        val entity = CallLogEntity(
            phoneNumber = phoneNumber,
            normalizedPhone = normalized,
            direction = direction.name,
            durationSeconds = durationSeconds,
            timestampMs = timestampMs,
            localAudioPath = localAudioPath,
            referenceDoctype = match?.doctype,
            referenceDocname = match?.docname,
            matchedName = match?.displayName,
            matchedCompany = match?.company,
            syncStatus = SyncStatus.PENDING.name,
            simSlotIndex = simSlotIndex
        )
        val id = callLogDao.insert(entity)
        enqueueSyncWorker()
        return id
    }

    suspend fun updatePostCallWrapUp(
        callLogId: Long,
        disposition: String?,
        note: String?,
        nextFollowUpDate: String?
    ) {
        val existing = callLogDao.getById(callLogId) ?: return
        val combinedNote = buildString {
            if (!disposition.isNullOrBlank()) append("[$disposition] ")
            if (!note.isNullOrBlank()) append(note.trim())
        }.trim().ifBlank { null }

        callLogDao.update(
            existing.copy(
                disposition = disposition,
                note = combinedNote,
                nextFollowUpDate = nextFollowUpDate,
                syncStatus = SyncStatus.PENDING.name
            )
        )
        if (!combinedNote.isNullOrBlank() &&
            !existing.referenceDoctype.isNullOrBlank() &&
            !existing.referenceDocname.isNullOrBlank()
        ) {
            crmNoteDao.insert(
                CrmNoteEntity(
                    referenceDoctype = existing.referenceDoctype,
                    referenceDocname = existing.referenceDocname,
                    title = disposition ?: "Call Wrap-Up",
                    content = combinedNote,
                    createdBy = credentialStore.getAuthSession().loggedInUser,
                    creationTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
                )
            )
        }
        enqueueSyncWorker()
    }

    suspend fun addNoteToRecord(
        doctype: String,
        docname: String,
        title: String,
        content: String
    ): RepoResult<Unit> = withContext(Dispatchers.IO) {
        val session = credentialStore.getAuthSession()
        val user = session.loggedInUser
        crmNoteDao.insert(
            CrmNoteEntity(
                referenceDoctype = doctype,
                referenceDocname = docname,
                title = title,
                content = content,
                createdBy = user,
                creationTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            )
        )
        if (!session.isAuthenticated) return@withContext RepoResult.Success(Unit)

        val service = FrappeClientFactory.createService(session)
        val payload = mapOf(
            "title" to title,
            "content" to content,
            "reference_doctype" to doctype,
            "reference_docname" to docname
        )
        runCatching {
            val resp = service.createResource("CRM Note", payload)
            if (resp.isSuccessful) RepoResult.Success(Unit)
            else RepoResult.Error("Saved locally; remote note failed (HTTP ${resp.code()}).")
        }.getOrElse {
            RepoResult.Error("Saved locally; will appear offline.")
        }
    }

    suspend fun toggleTaskDone(task: TaskEntity): RepoResult<Unit> = withContext(Dispatchers.IO) {
        val nextCompleted = !task.isCompleted
        val nextStatus = if (nextCompleted) "Done" else "Todo"
        taskDao.updateCompletion(task.name, nextCompleted, nextStatus)

        val session = credentialStore.getAuthSession()
        if (!session.isAuthenticated) return@withContext RepoResult.Success(Unit)

        val service = FrappeClientFactory.createService(session)
        runCatching {
            val crmResp = service.updateResource("CRM Task", task.name, mapOf("status" to nextStatus))
            if (!crmResp.isSuccessful) {
                val todoStatus = if (nextCompleted) "Closed" else "Open"
                service.updateResource("ToDo", task.name, mapOf("status" to todoStatus))
            }
            RepoResult.Success(Unit)
        }.getOrElse {
            RepoResult.Error("Updated locally; sync retry queued.")
        }
    }

    suspend fun addExcludedNumber(phone: String, label: String) {
        val normalized = PhoneNormalizer.normalize(phone)
        if (normalized.isBlank()) return
        excludedNumberDao.insert(
            ExcludedNumberEntity(
                normalizedPhone = normalized,
                displayPhone = phone.trim(),
                label = label.trim().ifBlank { "Personal" }
            )
        )
    }

    suspend fun removeExcludedNumber(normalizedPhone: String) {
        excludedNumberDao.delete(normalizedPhone)
    }

    suspend fun retryCallLog(callLogId: Long) {
        val log = callLogDao.getById(callLogId) ?: return
        callLogDao.update(log.copy(syncStatus = SyncStatus.PENDING.name, syncError = null))
        enqueueSyncWorker()
    }

    suspend fun syncPendingQueueOnce(): Boolean = withContext(Dispatchers.IO) {
        val session = credentialStore.getAuthSession()
        if (!session.isAuthenticated) return@withContext false
        val service = FrappeClientFactory.createService(session)
        val queue = callLogDao.getUploadQueue()
        if (queue.isEmpty()) return@withContext true

        var allSucceeded = true
        for (item in queue) {
            callLogDao.update(item.copy(syncStatus = SyncStatus.SYNCING.name, syncError = null))
            val result = uploadSingleCallLog(service, session, item)
            if (result.isFailure) {
                allSucceeded = false
                callLogDao.update(
                    item.copy(
                        syncStatus = SyncStatus.FAILED.name,
                        syncError = result.exceptionOrNull()?.localizedMessage ?: "Upload failed",
                        retryCount = item.retryCount + 1
                    )
                )
            }
        }
        allSucceeded
    }

    private suspend fun uploadSingleCallLog(
        service: com.example.data.remote.FrappeApiService,
        session: AuthSession,
        item: CallLogEntity
    ): Result<Unit> = runCatching {
        var uploadedFileUrl: String? = item.remoteAudioUrl
        val localFile = item.localAudioPath?.let { File(it) }

        // Step 1: Upload .m4a recording as Private File (is_private = 1)
        if (uploadedFileUrl.isNullOrBlank() && localFile != null && localFile.exists() && localFile.length() > 0L) {
            val reqFile = localFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", localFile.name, reqFile)
            val isPrivateBody = "1".toRequestBody("text/plain".toMediaTypeOrNull())
            val doctypeBody = item.referenceDoctype?.toRequestBody("text/plain".toMediaTypeOrNull())
            val docnameBody = item.referenceDocname?.toRequestBody("text/plain".toMediaTypeOrNull())
            val folderBody = "Home/Call Recordings".toRequestBody("text/plain".toMediaTypeOrNull())

            val uploadResp = service.uploadPrivateFile(
                file = part,
                isPrivate = isPrivateBody,
                doctype = doctypeBody,
                docname = docnameBody,
                folder = folderBody
            )
            if (!uploadResp.isSuccessful) {
                error("File upload failed (HTTP ${uploadResp.code()})")
            }
            uploadedFileUrl = uploadResp.body()?.message?.fileUrl
                ?: error("Upload succeeded but file_url missing")
        }

        // Step 2: Create CRM Call Log record
        val direction = runCatching { CallDirection.valueOf(item.direction) }
            .getOrDefault(CallDirection.OUTGOING)
        val user = session.loggedInUser.ifBlank { "Rep" }
        val payload = buildMap<String, Any?> {
            put("type", direction.frappeType)
            put("status", if (direction == CallDirection.MISSED) "No Answer" else "Completed")
            put("duration", item.durationSeconds)
            put("from", if (direction == CallDirection.OUTGOING) user else item.phoneNumber)
            put("to", if (direction == CallDirection.OUTGOING) item.phoneNumber else user)
            if (!uploadedFileUrl.isNullOrBlank()) put("recording_url", uploadedFileUrl)
            if (!item.referenceDoctype.isNullOrBlank()) put("reference_doctype", item.referenceDoctype)
            if (!item.referenceDocname.isNullOrBlank()) put("reference_docname", item.referenceDocname)
            if (!item.note.isNullOrBlank()) put("note", item.note)
        }

        val logResp = service.createResource("CRM Call Log", payload)
        if (!logResp.isSuccessful) {
            error("CRM Call Log failed (HTTP ${logResp.code()})")
        }
        val docName = logResp.body()?.data?.str("name").orEmpty()

        // Step 3: Optionally post CRM Note & Follow-Up Task
        if (!item.note.isNullOrBlank() &&
            !item.referenceDoctype.isNullOrBlank() &&
            !item.referenceDocname.isNullOrBlank()
        ) {
            service.createResource(
                "CRM Note",
                mapOf(
                    "title" to (item.disposition ?: "Call Note"),
                    "content" to item.note,
                    "reference_doctype" to item.referenceDoctype,
                    "reference_docname" to item.referenceDocname
                )
            )
        }

        if (!item.nextFollowUpDate.isNullOrBlank()) {
            val taskPayload = buildMap<String, Any?> {
                put("title", "Follow-up Call: ${item.matchedName ?: item.phoneNumber}")
                put("due_date", item.nextFollowUpDate)
                put("status", "Todo")
                put("priority", "High")
                if (!item.referenceDoctype.isNullOrBlank()) put("reference_doctype", item.referenceDoctype)
                if (!item.referenceDocname.isNullOrBlank()) put("reference_docname", item.referenceDocname)
            }
            val taskResp = service.createResource("CRM Task", taskPayload)
            if (!taskResp.isSuccessful) {
                service.createResource(
                    "ToDo",
                    mapOf(
                        "description" to "Follow-up Call: ${item.matchedName ?: item.phoneNumber}",
                        "date" to item.nextFollowUpDate,
                        "status" to "Open",
                        "reference_type" to item.referenceDoctype,
                        "reference_name" to item.referenceDocname
                    )
                )
            }
        }

        // Step 4: Mark synced and delete temporary local .m4a file ONLY after 200 OK
        callLogDao.update(
            item.copy(
                frappeDocName = docName,
                remoteAudioUrl = uploadedFileUrl,
                localAudioPath = null,
                syncStatus = SyncStatus.SYNCED.name,
                syncError = null
            )
        )
        if (localFile != null && localFile.exists()) {
            runCatching { localFile.delete() }
        }
    }

    fun enqueueSyncWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<CallSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "onebook_call_sync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    private fun parseFrappeDate(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss.SSSSSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            runCatching {
                val df = SimpleDateFormat(pattern, Locale.US)
                df.parse(raw)?.time?.let { return it }
            }
        }
        return System.currentTimeMillis()
    }
}
