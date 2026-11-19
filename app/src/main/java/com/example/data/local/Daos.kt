package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LeadDao {
    @Query("SELECT * FROM leads ORDER BY modified DESC")
    fun observeAll(): Flow<List<LeadEntity>>

    @Query("SELECT * FROM leads WHERE name = :name LIMIT 1")
    fun observeByName(name: String): Flow<LeadEntity?>

    @Query("SELECT * FROM leads WHERE normalizedPhone = :normalizedPhone LIMIT 1")
    suspend fun findByNormalizedPhone(normalizedPhone: String): LeadEntity?

    @Query("SELECT * FROM leads WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): LeadEntity?

    @Query("UPDATE leads SET status = :status WHERE name = :name")
    suspend fun updateStatus(name: String, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<LeadEntity>)

    @Query("DELETE FROM leads")
    suspend fun clearAll()
}

@Dao
interface DealDao {
    @Query("SELECT * FROM deals ORDER BY modified DESC")
    fun observeAll(): Flow<List<DealEntity>>

    @Query("SELECT * FROM deals WHERE name = :name LIMIT 1")
    fun observeByName(name: String): Flow<DealEntity?>

    @Query("SELECT * FROM deals WHERE normalizedPhone = :normalizedPhone LIMIT 1")
    suspend fun findByNormalizedPhone(normalizedPhone: String): DealEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DealEntity>)

    @Query("DELETE FROM deals")
    suspend fun clearAll()
}

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY fullName ASC")
    fun observeAll(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE name = :name LIMIT 1")
    fun observeByName(name: String): Flow<ContactEntity?>

    @Query("SELECT * FROM contacts WHERE normalizedPhone = :normalizedPhone LIMIT 1")
    suspend fun findByNormalizedPhone(normalizedPhone: String): ContactEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ContactEntity>)

    @Query("DELETE FROM contacts")
    suspend fun clearAll()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueDate ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("UPDATE tasks SET isCompleted = :completed, status = :status WHERE name = :name")
    suspend fun updateCompletion(name: String, completed: Boolean, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TaskEntity)

    @Query("DELETE FROM tasks")
    suspend fun clearAll()
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE syncStatus IN ('PENDING', 'FAILED', 'SYNCING') ORDER BY timestampMs DESC")
    fun observePendingOrFailed(): Flow<List<CallLogEntity>>

    @Query("SELECT COUNT(*) FROM call_logs WHERE syncStatus IN ('PENDING', 'FAILED', 'SYNCING')")
    fun observePendingCount(): Flow<Int>

    @Query(
        "SELECT * FROM call_logs WHERE " +
            "(referenceDoctype = :doctype AND referenceDocname = :docname) " +
            "OR (:normalizedPhone != '' AND normalizedPhone = :normalizedPhone) " +
            "ORDER BY timestampMs DESC"
    )
    fun observeForRecord(doctype: String, docname: String, normalizedPhone: String): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CallLogEntity?

    /** Calls left mid-upload (app killed, phone restarted) go back in the queue. */
    @Query("UPDATE call_logs SET syncStatus = 'PENDING' WHERE syncStatus = 'SYNCING'")
    suspend fun resetStuckSyncing()

    @Query("SELECT * FROM call_logs WHERE syncStatus IN ('PENDING', 'FAILED') ORDER BY timestampMs ASC")
    suspend fun getUploadQueue(): List<CallLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CallLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<CallLogEntity>)

    @Update
    suspend fun update(log: CallLogEntity)

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM call_logs WHERE syncStatus = 'SYNCED' AND frappeDocName != ''")
    suspend fun clearSyncedRemoteCache()
}

@Dao
interface ExcludedNumberDao {
    @Query("SELECT * FROM excluded_numbers ORDER BY label ASC")
    fun observeAll(): Flow<List<ExcludedNumberEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM excluded_numbers WHERE normalizedPhone = :normalizedPhone)")
    suspend fun isExcluded(normalizedPhone: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExcludedNumberEntity)

    @Query("DELETE FROM excluded_numbers WHERE normalizedPhone = :normalizedPhone")
    suspend fun delete(normalizedPhone: String)
}

@Dao
interface CrmNoteDao {
    @Query(
        "SELECT * FROM crm_notes WHERE referenceDoctype = :doctype AND referenceDocname = :docname " +
            "ORDER BY id DESC"
    )
    fun observeForRecord(doctype: String, docname: String): Flow<List<CrmNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: CrmNoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<CrmNoteEntity>)

    @Query("DELETE FROM crm_notes WHERE referenceDoctype = :doctype AND referenceDocname = :docname")
    suspend fun clearForRecord(doctype: String, docname: String)
}
