package com.example.appaudit.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.appaudit.LiveLog
import com.example.appaudit.worker.UninstallCheckWorker

/** Logs install/uninstall/update events live and triggers an immediate audit scan. */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pkg = intent.data?.schemeSpecificPart
        val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)

        when (intent.action) {
            Intent.ACTION_PACKAGE_ADDED ->
                if (!replacing && pkg != null) LiveLog.add(context, "INSTALLED", pkg)
            Intent.ACTION_PACKAGE_REPLACED ->
                if (pkg != null) LiveLog.add(context, "UPDATED", pkg)
            Intent.ACTION_PACKAGE_FULLY_REMOVED ->
                if (pkg != null) LiveLog.add(context, "UNINSTALLED", pkg)
            else -> Unit
        }

        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<UninstallCheckWorker>().build())
    }
}
