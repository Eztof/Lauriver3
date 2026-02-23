package com.oliver.lauriver3

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.oliver.lauriver3.ui.theme.Lauriver3Theme
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class AppVersion(
    val id: Int = 0,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    @SerialName("apk_url") val apkUrl: String? = null,
    @SerialName("release_notes") val releaseNotes: String? = null
)

val supabase = createSupabaseClient(
    supabaseUrl = SupabaseConfig.URL,
    supabaseKey = SupabaseConfig.ANON_KEY
) {
    install(Postgrest)
}

sealed class NavItem(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    data object Grades     : NavItem("Notenrechner",  Icons.Default.School)
    data object Waste      : NavItem("Müllkalender",  Icons.Default.DateRange)
    data object Milestones : NavItem("Meilensteine",  Icons.Default.Favorite)
    data object Update     : NavItem("App-Update",    Icons.Default.SystemUpdate)
}

val navItems = listOf(NavItem.Grades, NavItem.Waste, NavItem.Milestones, NavItem.Update)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Lauriver3Theme {
                MainApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<NavItem>(NavItem.Grades) }
    var showWasteLegend by remember { mutableStateOf(false) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(260.dp)) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Lauriver 💕",
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                navItems.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        selected = selected == item,
                        onClick = {
                            selected = item
                            showWasteLegend = false
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selected.label) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menü")
                        }
                    },
                    actions = {
                        if (selected == NavItem.Waste) {
                            IconButton(onClick = { showWasteLegend = true }) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Legende",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                when (selected) {
                    NavItem.Grades     -> GradeCalculatorScreen()
                    NavItem.Waste      -> WasteCalendarScreen(
                        showLegend = showWasteLegend,
                        onDismissLegend = { showWasteLegend = false }
                    )
                    NavItem.Milestones -> MilestonesScreen()
                    NavItem.Update     -> UpdateScreen()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Update-Screen
// ---------------------------------------------------------------------------
enum class DownloadState { IDLE, DOWNLOADING, DONE, FAILED }

@Composable
fun UpdateScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentVersionCode = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (_: Exception) { 1 }
    }

    var latestVersion by remember { mutableStateOf<AppVersion?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadState by remember { mutableStateOf(DownloadState.IDLE) }
    var downloadProgress by remember { mutableStateOf(0f) }

    // Prüfen ob "Unbekannte Quellen" erlaubt ist – nach jedem Resume neu prüfen
    var canInstallUnknown by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.packageManager.canRequestPackageInstalls()
            else true
        )
    }

    // Neu prüfen wenn der Screen rekomposiert wird (z.B. nach Rückkehr aus Einstellungen)
    LaunchedEffect(downloadState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            canInstallUnknown = context.packageManager.canRequestPackageInstalls()
        }
    }

    fun checkForUpdate() {
        scope.launch {
            isLoading = true
            error = null
            downloadState = DownloadState.IDLE
            try {
                val versions = supabase.from("app_version")
                    .select()
                    .decodeList<AppVersion>()
                latestVersion = versions.maxByOrNull { it.versionCode }
            } catch (e: Exception) {
                error = "Verbindung fehlgeschlagen: ${e.message?.take(80)}"
            }
            isLoading = false
        }
    }

    fun startDownload(url: String, versionCode: Int) {
        scope.launch {
            downloadState = DownloadState.DOWNLOADING
            downloadProgress = 0f
            try {
                // APK in internen Cache laden – FileProvider hat hier garantierten Zugriff
                val apkFile = File(context.cacheDir, "lauriver-update-$versionCode.apk")

                withContext(Dispatchers.IO) {
                    val connection = URL(url).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 60_000
                    connection.connect()

                    val totalBytes = connection.contentLength.toLong()
                    var downloadedBytes = 0L

                    connection.inputStream.use { input ->
                        apkFile.outputStream().use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (totalBytes > 0) {
                                    withContext(Dispatchers.Main) {
                                        downloadProgress = downloadedBytes.toFloat() / totalBytes
                                    }
                                }
                            }
                        }
                    }
                    connection.disconnect()
                }

                // Prüfen ob die Datei eine echte APK ist (fängt mit "PK" = ZIP-Header)
                val header = apkFile.inputStream().use { it.readNBytes(2) }
                if (header.size < 2 || header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                    apkFile.delete()
                    error = "Download-Fehler: Kein gültiger APK-Link. Bitte GitHub-Release-URL verwenden."
                    downloadState = DownloadState.FAILED
                    return@launch
                }

                downloadState = DownloadState.DONE
                installApk(context, apkFile)

            } catch (e: Exception) {
                downloadState = DownloadState.FAILED
                error = "Download fehlgeschlagen: ${e.message?.take(80)}"
            }
        }
    }

    LaunchedEffect(Unit) { checkForUpdate() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SystemUpdate,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text("App-Update", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // Installierte Version
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PhoneAndroid, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("Installierte Version", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Version $currentVersionCode", fontSize = 18.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Warnung: Berechtigung fehlt
        if (!canInstallUnknown) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Installation aus unbekannten Quellen nicht erlaubt",
                            fontWeight = FontWeight.Medium, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Text("Berechtigung erteilen →")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        when {
            isLoading -> {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
                Text("Prüfe auf Updates...", modifier = Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            error != null && downloadState != DownloadState.FAILED -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.WifiOff, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 14.sp)
                    }
                }
            }

            latestVersion != null -> {
                val latest = latestVersion!!
                val hasUpdate = latest.versionCode > currentVersionCode
                val hasUrl = !latest.apkUrl.isNullOrBlank() &&
                             !latest.apkUrl.contains("PLACEHOLDER")

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasUpdate)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (hasUpdate) Icons.Default.NewReleases
                                else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (hasUpdate) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (hasUpdate) "Update verfügbar!" else "App ist aktuell ✓",
                                fontWeight = FontWeight.Bold, fontSize = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Neueste Version: ${latest.versionCode}", fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)

                        if (!latest.releaseNotes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Was ist neu:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(latest.releaseNotes, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (hasUpdate && hasUrl) {
                            Spacer(modifier = Modifier.height(14.dp))
                            when (downloadState) {
                                DownloadState.IDLE -> {
                                    Button(
                                        onClick = {
                                            // Berechtigung nochmal aktuell prüfen
                                            canInstallUnknown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                                                context.packageManager.canRequestPackageInstalls()
                                            else true

                                            if (canInstallUnknown) {
                                                startDownload(latest.apkUrl!!, latest.versionCode)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = canInstallUnknown
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Version ${latest.versionCode} herunterladen")
                                    }
                                    if (!canInstallUnknown) {
                                        Text(
                                            "Bitte erst die Berechtigung oben erteilen.",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }

                                DownloadState.DOWNLOADING -> {
                                    Text("Wird heruntergeladen...", fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (downloadProgress > 0f) {
                                        LinearProgressIndicator(
                                            progress = { downloadProgress },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "${(downloadProgress * 100).toInt()}%",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Die Installation öffnet sich automatisch danach.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DownloadState.DONE -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Download fertig – Installer wurde geöffnet.",
                                            fontSize = 14.sp)
                                    }
                                }

                                DownloadState.FAILED -> {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Error, contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Download fehlgeschlagen.", fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.error)
                                        }
                                        if (error != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(error!!, fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        OutlinedButton(
                                            onClick = { startDownload(latest.apkUrl!!, latest.versionCode) },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Erneut versuchen")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = { checkForUpdate() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Erneut prüfen")
        }
    }
}

// ---------------------------------------------------------------------------
// APK installieren via FileProvider – direkt aus internem Cache
// ---------------------------------------------------------------------------
fun installApk(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
    context.startActivity(intent)
}
