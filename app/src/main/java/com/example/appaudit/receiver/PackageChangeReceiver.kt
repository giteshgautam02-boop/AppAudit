package com.example.appaudit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.appaudit.worker.UninstallCheckWorker

/**
 * Fires as soon as ANY app is installed/removed/updated on the device,
 * so an uninstall gets logged immediately instead of waiting up to
 * 15 minutes for the next periodic scan.
 */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                val request = OneTimeWorkRequestBuilder<UninstallCheckWorker>().build()
                WorkManager.getInstance(context).enqueue(request)
            }
        }
    }
}
