package com.example.appaudit.system

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * One place that decides HOW we run shell commands:
 *  1. ROOT    – `su -c` (rooted phone)
 *  2. SHIZUKU – Shizuku's shell (uid 2000) – no root needed, but Shizuku must be
 *               running and this app must have been granted permission.
 *  3. NONE    – neither is available.
 *
 * Shell (Shizuku) can read /data/local/tmp, `pm`, `dumpsys` etc. It can NOT read
 * Everything under /data/system (any file) - that needs real root.
 */
object ShellAccess {

    enum class Mode(val label: String) {
        ROOT("Root"), SHIZUKU("Shizuku (shell)"), NONE("none")
    }

    private const val REQ_CODE = 4242

    // ---- Shizuku state ----------------------------------------------------

    fun shizukuRunning(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun shizukuGranted(): Boolean = try {
        shizukuRunning() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    fun requestShizuku() {
        try { if (shizukuRunning() && !Shizuku.isPreV11()) Shizuku.requestPermission(REQ_CODE) } catch (_: Throwable) {}
    }

    // ---- mode detection ---------------------------------------------------

    private fun rootWorks(): Boolean = try {
        val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor(10, TimeUnit.SECONDS) && out.contains("uid=0")
    } catch (_: Throwable) { false }

    /** Shizuku hands its binder to the app a moment after launch, so wait a little for it. */
    private fun awaitBinder(timeoutMs: Long = 3000): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (shizukuRunning()) return true
            try { Thread.sleep(200) } catch (_: InterruptedException) { return false }
        }
        return shizukuRunning()
    }

    fun detectMode(): Mode = when {
        rootWorks() -> Mode.ROOT
        awaitBinder() && shizukuGranted() -> Mode.SHIZUKU
        else -> Mode.NONE
    }

    /** Human readable reason for Mode.NONE; also pops the Shizuku permission dialog if needed. */
    fun explainNone(): String =
        if (!shizukuRunning()) {
            "Root nahi mila aur Shizuku se connect nahi hua. Shizuku app mein \"Running\" check karo (na ho to Start karo), phir App Audit ko band karke dobara kholo."
        } else {
            requestShizuku()
            "Shizuku permission maangi gayi hai — \"Allow\" karke phir Refresh dabao."
        }

    // ---- running commands -------------------------------------------------

    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        ).apply { isAccessible = true }
    }

    private fun read(p: Process, timeoutSec: Long): String? {
        val out = p.inputStream.bufferedReader().readText()
        return if (p.waitFor(timeoutSec, TimeUnit.SECONDS)) out else { p.destroy(); null }
    }

    /** Runs [cmd] with the given [mode]; stderr is merged into the output. */
    fun exec(cmd: String, mode: Mode, timeoutSec: Long = 20): String? = try {
        when (mode) {
            Mode.ROOT -> read(ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start(), timeoutSec)
            Mode.SHIZUKU -> read(
                newProcess.invoke(null, arrayOf("sh", "-c", "$cmd 2>&1"), null, null) as Process, timeoutSec
            )
            Mode.NONE -> null
        }
    } catch (_: Throwable) { null }
}
