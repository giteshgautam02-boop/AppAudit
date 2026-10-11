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
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .associate { it.packageName to it.loadLabel(pm).toString() }
    }

    private fun installedPackageNames(): Set<String> {
        val list = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(0)
        }
        return list.map { it.packageName }.toSet()
    }

    private fun isInstalled(pkg: String): Boolean = try {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun prettyName(pkg: String): String {
        val parts = pkg.split('.').filter { it.isNotBlank() }
        val skip = setOf("com", "org", "net", "io", "android", "app", "apps", "mobile")
        val word = parts.lastOrNull { it !in skip } ?: pkg
        return word.replaceFirstChar { it.uppercase() }
    }

    /**
     * 1. Snapshot diff: apps we saw before that are gone now.
     * 2. History scan: apps the OS shows as used in the last 30 days that are
     *    not installed anymore (works instantly, even on first launch).
     */
    suspend fun runAuditScan() {
        val now = System.currentTimeMillis()
        val known = dao.getAllKnownPackages().associateBy { it.packageName }
        val current = currentlyInstalled()
        val hasUsage = UsageStatsHelper.hasUsageAccess(context)
        val activity = if (hasUsage) {
            UsageStatsHelper.queryActivity(context, now - 30L * 24 * 60 * 60 * 1000, now)
        } else emptyMap()

        val disappeared = known.keys - current.keys
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

        if (hasUsage) {
            val installedAll = installedPackageNames()
            val logged = dao.getRecentUninstalls(0L).map { it.packageName }.toSet()
            // an app that was reinstalled should leave the deleted list
            logged.filter { it in installedAll }.forEach { dao.removeUninstalled(it) }
            activity.forEach { (pkg, stats) ->
                if (pkg in logged || pkg in disappeared || pkg == context.packageName) return@forEach
                if (pkg in installedAll || isInstalled(pkg)) return@forEach
                dao.insertUninstalledApp(
                    UninstalledApp(
                        packageName = pkg,
                        appLabel = prettyName(pkg),
                        firstSeen = stats.firstActive,
                        lastActive = stats.lastActive,
                        detectedAt = now,
                        activeDurationMs = stats.activeDurationMs,
                        eventCount = stats.eventCount
                    )
                )
            }
        }

        val newOnes = current.keys - known.keys
        if (newOnes.isNotEmpty()) {
            dao.insertKnownPackages(newOnes.map { KnownPackage(it, current.getValue(it), now) })
        }
    }

    suspend fun recentUninstalls(withinDays: Int = 30) =
        dao.getRecentUninstalls(System.currentTimeMillis() - withinDays * 24L * 60 * 60 * 1000)
}
