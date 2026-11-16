package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LeadEntity::class,
        DealEntity::class,
        ContactEntity::class,
        TaskEntity::class,
        CallLogEntity::class,
        ExcludedNumberEntity::class,
        CrmNoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun leadDao(): LeadDao
    abstract fun dealDao(): DealDao
    abstract fun contactDao(): ContactDao
    abstract fun taskDao(): TaskDao
    abstract fun callLogDao(): CallLogDao
    abstract fun excludedNumberDao(): ExcludedNumberDao
    abstract fun crmNoteDao(): CrmNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "onebook_crm_dialer.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
