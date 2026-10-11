package com.example.appaudit

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.appaudit.worker.UninstallCheckWorker
import java.util.concurrent.TimeUnit

class AppAuditApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 15 minutes is WorkManager's minimum interval for periodic work.
        val periodicRequest = PeriodicWorkRequestBuilder<UninstallCheckWorker>(15, TimeUnit.MINUTES).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "uninstall_audit_scan",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }
}
