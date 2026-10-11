package com.example.appaudit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.appaudit.data.UninstalledApp
import com.example.appaudit.repository.InstalledAppsRepository
import com.example.appaudit.usage.UsageStatsHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AuditScreen() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen() {
    val context = LocalContext.current
    val repo = remember { InstalledAppsRepository(context) }
    val scope = rememberCoroutineScope()

    var hasAccess by remember { mutableStateOf(UsageStatsHelper.hasUsageAccess(context)) }
    var uninstalls by remember { mutableStateOf(listOf<UninstalledApp>()) }

    LaunchedEffect(hasAccess) {
        if (hasAccess) {
            repo.runAuditScan()
            uninstalls = repo.recentUninstalls()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("App Audit") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {

            if (!hasAccess) {
                Text("Usage access permission chahiye data padhne ke liye.")
                Spacer(Modifier.height(8.dp))
                Button(onClick = { context.startActivity(UsageStatsHelper.usageAccessSettingsIntent()) }) {
                    Text("Enable Usage Access")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { hasAccess = UsageStatsHelper.hasUsageAccess(context) }) {
                    Text("I've enabled it — refresh")
                }
            } else {
                Text("Detected Uninstalls (last 30 days)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                LazyColumn {
                    items(uninstalls) { app -> UninstalledAppRow(app) }
                }

                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    scope.launch {
                        repo.runAuditScan()
                        uninstalls = repo.recentUninstalls()
                    }
                }) { Text("Scan Now") }
            }
        }
    }
}

@Composable
fun UninstalledAppRow(app: UninstalledApp) {
    val fmt = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(app.appLabel, style = MaterialTheme.typography.titleSmall)
            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
            Text("Last active: ${fmt.format(Date(app.lastActive))}")
            Text("Detected: ${fmt.format(Date(app.detectedAt))}")
            Text("Active duration: ${app.activeDurationMs / 1000}s, events: ${app.eventCount}")
        }
    }
}
