package com.example.appaudit

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.appaudit.data.UninstalledApp
import com.example.appaudit.repository.InstalledAppsRepository
import com.example.appaudit.usage.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DAY_MS = 24L * 60 * 60 * 1000

private val Bg = Color(0xFF000000)
private val Panel = Color(0xFF111114)
private val Border = Color(0xFF26262B)
private val Green = Color(0xFF00FF70)
private val Red = Color(0xFFFF2D55)
private val RedDark = Color(0xFF7A1F33)
private val Cyan = Color(0xFF00E5CC)
private val Dim = Color(0xFF8A8A93)
private val Mono = FontFamily.Monospace
private val Round = RoundedCornerShape(16.dp)

enum class Range(val label: String, val ms: Long, val short: String) {
    D30("30 Days (Max)", 30 * DAY_MS, "30 Days (Max)"),
    D15("15 Days", 15 * DAY_MS, "15 Days"),
    D7("7 Days", 7 * DAY_MS, "7 Days"),
    D3("3 Days (72h)", 3 * DAY_MS, "3 Days (72h)"),
    H24("24 Hours", DAY_MS, "24 Hours")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuditTheme { AuditScreen() } }
    }
}

@Composable
fun AuditTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Green, background = Bg, surface = Panel,
            onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

private fun Modifier.panel(border: Color = Border, bg: Color = Panel): Modifier =
    this.clip(Round).background(bg).border(1.dp, border, Round)

private fun fmt(ms: Long): String = SimpleDateFormat("MMM d, yyyy HH:mm:ss", Locale.getDefault()).format(Date(ms))

private fun rel(ms: Long): String =
    DateUtils.getRelativeTimeSpanString(ms, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

private fun fmtDuration(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60}m ${s % 60}s"
        else -> "${s / 3600}h ${(s % 3600) / 60}m"
    }
}

private fun fmtSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    else -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
}

