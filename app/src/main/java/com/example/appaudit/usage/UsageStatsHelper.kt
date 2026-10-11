package com.example.appaudit.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
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

    /**
     * Collects per-package activity between [startTime] and [endTime] from raw events,
     * the aggregated stats and the daily / weekly / monthly interval buckets.
     * Packages that were removed from the phone usually still show up here.
     */
    fun queryActivity(context: Context, startTime: Long, endTime: Long): Map<String, AppActivity> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

        val first = HashMap<String, Long>()
        val last = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()
        val duration = HashMap<String, Long>()
        val count = HashMap<String, Int>()

        try {
            val events = usm.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                val ts = event.timeStamp
                count[pkg] = (count[pkg] ?: 0) + 1
                first.putIfAbsent(pkg, ts)
                last[pkg] = maxOf(last[pkg] ?: 0L, ts)
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> resumedAt[pkg] = ts
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val begin = resumedAt.remove(pkg)
                        if (begin != null && ts > begin) {
                            duration[pkg] = (duration[pkg] ?: 0L) + (ts - begin)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // fall through to the aggregated stats below
        }

        fun merge(stats: Collection<UsageStats>) {
            for (st in stats) {
                val pkg = st.packageName ?: continue
                val used = st.lastTimeUsed
                if (used < startTime && !count.containsKey(pkg)) continue
                if (used >= startTime) {
                    last[pkg] = maxOf(last[pkg] ?: 0L, used)
                    val begin = if (st.firstTimeStamp in startTime..used) st.firstTimeStamp else used
                    first[pkg] = minOf(first[pkg] ?: Long.MAX_VALUE, begin)
                }
                duration[pkg] = maxOf(duration[pkg] ?: 0L, st.totalTimeInForeground)
            }
        }

        try {
            merge(usm.queryAndAggregateUsageStats(startTime, endTime).values)
        } catch (e: Exception) {
        }
        val intervals = intArrayOf(
            UsageStatsManager.INTERVAL_DAILY,
            UsageStatsManager.INTERVAL_WEEKLY,
            UsageStatsManager.INTERVAL_MONTHLY
        )
        for (interval in intervals) {
            try {
                usm.queryUsageStats(interval, startTime, endTime)?.let { merge(it) }
            } catch (e: Exception) {
            }
        }

        return (first.keys + last.keys).associateWith { pkg ->
            AppActivity(
                packageName = pkg,
                firstActive = first[pkg] ?: last[pkg] ?: 0L,
                lastActive = last[pkg] ?: first[pkg] ?: 0L,
                activeDurationMs = duration[pkg] ?: 0L,
                eventCount = count[pkg] ?: 0
            )
        }
    }
}
