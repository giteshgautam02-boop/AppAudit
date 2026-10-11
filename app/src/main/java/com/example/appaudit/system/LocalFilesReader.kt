package com.example.appaudit.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Lists what is inside /data/local/tmp (and its sub-folders).
 * That folder is owned by "shell" and a normal app cannot list it, so the
 * listing goes through root (`su`) or Shizuku (see [ShellAccess]).
 */
object LocalFilesReader {

    const val ROOT = "/data/local/tmp"

    data class Entry(
        val name: String,
        val path: String,
        val isDir: Boolean,
        val isLink: Boolean,
        val perms: String,
        val owner: String,
        val size: Long,
        val modified: String,
        val linkTarget: String?,
        val flag: String? = null
    )

    sealed class Result {
        data class Ok(val path: String, val entries: List<Entry>) : Result()
        data class Error(val message: String) : Result()
    }

    /** Why a file is interesting, or null. Checked on the file name (and permissions). */
    fun flagFor(name: String, perms: String = ""): String? {
        val n = name.substringAfterLast('/').lowercase()
        return when {
            "shizuku" in n -> "Shizuku"
            n.endsWith(".sh") -> "Shell script (.sh)"
            n.endsWith(".key") || n.endsWith(".pem") || n.endsWith(".jks") ||
                n.endsWith(".keystore") || n.endsWith(".p12") || n.endsWith(".pfx") ||
                "key" in n || "cert" in n -> "Key / certificate"
            "tok" in n || "secret" in n || "passw" in n -> "Token / secret"
            perms.length == 10 && perms[0] == '-' && perms[3] == 'x' -> "Executable"
            else -> null
        }
    }

    private fun quote(path: String) = "'" + path.replace("'", "'\\''") + "'"

    // toybox/mksh `ls -lA`: perms links owner group size YYYY-MM-DD HH:MM name [-> target]
    private val line = Regex("""^([\-dlcbps][rwxsStT\-]{9})\S*\s+\d+\s+(\S+)\s+(\S+)\s+(\d+(?:,\s*\d+)?)\s+(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}(?::\d{2})?|[A-Za-z]{3}\s+\d{1,2}\s+(?:\d{4}|\d{2}:\d{2}))\s+(.+)$""")

    suspend fun list(path: String = ROOT): Result = withContext(Dispatchers.IO) {
        val mode = ShellAccess.detectMode()
        if (mode == ShellAccess.Mode.NONE) return@withContext Result.Error(ShellAccess.explainNone())
        val out = ShellAccess.exec("ls -lA ${quote(path)} 2>&1", mode)
            ?: return@withContext Result.Error("Folder read nahi ho paya.")

        val entries = out.lineSequence().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("total ") }
            .mapNotNull { raw ->
            val m = line.find(raw)
            if (m == null) {
                // Unknown line format: still show it raw so nothing gets hidden.
                if (raw.contains("No such file", true) || raw.contains("Permission denied", true)) return@mapNotNull null
                return@mapNotNull Entry(raw, path, false, false, "?", "?", 0L, "", null)
            }
            val (perms, owner, _, size, mtime, nameRaw) = m.destructured
            val isLink = perms.startsWith("l")
            val name = if (isLink) nameRaw.substringBefore(" -> ") else nameRaw
            Entry(
                name = name,
                path = path.trimEnd('/') + "/" + name,
                isDir = perms.startsWith("d"),
                isLink = isLink,
                perms = perms,
                owner = owner,
                size = size.substringBefore(',').toLongOrNull() ?: 0L,
                modified = mtime,
                linkTarget = if (isLink) nameRaw.substringAfter(" -> ", "") else null,
                flag = flagFor(name, perms)
            )
        }.sortedWith(compareByDescending<Entry> { it.isDir }.thenBy { it.name.lowercase() }).toList()

        if (entries.isEmpty() && out.contains("No such file", true)) {
            Result.Error("Folder nahi mila: $path")
        } else if (entries.isEmpty() && out.contains("Permission denied", true)) {
            Result.Error("Permission denied: $path")
        } else {
            Result.Ok(path, entries)
        }
    }

    /**
     * Recursive search of [ROOT] for .sh / .key / Shizuku files (and a few other
     * key formats). Full path is returned in [Entry.path].
     */
    suspend fun findFlagged(): Result = withContext(Dispatchers.IO) {
        val mode = ShellAccess.detectMode()
        if (mode == ShellAccess.Mode.NONE) return@withContext Result.Error(ShellAccess.explainNone())
        val cmd = "find $ROOT \\( -iname '*.sh' -o -iname '*.key' -o -iname '*.pem' " +
            "-o -iname '*.jks' -o -iname '*.keystore' -o -iname '*.p12' -o -iname '*.pfx' " +
            "-o -iname '*shizuku*' -o -iname '*key*' -o -iname '*tok*' -o -iname '*cert*' " +
            "-o -iname '*secret*' -o \\( -type f -perm -u+x \\) \\) -exec ls -ld {} + 2>&1"
        val out = ShellAccess.exec(cmd, mode) ?: return@withContext Result.Error("Search nahi ho paya.")
        val entries = out.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.mapNotNull { raw ->
            val m = line.find(raw) ?: return@mapNotNull null
            val (perms, owner, _, size, mtime, fullRaw) = m.destructured
            val isLink = perms.startsWith("l")
            val full = if (isLink) fullRaw.substringBefore(" -> ") else fullRaw
            Entry(
                name = full.substringAfterLast('/'), path = full, isDir = perms.startsWith("d"), isLink = isLink,
                perms = perms, owner = owner, size = size.substringBefore(',').toLongOrNull() ?: 0L,
                modified = mtime, linkTarget = if (isLink) fullRaw.substringAfter(" -> ", "") else null,
                flag = flagFor(full, perms)
            )
        }.sortedBy { it.path.lowercase() }.toList()
        Result.Ok(ROOT, entries)
    }
}