private fun buildReport(
    range: Range, uninst: List<UninstalledApp>, apks: List<ApkFile>, live: List<LiveEvent>
): String {
    val sb = StringBuilder()
    val eq = "=".repeat(52)
    val dash = "-".repeat(52)
    fun row(k: String, v: String) { sb.append(k.padEnd(20)).append(": ").append(v).append('\n') }
    fun row2(k: String, v: String) { sb.append("  ").append(k.padEnd(16)).append(": ").append(v).append('\n') }

    sb.append(eq).append('\n').append("         SYSTEM APPLICATION AUDIT REPORT\n").append(eq).append('\n')
    row("Application", "Arthur Audit v2.0")
    row("Developer", "Devil")
    row("Team / Org", "Team Arthur")
    row("Device Model", Build.MODEL)
    row("Android Version", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    row("Security Patch", Build.VERSION.SECURITY_PATCH ?: "n/a")
    row("Audit Timestamp", fmt(System.currentTimeMillis()))
    row("Audit Window", range.label)
    row("Total Deleted", "${uninst.size} package(s)")
    row("Residual APKs", "${apks.size} file(s)")
    row("Live Monitored", "${live.size} event(s)")
    sb.append(eq).append("\n\n").append(dash).append('\n')
    sb.append("DETECTED UNINSTALLED APPLICATIONS (${uninst.size})\n").append(dash).append('\n')
    uninst.forEachIndexed { i, a ->
        sb.append("[#${i + 1}] ${a.appLabel}\n")
        row2("Package Name", a.packageName)
        row2("Current Status", "UNINSTALLED / NOT PRESENT")
        row2("Last Active", "${fmt(a.lastActive)} (${rel(a.lastActive)})")
        row2("First Active", fmt(a.firstSeen))
        row2("Active Duration", fmtDuration(a.activeDurationMs))
        row2("Logged Events", "${a.eventCount} system interactions")
        row2("Estimated Del.", "Between ${fmt(a.lastActive)} and ${fmt(a.detectedAt)}")
        sb.append('\n')
    }
    sb.append(dash).append('\n').append("RESIDUAL APK FILES (${apks.size})\n").append(dash).append('\n')
    apks.forEach { sb.append("${it.name} (${fmtSize(it.size)})\n  ${it.path}\n  Modified: ${fmt(it.modified)}\n") }
    sb.append('\n').append(dash).append('\n').append("LIVE EVENTS (${live.size})\n").append(dash).append('\n')
    live.forEach { sb.append("${fmt(it.time)}  [${it.type}]  ${it.pkg}\n") }
    sb.append('\n').append("Team Arthur • Created by Devil\n")
    return sb.toString()
}

@Composable
fun AuditScreen() {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val repo = remember { InstalledAppsRepository(context) }
    val scope = rememberCoroutineScope()

    var hasAccess by remember { mutableStateOf(UsageStatsHelper.hasUsageAccess(context)) }
    var hasStorage by remember { mutableStateOf(ApkScanner.hasAccess(context)) }
    var uninstalls by remember { mutableStateOf(listOf<UninstalledApp>()) }
    var apks by remember { mutableStateOf(listOf<ApkFile>()) }
    var live by remember { mutableStateOf(listOf<LiveEvent>()) }
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var range by remember { mutableStateOf(Range.D30) }
    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var showAbout by remember { mutableStateOf(false) }
    var timelineApp by remember { mutableStateOf<UninstalledApp?>(null) }

    fun copy(text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }

    fun share(text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(i, "Share Log"))
    }

    fun scan() {
        if (scanning) return
        scope.launch {
            scanning = true
            scanError = null
            try {
                val since = System.currentTimeMillis() - 30 * DAY_MS
                val result = withContext(Dispatchers.IO) {
                    repo.runAuditScan()
                    Triple(repo.recentUninstalls(), ApkScanner.scan(context, since), LiveLog.read(context))
                }
                uninstalls = result.first
                apks = result.second
                live = result.third
            } catch (e: Exception) {
                scanError = "${e.javaClass.simpleName}: ${e.message}"
            } finally {
                scanning = false
            }
        }
    }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = UsageStatsHelper.hasUsageAccess(context)
                hasStorage = ApkScanner.hasAccess(context)
                scan()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val cutoff = System.currentTimeMillis() - range.ms
    val inRange = uninstalls.filter { it.lastActive >= cutoff }.sortedByDescending { it.lastActive }
    val apksInRange = apks.filter { it.modified >= cutoff }
    val liveInRange = live.filter { it.time >= cutoff }
    val shown = inRange.filter {
        query.isBlank() || it.appLabel.contains(query, true) || it.packageName.contains(query, true)
    }

    Scaffold(
        containerColor = Bg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { scan() }, containerColor = Green, contentColor = Color.Black, shape = CircleShape
            ) {
                if (scanning) CircularProgressIndicator(Modifier.size(24.dp), color = Color.Black, strokeWidth = 2.dp)
                else Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Header(
                onInfo = { showAbout = true },
                onShield = { UsageStatsHelper.openUsageAccessSettings(context) }
            )

            if (!hasAccess) {
                PermissionPanel { UsageStatsHelper.openUsageAccessSettings(context) }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(Range.values().toList()) { r ->
                        val sel = r == range
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier.clip(shape)
                                .background(if (sel) Green else Panel)
                                .border(1.dp, if (sel) Green else Border, shape)
                                .clickable { range = r }
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                        ) {
                            Text(r.label, color = if (sel) Color.Black else Dim, fontFamily = Mono, fontSize = 15.sp)
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("${inRange.size}", "Deleted Apps", Red, Modifier.weight(1f))
                    StatCard("${apksInRange.size}", "Residual APKs", Green, Modifier.weight(1f))
                    StatCard("${liveInRange.size}", "Live Logged", Green, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))

                val titles = listOf("Deleted Apps", "Residual APKs", "Live Monitor", "Full Report")
                TabRow(selectedTabIndex = tab, containerColor = Bg, contentColor = Green) {
                    titles.forEachIndexed { i, t ->
                        Tab(selected = tab == i, onClick = { tab = i }, text = {
                            Text(
                                t, color = if (tab == i) Green else Dim, fontFamily = Mono, fontSize = 14.sp,
                                fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center, maxLines = 2
                            )
                        })
                    }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        0 -> DeletedTab(
                            shown, query, { query = it }, scanning, scanError,
                            onCopy = { copy("${it.appLabel}\n${it.packageName}") },
                            onTimeline = { timelineApp = it }
                        )
                        1 -> ApkTab(hasStorage, apksInRange) { ApkScanner.openSettings(context) }
                        2 -> LiveTab(liveInRange)
                        else -> {
                            val report = buildReport(range, inRange, apksInRange, liveInRange)
                            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(
                                        Modifier.weight(1f).clip(Round).border(1.dp, Dim, Round)
                                            .clickable { copy(report) }.padding(vertical = 16.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Copy Log", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(
                                        Modifier.weight(1f).clip(Round).background(Green)
                                            .clickable { share(report) }.padding(vertical = 16.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Share, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Share Log", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                Spacer(Modifier.height(16.dp))
                                Box(Modifier.fillMaxWidth().panel().padding(14.dp)) {
                                    Text(report, fontFamily = Mono, fontSize = 12.sp, color = Color(0xFFD0D0D8))
                                }
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Team Arthur • Created by Devil", color = Dim, fontSize = 13.sp,
                                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(88.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAbout) AboutDialog { showAbout = false }
    timelineApp?.let { TimelineDialog(it) { timelineApp = null } }
}

@Composable
private fun Header(onInfo: () -> Unit, onShield: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Panel),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.Search, null, tint = Color(0xFF3B9EFF), modifier = Modifier.size(24.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Arthur Audit", fontFamily = Mono, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A2A17))
                        .border(1.dp, Green, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                ) { Text("SYS_AUDIT", color = Green, fontFamily = Mono, fontSize = 11.sp) }
            }
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Dim)) { append(">_ activity_monitor") }
                    withStyle(SpanStyle(color = Dim)) { append(" • ") }
                    withStyle(SpanStyle(color = Cyan)) { append("Devil") }
                    withStyle(SpanStyle(color = Dim)) { append(" • ") }
                    withStyle(SpanStyle(color = Cyan)) { append("Team Arthur") }
                },
                fontFamily = Mono, fontSize = 12.sp
            )
        }
        IconButton(onClick = onInfo) { Icon(Icons.Filled.Info, "About", tint = Dim) }
        IconButton(onClick = onShield) { Icon(Icons.Filled.Security, "Permissions", tint = Dim) }
    }
}

