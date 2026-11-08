package dev.kyouzo.solace.data.remote

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = SyncRepository(applicationContext)
        if (repo.session() == null) return Result.success()
        return repo.syncNow().fold({ Result.success() }, { if (runAttemptCount < 5) Result.retry() else Result.failure() })
    }
}

object SyncScheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Batches captured points: at most one pending upload, delayed so several points go out together. */
    fun kick(context: Context, delayMinutes: Long = 2) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).setInitialDelay(delayMinutes, TimeUnit.MINUTES).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork("sync-now", ExistingWorkPolicy.KEEP, request)
    }

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(online).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork("sync-periodic", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
