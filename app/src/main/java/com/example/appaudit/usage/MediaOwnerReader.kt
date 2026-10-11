package com.example.appaudit.usage

import android.content.Context
import android.os.Build
import android.provider.MediaStore

/**
 * Extra no-root / no-Shizuku source for the "Deleted Apps" scan.
 *
 * Every file registered in MediaStore (photos, downloads, videos, documents, ...) keeps the
 * package name of the app that created it in OWNER_PACKAGE_NAME - and that value survives
 * after the app is uninstalled. So any package that owns media but is no longer installed
 * is a very likely "deleted app", even if it was removed before this audit app existed.
 *
 * Needs Android 10+ and the storage access this app already asks for
 * (MANAGE_EXTERNAL_STORAGE). Apps that never wrote a file can't be found this way.
 */
object MediaOwnerReader {

    fun readActivity(context: Context): Map<String, UsageStatsHelper.AppActivity> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyMap()

        val first = HashMap<String, Long>()
        val last = HashMap<String, Long>()
        val count = HashMap<String, Int>()

        try {
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            context.contentResolver.query(
                uri, projection, "${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} IS NOT NULL", null, null
            )?.use { c ->
                val iPkg = c.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (c.moveToNext()) {
                    val pkg = c.getString(iPkg)?.takeIf { it.isNotBlank() } ?: continue
                    val added = c.getLong(iAdded) * 1000L // MediaStore stores seconds
                    val modified = c.getLong(iMod) * 1000L
                    val lo = if (added > 0) added else modified
                    val hi = maxOf(added, modified)
                    if (lo <= 0L) continue
                    count[pkg] = (count[pkg] ?: 0) + 1
                    first[pkg] = minOf(first[pkg] ?: Long.MAX_VALUE, lo)
                    last[pkg] = maxOf(last[pkg] ?: 0L, hi)
                }
            }
        } catch (_: Throwable) {
            return emptyMap()
        }

        return first.keys.associateWith { pkg ->
            UsageStatsHelper.AppActivity(
                packageName = pkg,
                firstActive = first.getValue(pkg),
                lastActive = last[pkg] ?: first.getValue(pkg),
                activeDurationMs = 0L,
                eventCount = count[pkg] ?: 0
            )
        }
    }
}
