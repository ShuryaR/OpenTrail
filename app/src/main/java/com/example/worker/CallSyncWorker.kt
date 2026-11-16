package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.OneBookApplication

class CallSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? OneBookApplication ?: return Result.failure()
        val ok = app.repository.syncPendingQueueOnce()
        return if (ok) Result.success() else Result.retry()
    }
}
