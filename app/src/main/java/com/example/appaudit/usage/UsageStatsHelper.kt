package com.example.appaudit.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

object UsageStatsHelper {

    /** True once the user has granted "Usage access" to this app. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName
            )
        }
        // Many phones report MODE_DEFAULT even after the user enabled access,
        // so in that case fall back to the real permission check.
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    /** Opens the usage access screen (directly on this app's toggle when the phone supports it). */
    fun openUsageAccessSettings(context: Context) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    data class AppActivity(
        val packageName: String,
        val firstActive: Long,
        val lastActive: Long,
        val activeDurationMs: Long,
        val eventCount: Int
    )

    /** Replays foreground/background events and summarizes per-package activity (OS keeps ~30 days). */
    fun queryActivity(context: Context, startTime: Long, endTime: Long): Map<String, AppActivity> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        val firstActive = HashMap<String, Long>()
        val lastActive = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()
        val activeDuration = HashMap<String, Long>()
        val eventCount = HashMap<String, Int>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    eventCount[pkg] = (eventCount[pkg] ?: 0) + 1
                    firstActive.putIfAbsent(pkg, event.timeStamp)
                    lastActive[pkg] = event.timeStamp

                    if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                        resumedAt[pkg] = event.timeStamp
                    } else {
                        val start = resumedAt.remove(pkg)
                        if (start != null && event.timeStamp > start) {
                            activeDuration[pkg] = (activeDuration[pkg] ?: 0) + (event.timeStamp - start)
                        }
                    }
                }
            }
        }

        return firstActive.keys.associateWith { pkg ->
            AppActivity(
                packageName = pkg,
                firstActive = firstActive[pkg] ?: 0L,
                lastActive = lastActive[pkg] ?: 0L,
                activeDurationMs = activeDuration[pkg] ?: 0L,
                eventCount = eventCount[pkg] ?: 0
            )
        }
    }
}
