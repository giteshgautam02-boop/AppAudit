package com.example.appaudit.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Reads Android's own package bookkeeping straight from /data/system.
 *
 * These paths are owned by the "system" user and are NOT readable by a normal
 * app, so every read goes through `su` (root). Without root, [isRootAvailable]
 * is false and every read returns null.
 *
 * Sources merged into one list:
 *  - /data/system/packages.list
 *  - /data/system/packages.xml
 *  - /data/system/packages.xml.bak
 *  - /data/system/package_cache/
 */
object SystemPackageReader {

    private const val DIR = "/data/system"

    enum class Source(val label: String) {
        LIST("packages.list"),
        XML("packages.xml"),
        XML_BAK("packages.xml.bak"),
        CACHE("package_cache")
    }

    data class SystemPackage(
        val packageName: String,
        val sources: Set<Source>,
        val uid: String? = null,
        val codePath: String? = null,
        val versionCode: String? = null,
        val installer: String? = null,
        val isSystemApp: Boolean? = null,
        val dataDir: String? = null
    )

    data class Result(
        val packages: List<SystemPackage>,
        /** Per-source status line, e.g. "packages.xml: 312 packages" or an error. */
        val status: List<String>,
        val rootAvailable: Boolean
    )

    // ---- root shell -------------------------------------------------------

    private fun su(cmd: String, timeoutSec: Long = 20): String? = try {
        val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(false).start()
        val out = p.inputStream.bufferedReader().readText()
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly(); null
        } else if (p.exitValue() != 0 && out.isBlank()) null else out
    } catch (e: Exception) {
        null
    }

    suspend fun isRootAvailable(): Boolean = withContext(Dispatchers.IO) {
        su("id")?.contains("uid=0") == true
    }

    // ---- individual sources ----------------------------------------------

    /** packages.list line: `pkg uid debuggable dataDir seinfo gids` */
    private fun parseList(text: String): Map<String, SystemPackage> =
        text.lineSequence().mapNotNull { line ->
            val f = line.trim().split(' ')
            if (f.size < 2) null
            else f[0] to SystemPackage(
                packageName = f[0],
                sources = setOf(Source.LIST),
                uid = f[1],
                dataDir = f.getOrNull(3)
            )
        }.toMap()

    private val pkgTag = Regex("""<package\s+([^>]*?)/?>""")
    private val attr = Regex("""(\w+)="([^"]*)"""")

    private fun parseXml(text: String, src: Source): Map<String, SystemPackage> {
        val map = LinkedHashMap<String, SystemPackage>()
        for (m in pkgTag.findAll(text)) {
            val a = attr.findAll(m.groupValues[1]).associate { it.groupValues[1] to it.groupValues[2] }
            val name = a["name"] ?: continue
            val code = a["codePath"]
            map[name] = SystemPackage(
                packageName = name,
                sources = setOf(src),
                uid = a["userId"] ?: a["sharedUserId"],
                codePath = code,
                versionCode = a["version"],
                installer = a["installer"],
                isSystemApp = code?.let { c ->
                    listOf("/system", "/product", "/vendor", "/system_ext", "/apex", "/odm")
                        .any { c.startsWith(it) }
                }
            )
        }
        return map
    }

    /**
     * Android 12+ stores packages.xml as binary XML ("ABX"). If so, convert it
     * with the system's own `abx2xml` tool before parsing.
     */
    private fun readXml(file: String): String? {
        val head = su("head -c 4 $DIR/$file") ?: return null
        return if (head.startsWith("ABX")) {
            su("abx2xml $DIR/$file -", 30)
        } else {
            su("cat $DIR/$file", 30)
        }
    }

    /** package_cache holds one entry per package, named like `com.foo.bar-<hash>` or `<hash>`. */
    private fun readCache(): Map<String, SystemPackage>? {
        val out = su("ls -1 $DIR/package_cache") ?: return null
        val map = LinkedHashMap<String, SystemPackage>()
        out.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { entry ->
            // Strip a trailing "-<hash>" / ".<hash>" suffix when it looks like a package name.
            val name = entry.substringBeforeLast('-', entry)
            if (name.contains('.')) {
                map[name] = SystemPackage(
                    packageName = name,
                    sources = setOf(Source.CACHE),
                    codePath = "$DIR/package_cache/$entry"
                )
            }
        }
        return map
    }

    // ---- public entry point ----------------------------------------------

    suspend fun readAll(): Result = withContext(Dispatchers.IO) {
        if (su("id")?.contains("uid=0") != true) {
            return@withContext Result(
                emptyList(),
                listOf("Root access nahi mila — ye files bina root ke read nahi ho sakti."),
                rootAvailable = false
            )
        }

        val status = mutableListOf<String>()
        val parts = mutableListOf<Map<String, SystemPackage>>()

        fun add(label: String, data: Map<String, SystemPackage>?) {
            if (data == null) status += "$label: read nahi ho paya"
            else { status += "$label: ${data.size} packages"; parts += data }
        }

        add(Source.LIST.label, su("cat $DIR/packages.list")?.let(::parseList))
        add(Source.XML.label, readXml("packages.xml")?.let { parseXml(it, Source.XML) })
        add(Source.XML_BAK.label, readXml("packages.xml.bak")?.let { parseXml(it, Source.XML_BAK) })
        add(Source.CACHE.label, readCache())

        // Merge everything into one entry per package name.
        val merged = LinkedHashMap<String, SystemPackage>()
        for (part in parts) for ((name, p) in part) {
            val old = merged[name]
            merged[name] = if (old == null) p else old.copy(
                sources = old.sources + p.sources,
                uid = old.uid ?: p.uid,
                codePath = old.codePath ?: p.codePath,
                versionCode = old.versionCode ?: p.versionCode,
                installer = old.installer ?: p.installer,
                isSystemApp = old.isSystemApp ?: p.isSystemApp,
                dataDir = old.dataDir ?: p.dataDir
            )
        }

        Result(merged.values.sortedBy { it.packageName }, status, rootAvailable = true)
    }
}
