package com.example.appaudit.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

object UsageStatsHelper {

    /** True once the user has manually granted "Usage access" to this app. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Intent that opens the system screen where the user turns usage access ON. */
    fun usageAccessSettingsIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    data class AppActivity(
        val packageName: String,
        val firstActive: Long,
        val lastActive: Long,
        val activeDurationMs: Long,
        val eventCount: Int
    )

    /**
     * Replays raw foreground/background events between [startTime] and [endTime]
     * (epoch millis) and summarizes per-package activity.
     *
     * Note: regardless of the window you ask for, the OS only retains roughly
     * 30 days of this history on most devices.
     */
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