@Composable
private fun StatCard(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier.panel().padding(14.dp)) {
        Text(value, color = color, fontFamily = Mono, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Dim, fontFamily = Mono, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun PermissionPanel(onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp).panel().padding(20.dp)) {
        Text(">_ PERMISSION REQUIRED", color = Red, fontFamily = Mono, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Usage Access is required to detect uninstalled apps.", color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text(
            "1. Tap the button below\n2. Select Arthur Audit from the list\n3. Turn the toggle ON\n4. Press back to return",
            color = Dim, fontFamily = Mono, fontSize = 13.sp
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpen, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)
        ) { Text("Open Usage Access settings") }
    }
}

@Composable
private fun DeletedTab(
    apps: List<UninstalledApp>, query: String, onQuery: (String) -> Unit, scanning: Boolean, error: String?,
    onCopy: (UninstalledApp) -> Unit, onTimeline: (UninstalledApp) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = Round,
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = Cyan) },
                placeholder = { Text("filter pkg or name...", color = Dim, fontFamily = Mono, fontSize = 14.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Mono, fontSize = 15.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Green, unfocusedBorderColor = Border,
                    cursorColor = Green, focusedContainerColor = Panel, unfocusedContainerColor = Panel
                )
            )
        }
        item {
            Text(
                ">_ UNINSTALLED PACKAGES DETECTED [${apps.size}]",
                color = Red, fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 15.sp
            )
        }
        if (error != null) {
            item { Text("Scan error: $error", color = Red, fontFamily = Mono, fontSize = 12.sp) }
        }
        if (apps.isEmpty()) {
            item {
                Text(
                    if (scanning) "Scanning..." else "No uninstalled apps found in the selected window.",
                    color = Dim, fontFamily = Mono, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                )
            }
        }
        items(apps, key = { it.packageName }) { app ->
            DeletedCard(app, { onCopy(app) }, { onTimeline(app) })
        }
    }
}

