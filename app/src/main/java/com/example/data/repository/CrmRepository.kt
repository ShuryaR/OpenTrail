package com.example.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.data.local.AppDatabase
import com.example.data.local.AuthMode
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
import com.example.data.remote.FrappeApiService
import com.example.data.remote.FrappeClientFactory
import com.example.data.remote.FrappeLoginRequest
import com.example.data.remote.dbl
import com.example.data.remote.extractFrappeError
import com.example.data.remote.integer
import com.example.data.remote.str
import com.example.util.PhoneNormalizer
import com.example.worker.CallSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
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

    private val _syncStage = MutableStateFlow<String?>(null)
    val syncStageFlow: StateFlow<String?> = _syncStage.asStateFlow()

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
                val rawCookies = resp.headers().values("Set-Cookie")
                val cookiePairs = rawCookies
                    .mapNotNull { header -> header.split(';').firstOrNull()?.trim() }
                    .filter { it.contains('=') }
                val hasSid = cookiePairs.any { it.startsWith("sid=") && !it.endsWith("=Guest") }
                if (!hasSid) {
                    return@withContext RepoResult.Error("Login succeeded but no valid session cookie (sid) was returned.")
                }
                val combinedCookies = cookiePairs.joinToString("; ")
                val tempSession = AuthSession(
                    baseUrl = cleanUrl,
                    sessionCookie = combinedCookies,
                    authMode = AuthMode.SESSION_COOKIE
                )
                val authedService = FrappeClientFactory.createService(tempSession)
                val csrfToken = fetchCsrfToken(authedService)
                val resolvedUser = runCatching {
                    authedService.getLoggedUser().body()?.message?.takeIf { it.isNotBlank() }
                }.getOrNull() ?: email.trim()

                credentialStore.saveSessionCredentials(
                    baseUrl = cleanUrl,
                    sessionCookie = combinedCookies,
                    csrfToken = csrfToken,
                    loggedInUser = resolvedUser
                )
                RepoResult.Success(resolvedUser)
            } else {
                val detail = extractFrappeError(resp.code(), resp.errorBody()?.string())
                RepoResult.Error("Invalid Frappe credentials ($detail).")
            }
        }.getOrElse { e ->
            RepoResult.Error(e.localizedMessage ?: "Connection error reaching Frappe server.")
        }
    }

    private suspend fun fetchCsrfToken(service: FrappeApiService): String {
        val regex = Regex("""csrf_token["']?\s*[:=]\s*["']([a-fA-F0-9]{16,})["']""")
        for (fetcher in listOf<suspend () -> retrofit2.Response<okhttp3.ResponseBody>>(
            { service.fetchCrmPage() },
            { service.fetchAppPage() }
        )) {
            val body = runCatching {
                val r = fetcher()
                if (r.isSuccessful) r.body()?.string() else null
            }.getOrNull()
            if (!body.isNullOrBlank()) {
                val match = regex.find(body)?.groupValues?.getOrNull(1)
                if (!match.isNullOrBlank()) return match
            }
        }
        return ""
    }

    suspend fun refreshAllPipeline(): RepoResult<Unit> = withContext(Dispatchers.IO) {
        val session = credentialStore.getAuthSession()
        if (!session.isAuthenticated) {
            return@withContext RepoResult.Error("Not authenticated.")
        }
        val service = FrappeClientFactory.createService(session)

        runCatching {
            _syncStage.value = "Syncing CRM Leads…"
            // 1. Leads (CRM Lead with Lead fallback)
            val leadRows = listAllPaged(service, "CRM Lead", fallbackDoctype = "Lead")
            if (leadRows != null) {
                val leads = leadRows.map { row ->
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

            _syncStage.value = "Syncing Deals & Contacts…"
            // 2. Deals (CRM Deal with Opportunity fallback)
            val dealRows = listAllPaged(service, "CRM Deal", fallbackDoctype = "Opportunity")
            if (dealRows != null) {
                val deals = dealRows.map { row ->
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
            val contactRows = listAllPaged(service, "Contact")
            if (contactRows != null) {
                val contacts = contactRows.map { row ->
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
            val taskRows = listAllPaged(
                service, "CRM Task", orderBy = "due_date asc",
                fallbackDoctype = "ToDo", fallbackOrderBy = "date asc"
            )
            if (taskRows != null) {
                val tasks = taskRows.map { row ->
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

            _syncStage.value = "Syncing Tasks & Call Logs…"
            // 5. Recent CRM Call Logs
            // Only the signed-in rep's own calls. The CRM lets every Sales User read every call log, so an
            // unfiltered list would show (and count) colleagues' calls as this rep's.
            val me = session.loggedInUser.trim()
            val since = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(Date(System.currentTimeMillis() - CALL_HISTORY_DAYS * 86_400_000L))
            val asCaller = if (me.isBlank()) null else
                listAllPaged(service, "CRM Call Log", orderBy = "creation desc", filters = ownCallLogFilters("caller", me, since))
            val asReceiver = if (me.isBlank()) null else
                listAllPaged(service, "CRM Call Log", orderBy = "creation desc", filters = ownCallLogFilters("receiver", me, since))
            // If either fetch failed, keep the cache as it is rather than wiping it.
            if (asCaller != null && asReceiver != null) {
                val ownRows = (asCaller + asReceiver).distinctBy { it.str("name") }
                val remoteLogs = ownRows.mapNotNull { row ->
                    val docName = row.str("name")
                    if (docName.isBlank()) return@mapNotNull null
                    val type = row.str("type")
                    val caller = row.str("from", "caller")
                    val receiver = row.str("to", "receiver")
                    val num = if (type.equals("Incoming", true)) caller else receiver.ifBlank { caller }
                    // Server rows carry no display name: resolve it from the CRM cache refreshed above.
                    val match = matchPhoneToCrm(num)
                    CallLogEntity(
                        frappeDocName = docName,
                        phoneNumber = num,
                        normalizedPhone = PhoneNormalizer.normalize(num),
                        direction = when {
                            // An incoming call nobody answered is a missed call, not a normal incoming one.
                            type.equals("Incoming", true) && row.str("status").equals("No Answer", true) ->
                                CallDirection.MISSED.name
                            type.equals("Incoming", true) -> CallDirection.INCOMING.name
                            else -> CallDirection.OUTGOING.name
                        },
                        durationSeconds = row.integer("duration"),
                        timestampMs = parseFrappeDate(row.str("creation", "start_time")),
                        remoteAudioUrl = row.str("recording_url"),
                        referenceDoctype = row.str("reference_doctype").ifBlank { null } ?: match?.doctype,
                        referenceDocname = row.str("reference_docname").ifBlank { null } ?: match?.docname,
                        matchedName = match?.displayName,
                        matchedCompany = match?.company,
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
            _syncStage.value = null
            RepoResult.Success(Unit)
        }.getOrElse { e ->
            _syncStage.value = null
            RepoResult.Error(e.localizedMessage ?: "Failed to sync Frappe CRM records.")
        }
    }

    /**
     * Frappe caps list responses (default 20), so fetch every page. Returns null when neither the
     * CRM doctype nor its classic ERPNext fallback is readable. Capped to keep sync bounded.
     */
    private suspend fun listAllPaged(
        service: FrappeApiService,
        doctype: String,
        orderBy: String = "modified desc",
        fallbackDoctype: String? = null,
        fallbackOrderBy: String = "modified desc",
        filters: String? = null
    ): List<Map<String, Any?>>? {
        suspend fun fetch(dt: String, order: String): List<Map<String, Any?>>? {
            val all = mutableListOf<Map<String, Any?>>()
            var start = 0
            while (all.size < MAX_SYNC_ROWS) {
                val resp = service.listResources(dt, limit = PAGE_SIZE, start = start, orderBy = order, filters = filters)
                if (!resp.isSuccessful) return if (start == 0) null else all
                val page = resp.body()?.data.orEmpty()
                all += page
                if (page.size < PAGE_SIZE) break
                start += PAGE_SIZE
            }
            return all
        }
        return fetch(doctype, orderBy)
            ?: fallbackDoctype?.let { fetch(it, fallbackOrderBy) }
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

        val (_, service) = ensureCsrfService(session)
        val payload = mapOf(
            "title" to title,
            "content" to content,
            "reference_doctype" to doctype,
            "reference_docname" to docname
        )
        runCatching {
            val resp = service.createResource("CRM Note", payload)
            if (resp.isSuccessful) {
                RepoResult.Success(Unit)
            } else if (resp.code() == 404) {
                val fallbackResp = service.createResource(
                    "Note",
                    mapOf("title" to "$title ($docname)", "content" to content, "public" to 0)
                )
                if (fallbackResp.isSuccessful) {
                    RepoResult.Success(Unit)
                } else {
                    val detail = extractFrappeError(fallbackResp.code(), fallbackResp.errorBody()?.string())
                    RepoResult.Error("Saved locally; remote note failed ($detail).")
                }
            } else {
                val detail = extractFrappeError(resp.code(), resp.errorBody()?.string())
                RepoResult.Error("Saved locally; remote note failed ($detail).")
            }
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

        val (_, service) = ensureCsrfService(session)
        runCatching {
            val crmResp = service.updateResource("CRM Task", task.name, mapOf("status" to nextStatus))
            if (crmResp.isSuccessful) {
                RepoResult.Success(Unit)
            } else if (crmResp.code() == 404) {
                val todoStatus = if (nextCompleted) "Closed" else "Open"
                val todoResp = service.updateResource("ToDo", task.name, mapOf("status" to todoStatus))
                if (todoResp.isSuccessful) RepoResult.Success(Unit)
                else RepoResult.Error("Task update failed (${extractFrappeError(todoResp.code(), todoResp.errorBody()?.string())})")
            } else {
                RepoResult.Error("Task update failed (${extractFrappeError(crmResp.code(), crmResp.errorBody()?.string())})")
            }
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

    private suspend fun ensureCsrfService(initialSession: AuthSession): Pair<AuthSession, FrappeApiService> {
        var session = initialSession
        var service = FrappeClientFactory.createService(session)
        if (session.authMode == AuthMode.SESSION_COOKIE && session.csrfToken.isBlank()) {
            val refreshedToken = fetchCsrfToken(service)
            if (refreshedToken.isNotBlank()) {
                credentialStore.saveSessionCredentials(
                    baseUrl = session.baseUrl,
                    sessionCookie = session.sessionCookie,
                    csrfToken = refreshedToken,
                    loggedInUser = session.loggedInUser
                )
                session = session.copy(csrfToken = refreshedToken)
                service = FrappeClientFactory.createService(session)
            }
        }
        return session to service
    }

    suspend fun syncPendingQueueOnce(): Boolean = withContext(Dispatchers.IO) {
        val initialSession = credentialStore.getAuthSession()
        if (!initialSession.isAuthenticated) return@withContext false
        val (session, service) = ensureCsrfService(initialSession)
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
        _syncStage.value = null
        allSucceeded
    }

    private suspend fun uploadSingleCallLog(
        service: FrappeApiService,
        session: AuthSession,
        item: CallLogEntity
    ): Result<Unit> = runCatching {
        var uploadedFileUrl: String? = item.remoteAudioUrl
        val localFile = item.localAudioPath?.let { File(it) }

        // Step 1: Upload .m4a recording as Private File (is_private = 1)
        // Uses Home root (never a nonexistent subfolder) and falls back to unattached upload if parent doc rejects attach.
        if (uploadedFileUrl.isNullOrBlank() && localFile != null && localFile.exists() && localFile.length() > 0L) {
            _syncStage.value = "Uploading call recording…"
            val reqFile = localFile.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val filePart = MultipartBody.Part.createFormData("file", localFile.name, reqFile)
            val isPrivatePart = MultipartBody.Part.createFormData("is_private", "1")

            val hasRef = !item.referenceDoctype.isNullOrBlank() && !item.referenceDocname.isNullOrBlank()
            val primaryParts = buildList {
                add(filePart)
                add(isPrivatePart)
                if (hasRef) {
                    add(MultipartBody.Part.createFormData("doctype", item.referenceDoctype!!))
                    add(MultipartBody.Part.createFormData("docname", item.referenceDocname!!))
                }
            }

            var uploadResp = service.uploadPrivateFile(primaryParts)
            if (!uploadResp.isSuccessful && hasRef) {
                // Retry standalone private upload if parent doctype/docname denied attachment
                val retryFilePart = MultipartBody.Part.createFormData("file", localFile.name, reqFile)
                val retryPrivatePart = MultipartBody.Part.createFormData("is_private", "1")
                uploadResp = service.uploadPrivateFile(listOf(retryFilePart, retryPrivatePart))
            }
            if (!uploadResp.isSuccessful) {
                val errDetail = extractFrappeError(uploadResp.code(), uploadResp.errorBody()?.string())
                error("Recording upload failed ($errDetail)")
            }
            uploadedFileUrl = uploadResp.body()?.message?.fileUrl
                ?: error("Upload succeeded but file_url missing")
        }

        // Step 2: Create CRM Note FIRST so CRM Call Log's `note` Link field receives a valid CRM Note docname
        var createdNoteDocName: String? = null
        val supportsCrmNoteRef = item.referenceDoctype in setOf("CRM Lead", "CRM Deal", "Lead", "Opportunity")
        if (!item.note.isNullOrBlank() && supportsCrmNoteRef && !item.referenceDocname.isNullOrBlank()) {
            _syncStage.value = "Creating CRM Note…"
            val noteResp = service.createResource(
                "CRM Note",
                mapOf(
                    "title" to (item.disposition ?: "Call Note"),
                    "content" to item.note,
                    "reference_doctype" to item.referenceDoctype,
                    "reference_docname" to item.referenceDocname
                )
            )
            if (noteResp.isSuccessful) {
                createdNoteDocName = noteResp.body()?.data?.str("name")?.ifBlank { null }
            }
        }

        // Step 3: Create CRM Call Log (with safe retry & classic ERPNext Call Log fallback)
        val direction = runCatching { CallDirection.valueOf(item.direction) }
            .getOrDefault(CallDirection.OUTGOING)
        val user = session.loggedInUser.trim()
        val hasValidUser = user.isNotBlank() && !user.equals("Rep", ignoreCase = true)

        val dtFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val startTimeStr = dtFormatter.format(Date(item.timestampMs))
        val endTimeStr = dtFormatter.format(Date(item.timestampMs + (item.durationSeconds * 1000L)))
        val callUniqueId = "onebook-${item.id}-${item.timestampMs}"

        // NOTE: Frappe requires `from`/`to` and validates them as Phone Numbers, so never put the
        // rep's email/name in them (HTTP 417). The rep is identified via `caller`/`receiver`.
        val baseCallPayload = buildCallLogBasePayload(
            callUniqueId = callUniqueId,
            direction = direction,
            customerNumber = item.phoneNumber,
            repNumber = com.example.util.SimManager.ownNumberFor(context, item.simSlotIndex),
            durationSeconds = item.durationSeconds,
            startTime = startTimeStr,
            endTime = endTimeStr,
            recordingUrl = uploadedFileUrl
        )

        val fullCrmPayload = buildMap<String, Any?> {
            putAll(baseCallPayload)
            if (hasValidUser) {
                if (direction == CallDirection.OUTGOING) put("caller", user)
                else put("receiver", user)
            }
            if (!item.referenceDoctype.isNullOrBlank() && !item.referenceDocname.isNullOrBlank()) {
                put("reference_doctype", item.referenceDoctype)
                put("reference_docname", item.referenceDocname)
            }
            if (!createdNoteDocName.isNullOrBlank()) {
                put("note", createdNoteDocName)
            }
        }

        // A wrap-up edit of a call already in Frappe must patch it: re-creating would reuse the same
        // unique `id` and be rejected as a duplicate.
        val docName: String
        if (item.frappeDocName.isNotBlank()) {
            val patch = buildMap<String, Any?> {
                if (!createdNoteDocName.isNullOrBlank()) put("note", createdNoteDocName)
                if (!item.referenceDoctype.isNullOrBlank() && !item.referenceDocname.isNullOrBlank()) {
                    put("reference_doctype", item.referenceDoctype)
                    put("reference_docname", item.referenceDocname)
                }
            }
            if (patch.isNotEmpty()) {
                service.updateResource("CRM Call Log", item.frappeDocName, patch)
            }
            docName = item.frappeDocName
        } else {
            _syncStage.value = "Logging call in Frappe CRM…"
            var logResp = service.createResource("CRM Call Log", fullCrmPayload)
            if (!logResp.isSuccessful && logResp.code() in setOf(400, 417)) {
                // Retry with minimal core fields in case site customized/removed optional link fields
                logResp = service.createResource("CRM Call Log", baseCallPayload)
            }
            if (!logResp.isSuccessful && logResp.code() == 404) {
                // Fallback to classic ERPNext `Call Log` DocType
                val classicPayload = buildMap<String, Any?> {
                    putAll(baseCallPayload)
                    if (!item.note.isNullOrBlank()) put("summary", item.note)
                }
                logResp = service.createResource("Call Log", classicPayload)
            }

            if (logResp.code() == 409) {
                // Already in the CRM: an earlier attempt created it but the phone never saw the reply.
                // Its name is our call id, so adopt it instead of failing forever, and fill in any
                // note or link added since.
                docName = callUniqueId
                val patch = buildMap<String, Any?> {
                    if (!createdNoteDocName.isNullOrBlank()) put("note", createdNoteDocName)
                    if (!uploadedFileUrl.isNullOrBlank()) put("recording_url", uploadedFileUrl)
                    if (!item.referenceDoctype.isNullOrBlank() && !item.referenceDocname.isNullOrBlank()) {
                        put("reference_doctype", item.referenceDoctype)
                        put("reference_docname", item.referenceDocname)
                    }
                }
                if (patch.isNotEmpty()) runCatching { service.updateResource("CRM Call Log", docName, patch) }
            } else {
                if (!logResp.isSuccessful) {
                    val errDetail = extractFrappeError(logResp.code(), logResp.errorBody()?.string())
                    error("CRM Call Log failed ($errDetail)")
                }
                docName = logResp.body()?.data?.str("name").orEmpty()
            }
        }

        // Step 4: Optionally schedule Follow-Up Task
        if (!item.nextFollowUpDate.isNullOrBlank()) {
            val taskPayload = buildMap<String, Any?> {
                put("title", "Follow-up Call: ${item.matchedName ?: item.phoneNumber}")
                put("due_date", item.nextFollowUpDate)
                put("status", "Todo")
                put("priority", "High")
                if (hasValidUser) put("assigned_to", user)
                if (!item.referenceDoctype.isNullOrBlank() && !item.referenceDocname.isNullOrBlank()) {
                    put("reference_doctype", item.referenceDoctype)
                    put("reference_docname", item.referenceDocname)
                }
            }
            val taskResp = service.createResource("CRM Task", taskPayload)
            if (!taskResp.isSuccessful) {
                val todoPayload = buildMap<String, Any?> {
                    put("description", "Follow-up Call: ${item.matchedName ?: item.phoneNumber}")
                    put("date", item.nextFollowUpDate)
                    put("status", "Open")
                    if (hasValidUser) put("allocated_to", user)
                    if (!item.referenceDoctype.isNullOrBlank() && !item.referenceDocname.isNullOrBlank()) {
                        put("reference_type", item.referenceDoctype)
                        put("reference_name", item.referenceDocname)
                    }
                }
                service.createResource("ToDo", todoPayload)
            }
        }

        // Step 5: Mark synced and delete temporary local .m4a file ONLY after 200 OK
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

    /**
     * Advances a lead's status after a call (e.g. New -> Contacted). Best effort: the local cache is
     * updated first so the UI reflects it, and a CRM failure is reported without losing the call.
     */
    suspend fun updateLeadStatus(docname: String, status: String): RepoResult<Unit> = withContext(Dispatchers.IO) {
        leadDao.updateStatus(docname, status)
        val session = credentialStore.getAuthSession()
        if (!session.isAuthenticated) return@withContext RepoResult.Success(Unit)
        val (_, service) = ensureCsrfService(session)
        runCatching {
            val resp = service.updateResource("CRM Lead", docname, mapOf("status" to status))
            if (resp.isSuccessful) RepoResult.Success(Unit)
            else RepoResult.Error("Lead status saved locally; CRM said ${extractFrappeError(resp.code(), resp.errorBody()?.string())}")
        }.getOrElse { RepoResult.Error("Lead status saved locally; will not reach CRM offline.") }
    }

    /** Creates a CRM Lead for an unknown caller and links the call to it. */
    suspend fun createLeadFromCall(callLogId: Long, fullName: String): RepoResult<String> = withContext(Dispatchers.IO) {
        val log = callLogDao.getById(callLogId) ?: return@withContext RepoResult.Error("Call not found.")
        val session = credentialStore.getAuthSession()
        if (!session.isAuthenticated) return@withContext RepoResult.Error("Sign in first.")
        val (_, service) = ensureCsrfService(session)
        val cleanName = fullName.trim()
        val first = cleanName.substringBefore(' ')
        val last = cleanName.substringAfter(' ', "")
        runCatching {
            val payload = buildMap<String, Any?> {
                put("first_name", first)
                if (last.isNotBlank()) put("last_name", last)
                put("mobile_no", log.phoneNumber)
                put("status", "New")
            }
            val resp = service.createResource("CRM Lead", payload)
            if (!resp.isSuccessful) {
                return@withContext RepoResult.Error(
                    "Could not create lead (${extractFrappeError(resp.code(), resp.errorBody()?.string())})."
                )
            }
            val docname = resp.body()?.data?.str("name").orEmpty()
            if (docname.isBlank()) return@withContext RepoResult.Error("CRM did not return a lead id.")
            leadDao.upsertAll(
                listOf(
                    LeadEntity(
                        name = docname, leadName = cleanName, organization = "", status = "New",
                        email = "", phone = "", mobileNo = log.phoneNumber,
                        normalizedPhone = PhoneNormalizer.normalize(log.phoneNumber),
                        source = "", lastNote = "", modified = ""
                    )
                )
            )
            callLogDao.update(
                log.copy(referenceDoctype = "CRM Lead", referenceDocname = docname, matchedName = cleanName)
            )
            if (log.frappeDocName.isNotBlank()) {
                runCatching {
                    service.updateResource(
                        "CRM Call Log", log.frappeDocName,
                        mapOf("reference_doctype" to "CRM Lead", "reference_docname" to docname)
                    )
                }
            }
            RepoResult.Success(docname)
        }.getOrElse { RepoResult.Error("Could not reach Frappe to create the lead.") }
    }

    suspend fun leadStatusOf(docname: String): String? = leadDao.getByName(docname)?.status

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

    companion object {
        private const val PAGE_SIZE = 500
        private const val MAX_SYNC_ROWS = 5000
        private const val CALL_HISTORY_DAYS = 30L

        /**
         * Frappe `filters` JSON for one rep's calls on one side of the call ("caller" or "receiver")
         * since [sinceDate] (yyyy-MM-dd). [user] is a Frappe user id, usually an email.
         */
        internal fun ownCallLogFilters(field: String, user: String, sinceDate: String): String {
            fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
            return "[[\"${esc(field)}\",\"=\",\"${esc(user)}\"],[\"creation\",\">=\",\"${esc(sinceDate)}\"]]"
        }

        /**
         * "No Answer" for a missed call and for an outgoing call that never connected (0 seconds),
         * otherwise "Completed". Frappe's other statuses (Busy, Failed) cannot be told apart from
         * the phone's call history.
         */
        internal fun callLogStatus(direction: CallDirection, durationSeconds: Int): String = when {
            direction == CallDirection.MISSED -> "No Answer"
            direction == CallDirection.OUTGOING && durationSeconds <= 0 -> "No Answer"
            else -> "Completed"
        }

        /**
         * Builds the minimal CRM Call Log payload. `from`/`to` are both required and phone-validated
         * by Frappe: the customer number on one side, the rep's SIM number (or placeholder) on the other.
         */
        internal fun buildCallLogBasePayload(
            callUniqueId: String,
            direction: CallDirection,
            customerNumber: String,
            repNumber: String,
            durationSeconds: Int,
            startTime: String,
            endTime: String,
            recordingUrl: String?
        ): Map<String, Any?> = buildMap {
            // Both ends are written as +<country code><number>, whatever format Android or the CRM used.
            val customer = PhoneNormalizer.toDialable(customerNumber).ifBlank { customerNumber }
            val rep = PhoneNormalizer.toDialable(repNumber).ifBlank { repNumber }
            put("id", callUniqueId)
            put("type", direction.frappeType)
            put("status", callLogStatus(direction, durationSeconds))
            put("duration", durationSeconds)
            put("start_time", startTime)
            put("end_time", endTime)
            if (direction == CallDirection.OUTGOING) {
                put("from", rep)
                put("to", customer)
            } else {
                put("from", customer)
                put("to", rep)
            }
            if (!recordingUrl.isNullOrBlank()) put("recording_url", recordingUrl)
        }
    }
}
