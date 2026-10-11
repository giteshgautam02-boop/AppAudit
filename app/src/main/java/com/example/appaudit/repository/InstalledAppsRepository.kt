package com.example.appaudit.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.appaudit.data.AppDatabase
import com.example.appaudit.data.KnownPackage
import com.example.appaudit.data.UninstalledApp
import com.example.appaudit.usage.UsageStatsHelper

class InstalledAppsRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).trackedAppDao()
    private val pm = context.packageManager

    private fun currentlyInstalled(): Map<String, String> {
        val apps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }
        return apps
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 } // skip preloaded system apps
            .associate { it.packageName to it.loadLabel(pm).toString() }
    }

    /**
     * The actual "audit" step:
     *  1. Compare the live install list against the last saved snapshot.
     *  2. Anything that vanished gets looked up in UsageStatsManager history
     *     and written to uninstalled_apps.
     *  3. The snapshot is refreshed with whatever is newly installed.
     */
    suspend fun runAuditScan() {
        val known = dao.getAllKnownPackages().associateBy { it.packageName }
        val current = currentlyInstalled()

        val disappeared = known.keys - current.keys
        if (disappeared.isNotEmpty()) {
            val now = System.currentTimeMillis()
            val lookbackStart = now - 30L * 24 * 60 * 60 * 1000 // 30-day max retained

            val activity = if (UsageStatsHelper.hasUsageAccess(context)) {
                UsageStatsHelper.queryActivity(context, lookbackStart, now)
            } else emptyMap()

            disappeared.forEach { pkg ->
                val info = known.getValue(pkg)
                val stats = activity[pkg]
                dao.insertUninstalledApp(
                    UninstalledApp(
                        packageName = pkg,
                        appLabel = info.appLabel,
                        firstSeen = stats?.firstActive ?: info.firstSeen,
                        lastActive = stats?.lastActive ?: info.firstSeen,
                        detectedAt = now,
                        activeDurationMs = stats?.activeDurationMs ?: 0L,
                        eventCount = stats?.eventCount ?: 0
                    )
                )
                dao.removeKnownPackage(pkg)
            }
        }

        val newOnes = current.keys - known.keys
        if (newOnes.isNotEmpty()) {
            val now = System.currentTimeMillis()
            dao.insertKnownPackages(newOnes.map { KnownPackage(it, current.getValue(it), now) })
        }
    }

    suspend fun recentUninstalls(withinDays: Int = 30) =
        dao.getRecentUninstalls(System.currentTimeMillis() - withinDays * 24L * 60 * 60 * 1000)
}