@Composable
private fun DeletedCard(app: UninstalledApp, onCopy: () -> Unit, onTimeline: () -> Unit) {
    Column(Modifier.fillMaxWidth().panel(RedDark, Color(0xFF0F0F12)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF3A1420))
                    .border(1.dp, RedDark, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)
            ) { Text("[STATUS: UNINSTALLED]", color = Red, fontFamily = Mono, fontSize = 12.sp) }
            Text("Active ${rel(app.lastActive)}", color = Red, fontFamily = Mono, fontSize = 13.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text("> ${app.appLabel}", fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(app.packageName, color = Dim, fontFamily = Mono, fontSize = 15.sp)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Border))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("LAST ACTIVE", color = Dim, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Text(fmt(app.lastActive), fontFamily = Mono, fontSize = 14.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("ACTIVE DURATION", color = Dim, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                Text(fmtDuration(app.activeDurationMs), fontFamily = Mono, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
            SmallAction(Icons.Filled.ContentCopy, "Copy", Color.White, onCopy)
            SmallAction(Icons.Filled.ShowChart, "Timeline", Green, onTimeline)
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.clip(shape).background(Color(0xFF16161A)).border(1.dp, Border, shape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = color, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ApkTab(hasStorage: Boolean, apks: List<ApkFile>, onGrant: () -> Unit) {
    if (!hasStorage) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text("Storage access required", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Allow All files access to scan for leftover .apk installers.", color = Dim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)) {
                Text("Grant access")
            }
        }
    } else if (apks.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Filled.Folder, null, tint = Dim, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text("No Residual APKs Found", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "No installer files (.apk) were modified or downloaded in storage within the selected audit window.",
                color = Dim, textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(apks, key = { it.path }) { f ->
                Column(Modifier.fillMaxWidth().panel().padding(14.dp)) {
                    Text("> ${f.name}", fontFamily = Mono, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(f.path, color = Dim, fontFamily = Mono, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("${fmtSize(f.size)}  •  ${fmt(f.modified)}", color = Green, fontFamily = Mono, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun LiveTab(events: List<LiveEvent>) {
    if (events.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Text("No Live Events Yet", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Install, update and uninstall events are logged here in real time.",
                color = Dim, textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(events) { e ->
                val c = when (e.type) { "UNINSTALLED" -> Red; "INSTALLED" -> Green; else -> Cyan }
                Column(Modifier.fillMaxWidth().panel().padding(14.dp)) {
                    Text("[${e.type}]", color = c, fontFamily = Mono, fontWeight = FontWeight.Bold)
                    Text(e.pkg, fontFamily = Mono, fontSize = 15.sp)
                    Text(fmt(e.time), color = Dim, fontFamily = Mono, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text("Arthur Audit", fontWeight = FontWeight.Bold, fontSize = 26.sp)
                Spacer(Modifier.height(4.dp))
                Text("v2.0 Diagnostic Suite", color = Dim, fontSize = 16.sp)
            }
        },
        text = {
            Column {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0A0A0C)).padding(16.dp)) {
                    Text("DEVELOPER", color = Green, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Devil", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A2A17)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text("Team Arthur", color = Green, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Application diagnostic & deletion monitor. Built for real-time system inspection, residual detection, and activity auditing.",
                    color = Color(0xFFC8C8D0)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)
            ) { Text("Close", fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
private fun TimelineDialog(app: UninstalledApp, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = Panel,
        title = { Text("> ${app.appLabel}", fontFamily = Mono, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(app.packageName, color = Dim, fontFamily = Mono, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Text("First Active", color = Green, fontFamily = Mono, fontSize = 12.sp)
                Text(fmt(app.firstSeen), fontFamily = Mono, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("Last Active", color = Green, fontFamily = Mono, fontSize = 12.sp)
                Text("${fmt(app.lastActive)} (${rel(app.lastActive)})", fontFamily = Mono, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("Active Duration / Events", color = Green, fontFamily = Mono, fontSize = 12.sp)
                Text("${fmtDuration(app.activeDurationMs)} / ${app.eventCount}", fontFamily = Mono, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("Estimated Deletion", color = Red, fontFamily = Mono, fontSize = 12.sp)
                Text("Between ${fmt(app.lastActive)} and ${fmt(app.detectedAt)}", fontFamily = Mono, fontSize = 14.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.Black)
            ) { Text("Close", fontWeight = FontWeight.Bold) }
        }
    )
}
