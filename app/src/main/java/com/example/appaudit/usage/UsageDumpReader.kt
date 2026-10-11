package com.example.appaudit.usage

import com.example.appaudit.system.ShellAccess
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Second source of app-usage history for the "Deleted Apps" scan.
 *
 * Android keeps its usage history in /data/system_ce/0/usagestats/. Those raw files
 * are protected and stored in a binary format, so instead we ask the system for the
 * same data in text form: `dumpsys usagestats` (needs Shizuku or root).
 *
 * Returns an empty map when neither Shizuku nor root is available.
 */
object UsageDumpReader {

    // time="2026-10-10 12:34:56" type=ACTIVITY_RESUMED package=com.foo class=...
    private val ev = Regex("""time="(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})[^"]*"\s+type=(\w+)\s+package=(\S+)""")
    private val resumed = setOf("ACTIVITY_RESUMED", "MOVE_TO_FOREGROUND")
    private val ended = setOf("ACTIVITY_PAUSED", "ACTIVITY_STOPPED", "MOVE_TO_BACKGROUND")

    fun readActivity(): Map<String, UsageStatsHelper.AppActivity> {
        val mode = ShellAccess.detectMode()
        if (mode == ShellAccess.Mode.NONE) return emptyMap()

        // grep keeps only event lines, so the (very large) dump stays small.
        val out = ShellAccess.exec(
            "dumpsys usagestats | grep -E 'time=.*type=(ACTIVITY_|MOVE_TO_)'", mode, 60
        ) ?: return emptyMap()

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val first = HashMap<String, Long>()
        val last = HashMap<String, Long>()
        val resumedAt = HashMap<String, Long>()
        val duration = HashMap<String, Long>()
        val count = HashMap<String, Int>()

        for (m in ev.findAll(out)) {
            val (timeStr, type, pkg) = m.destructured
            val t = try { fmt.parse(timeStr)?.time } catch (_: Exception) { null } ?: continue
            if (type !in resumed && type !in ended) continue
            count[pkg] = (count[pkg] ?: 0) + 1
            if (pkg !in first || t < first.getValue(pkg)) first[pkg] = t
            if (pkg !in last || t > last.getValue(pkg)) last[pkg] = t
            if (type in resumed) {
                resumedAt[pkg] = t
            } else {
                val s = resumedAt.remove(pkg)
                if (s != null && t > s) duration[pkg] = (duration[pkg] ?: 0L) + (t - s)
            }
        }

        return first.keys.associateWith { pkg ->
            UsageStatsHelper.AppActivity(
                packageName = pkg,
                firstActive = first[pkg] ?: 0L,
                lastActive = last[pkg] ?: 0L,
                activeDurationMs = duration[pkg] ?: 0L,
                eventCount = count[pkg] ?: 0
            )
        }
    }
}
