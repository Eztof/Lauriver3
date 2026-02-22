package com.oliver.lauriver3

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
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
import com.oliver.lauriver3.ui.theme.Lauriver3Theme
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// --- Supabase Datenmodell ---
@Serializable
data class AppVersion(
    val id: Int = 0,
    @SerialName("version_code") val versionCode: Int,
    @SerialName("version_name") val versionName: String,
    // apk_storage_path = Pfad im Supabase Storage Bucket "apk-releases", z.B. "lauriver-1.1.apk"
    // apk_url = direkte externe URL (Fallback / Alternative)
    @SerialName("apk_storage_path") val apkStoragePath: String? = null,
    @SerialName("apk_url") val apkUrl: String? = null,
    @SerialName("release_notes") val releaseNotes: String? = null
)

// --- Supabase Client (mit Storage) ---
val supabase = createSupabaseClient(
    supabaseUrl = SupabaseConfig.URL,
    supabaseKey = SupabaseConfig.ANON_KEY
) {
    install(Postgrest)
    install(Storage)
}

// --- Navigations-Einträge ---
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
                    NavItem.Waste      -> WasteCalendarScreen()
                    NavItem.Milestones -> MilestonesScreen()
                    NavItem.Update     -> UpdateScreen()
                }
            }
        }
    }
}

// --- Auto-Update Screen ---
@Composable
fun UpdateScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentVersionCode = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (_: Exception) { 1 }
    }

    var latestVersion by remember { mutableStateOf<AppVersion?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var downloadStarted by remember { mutableStateOf(false) }

    fun checkForUpdate() {
        scope.launch {
            isLoading = true
            error = null
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

    LaunchedEffect(Unit) { checkForUpdate() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.SystemUpdate,
            contentDescription = null,
            modifier = Modifier.size(64.dp).padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text("App-Update", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // Aktuelle Version
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Installierte Version", fontWeight = FontWeight.Medium, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Version $currentVersionCode", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                CircularProgressIndicator()
                Text("Prüfe auf Updates...", modifier = Modifier.padding(top = 8.dp))
            }
            error != null -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(error!!, modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            latestVersion != null -> {
                val latest = latestVersion!!
                val hasUpdate = latest.versionCode > currentVersionCode

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
                                if (hasUpdate) Icons.Default.NewReleases else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (hasUpdate) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (hasUpdate) "Update verfügbar!" else "App ist aktuell ✓",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Neueste Version: ${latest.versionName} (${latest.versionCode})",
                            fontSize = 14.sp)
                        if (!latest.releaseNotes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Was ist neu:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text(latest.releaseNotes, fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (hasUpdate) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        downloadStarted = true
                                        try {
                                            // Supabase Storage hat Priorität
                                            val path = latest.apkStoragePath
                                            if (!path.isNullOrBlank()) {
                                                val signedUrl = supabase.storage
                                                    .from("apk-releases")
                                                    .createSignedUrl(path, expiresIn = 300.seconds)
                                                downloadApk(context, signedUrl, latest.versionName)
                                            } else if (!latest.apkUrl.isNullOrBlank()) {
                                                downloadApk(context, latest.apkUrl!!, latest.versionName)
                                            }
                                        } catch (e: Exception) {
                                            error = "Download fehlgeschlagen: ${e.message?.take(60)}"
                                            downloadStarted = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !downloadStarted
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (downloadStarted) "Download läuft..." else "Jetzt herunterladen")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(
            onClick = { downloadStarted = false; checkForUpdate() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Erneut prüfen")
        }
    }
}

fun downloadApk(context: Context, url: String, versionName: String) {
    val request = DownloadManager.Request(Uri.parse(url)).apply {
        setTitle("Lauriver Update v$versionName")
        setDescription("APK wird heruntergeladen...")
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "lauriver-$versionName.apk")
        setMimeType("application/vnd.android.package-archive")
    }
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
}
