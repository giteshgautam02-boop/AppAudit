package com.example.appaudit

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File

data class ApkFile(val name: String, val path: String, val size: Long, val modified: Long)
data class LiveEvent(val time: Long, val type: String, val pkg: String)

/** Finds leftover .apk installer files in shared storage. */
object ApkScanner {

    fun hasAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    fun openSettings(ctx: Context) {
        val pkgUri = Uri.parse("package:${ctx.packageName}")
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkgUri)
            else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun scan(ctx: Context, sinceMs: Long): List<ApkFile> {
        if (!hasAccess(ctx)) return emptyList()
        val out = ArrayList<ApkFile>()
        try {
            Environment.getExternalStorageDirectory()
                .walkTopDown()
                .maxDepth(8)
                .onEnter { it.name != "Android" }
                .filter { it.isFile && it.extension.equals("apk", ignoreCase = true) && it.lastModified() >= sinceMs }
                .forEach { out.add(ApkFile(it.name, it.absolutePath, it.length(), it.lastModified())) }
        } catch (e: Exception) {
            // ignore unreadable folders
        }
        return out.sortedByDescending { it.modified }.take(200)
    }
}

/** Tiny file-backed log of install / uninstall / update events seen while the app is installed. */
object LiveLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "live_events.log")

    @Synchronized
    fun add(ctx: Context, type: String, pkg: String) {
        if (pkg == ctx.packageName) return
        val f = file(ctx)
        f.appendText("${System.currentTimeMillis()}|$type|$pkg\n")
        val lines = f.readLines()
        if (lines.size > 500) f.writeText(lines.takeLast(500).joinToString("\n", postfix = "\n"))
    }

    @Synchronized
    fun read(ctx: Context): List<LiveEvent> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val p = line.split("|", limit = 3)
            val t = p.getOrNull(0)?.toLongOrNull()
            if (p.size == 3 && t != null) LiveEvent(t, p[1], p[2]) else null
        }.sortedByDescending { it.time }
    }
}
