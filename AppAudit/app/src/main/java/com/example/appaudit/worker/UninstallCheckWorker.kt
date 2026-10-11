package com.example.appaudit.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.appaudit.repository.InstalledAppsRepository

class UninstallCheckWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        InstalledAppsRepository(applicationContext).runAuditScan()
        Result.success()
    } catch (e: Exception) {
        Result.retry()
    }
}
