package com.oliver.lauriver3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

@Serializable
data class GradeConfigEntry(
    val id: String = "",
    @SerialName("system_type") val systemType: String,
    @SerialName("grade_label") val gradeLabel: String,
    @SerialName("min_pct") val minPct: Double,
    @SerialName("max_pct") val maxPct: Double
)

@Serializable
data class GradeProfile(
    val id: String = "",
    val name: String,
    @SerialName("system_type") val systemType: String,
    val entries: JsonArray,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ProfileEntry(
    @SerialName("grade_label") val gradeLabel: String,
    @SerialName("min_pct") val minPct: Double,
    @SerialName("max_pct") val maxPct: Double
)

// Typsichere Insert/Update-Klassen statt mapOf<String, Any>
@Serializable
data class GradeProfileInsert(
    val name: String,
    @SerialName("system_type") val systemType: String,
    val entries: JsonArray
)

@Serializable
data class GradeProfileUpdate(
    val entries: JsonArray,
    @SerialName("updated_at") val updatedAt: String
)

data class GradeRange(
    val grade: String,
    val minPoints: Int,
    val maxPoints: Int,
    val color: Color
)

// ─────────────────────────────────────────────────────────────────────────────
// Farben & Fallback-Defaults (nur bei Offline-Start genutzt)
// ─────────────────────────────────────────────────────────────────────────────

val COLORS_1_6 = listOf(
    Color(0xFF1B5E20), Color(0xFF388E3C), Color(0xFF9E9D24),
    Color(0xFFF57F17), Color(0xFFE65100), Color(0xFFC62828)
)
val COLORS_0_15 = listOf(
    Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF388E3C),
    Color(0xFF558B2F), Color(0xFF689F38), Color(0xFF8BC34A),
    Color(0xFFC6D122), Color(0xFFF9A825), Color(0xFFF57F17),
    Color(0xFFEF6C00), Color(0xFFE64A19), Color(0xFFD84315),
    Color(0xFFBF360C), Color(0xFFB71C1C), Color(0xFFC62828),
    Color(0xFF4A148C)
)

private val FALLBACK_CONFIG_1_6 = listOf(
    GradeConfigEntry(systemType = "1-6", gradeLabel = "1 – sehr gut",     minPct = 0.87, maxPct = 1.00),
    GradeConfigEntry(systemType = "1-6", gradeLabel = "2 – gut",          minPct = 0.73, maxPct = 0.86),
    GradeConfigEntry(systemType = "1-6", gradeLabel = "3 – befriedigend", minPct = 0.59, maxPct = 0.72),
    GradeConfigEntry(systemType = "1-6", gradeLabel = "4 – ausreichend",  minPct = 0.45, maxPct = 0.58),
    GradeConfigEntry(systemType = "1-6", gradeLabel = "5 – mangelhaft",   minPct = 0.18, maxPct = 0.44),
    GradeConfigEntry(systemType = "1-6", gradeLabel = "6 – ungenügend",   minPct = 0.00, maxPct = 0.17),
)

private val FALLBACK_CONFIG_0_15 = listOf(
    GradeConfigEntry(systemType = "0-15", gradeLabel = "15 – 1+", minPct = 0.95, maxPct = 1.00),
    GradeConfigEntry(systemType = "0-15", gradeLabel = "14 – 1",  minPct = 0.90, maxPct = 0.94),
    GradeConfigEntry(systemType = "0-15", gradeLabel = "13 – 1−", minPct = 0.85, maxPct = 0.89),
    GradeConfigEntry(systemType = "0-15", gradeLabel = "12 – 2+", minPct = 0.80, maxPct = 0.84),
    GradeConfigEntry(systemType = "0-15", gradeLabel = "11 – 2",  minPct = 0.75, maxPct = 0.79),
    GradeConfigEntry(systemType = "0-15", gradeLabel = "10 – 2−", minPct = 0.70, maxPct = 0.74),
    GradeConfigEntry(systemType = "0-15", gradeLabel =  "9 – 3+", minPct = 0.65, maxPct = 0.69),
    GradeConfigEntry(systemType = "0-15", gradeLabel =  "8 – 3",  minPct = 0.60, maxPct = 0.64),
    GradeConfigEntry(systemType = "0-15", gradeLabel =  "7 – 3−", minPct = 0.55, maxPct = 0.59),
    GradeConfigEntry(systemType = "0-15", gradeLabel =  "6 – 4+", minPct = 0.50, maxPct = 0.54),
    GradeConfigEntry(systemType = "0-15", gradeLabel =  "5 – 4",  minPct = 0.45, maxPct = 0.49),
    GradeConfigEntry(systemType = "0-15", gradeLabel =  "4 – 4−", minPct = 0.40, maxPct = 0.44),
    GradeConfigEntry(systemType = "0-15", gradeLabel =  "3 – 5+", minPct = 0.33, maxPct = 0.39),
    GradeConfigEntry(systemType = "0-15", gradeLabel =  "2 – 5",  minPct = 0.27, maxPct = 0.32),
    GradeConfigEntry(systemType = "0-15", gradeLabel =  "1 – 5−", minPct = 0.20, maxPct = 0.26),
    GradeConfigEntry(systemType = "0-15", gradeLabel =  "0 – 6",  minPct = 0.00, maxPct = 0.19),
)

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

fun configToRanges(config: List<GradeConfigEntry>, maxPoints: Int, colors: List<Color>): List<GradeRange> {
    return config.mapIndexed { i, entry ->
        val minPts = ceil(entry.minPct * maxPoints).toInt()
        val maxPts = floor(entry.maxPct * maxPoints).toInt()
        GradeRange(entry.gradeLabel, minPts, maxPts.coerceAtLeast(minPts), colors.getOrElse(i) { Color.Gray })
    }
}

fun configToJsonArray(config: List<GradeConfigEntry>): JsonArray {
    val entries = config.map { ProfileEntry(it.gradeLabel, it.minPct, it.maxPct) }
    val jsonString = Json.encodeToString(entries)
    return Json.decodeFromString(jsonString)
}

fun jsonArrayToConfig(systemType: String, jsonArray: JsonArray): List<GradeConfigEntry> {
    return jsonArray.map { element ->
        val entry = Json.decodeFromJsonElement<ProfileEntry>(element)
        GradeConfigEntry(systemType = systemType, gradeLabel = entry.gradeLabel,
            minPct = entry.minPct, maxPct = entry.maxPct)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Haupt-Screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GradeCalculatorScreen() {
    val scope = rememberCoroutineScope()
    var maxPointsText by remember { mutableStateOf("") }
    var selectedSystem by remember { mutableStateOf("1-6") }
    var gradeRanges by remember { mutableStateOf<List<GradeRange>>(emptyList()) }

    var config1_6  by remember { mutableStateOf(FALLBACK_CONFIG_1_6) }
    var config0_15 by remember { mutableStateOf(FALLBACK_CONFIG_0_15) }
    var isLoadingDefaults by remember { mutableStateOf(true) }

    var showConfigDialog  by remember { mutableStateOf(false) }
    var showSaveDialog    by remember { mutableStateOf(false) }
    var showLoadDialog    by remember { mutableStateOf(false) }
    var activeProfileName by remember { mutableStateOf<String?>(null) }

    val currentConfig = if (selectedSystem == "1-6") config1_6 else config0_15
    val currentColors = if (selectedSystem == "1-6") COLORS_1_6 else COLORS_0_15

    fun loadDefaultsFromDb() {
        scope.launch {
            isLoadingDefaults = true
            try {
                val all = supabase.from("grade_config")
                    .select()
                    .decodeList<GradeConfigEntry>()
                val c16  = all.filter { it.systemType == "1-6" }
                val c015 = all.filter { it.systemType == "0-15" }
                if (c16.isNotEmpty())  config1_6  = c16
                if (c015.isNotEmpty()) config0_15 = c015
            } catch (_: Exception) { /* Fallback bleibt */ }
            isLoadingDefaults = false
        }
    }

    LaunchedEffect(Unit) { loadDefaultsFromDb() }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("1-6" to "Klasse 1–10", "0-15" to "Oberstufe").forEach { (key, label) ->
                val isSelected = selectedSystem == key
                Button(
                    onClick = {
                        selectedSystem = key
                        gradeRanges = emptyList()
                        activeProfileName = null
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                         else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor   = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                         else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = if (isSelected) 4.dp else 0.dp
                    )
                ) {
                    Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        if (isLoadingDefaults) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
        }

        if (activeProfileName != null) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Text("Profil: $activeProfileName", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium)
                }
            }
        }

        OutlinedTextField(
            value = maxPointsText,
            onValueChange = { v -> maxPointsText = v.filter { it.isDigit() } },
            label = { Text("Maximale Punktzahl") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Text("Pkt.", modifier = Modifier.padding(end = 8.dp)) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    val max = maxPointsText.toIntOrNull() ?: return@Button
                    gradeRanges = configToRanges(currentConfig, max, currentColors)
                },
                modifier = Modifier.weight(1f),
                enabled = maxPointsText.isNotBlank() && !isLoadingDefaults
            ) {
                Text("Berechnen", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = { showConfigDialog = true },
                modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Konfigurieren",
                    modifier = Modifier.size(20.dp))
            }
            OutlinedButton(onClick = { showSaveDialog = true },
                modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.BookmarkAdd, contentDescription = "Profil speichern",
                    modifier = Modifier.size(20.dp))
            }
            OutlinedButton(onClick = { showLoadDialog = true },
                modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) {
                Icon(Icons.Default.FolderOpen, contentDescription = "Profil laden",
                    modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (gradeRanges.isNotEmpty()) {
            val maxPoints = maxPointsText.toInt()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text("Note",  fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                Text("von",   fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("bis",   fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("ab %",  fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
            }
            Spacer(modifier = Modifier.height(4.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(gradeRanges) { range ->
                    val pct = if (maxPoints > 0) range.minPoints.toDouble() / maxPoints * 100 else 0.0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(range.color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(2f)) {
                            Box(modifier = Modifier.size(12.dp).background(range.color, RoundedCornerShape(3.dp)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(range.grade, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                        Text("${range.minPoints}", modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center, fontSize = 15.sp)
                        Text("${range.maxPoints}", modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center, fontSize = 15.sp)
                        Text("≥ ${"%.0f".format(pct)}%", modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End, fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showConfigDialog) {
        GradeConfigDialog(
            config     = currentConfig,
            systemType = selectedSystem,
            onDismiss  = { showConfigDialog = false },
            onSave     = { newConfig ->
                scope.launch {
                    try {
                        supabase.from("grade_config")
                            .delete { filter { eq("system_type", selectedSystem) } }
                        supabase.from("grade_config").insert(newConfig)
                        if (selectedSystem == "1-6") config1_6  = newConfig
                        else                         config0_15 = newConfig
                        activeProfileName = null
                        val max = maxPointsText.toIntOrNull()
                        if (max != null && gradeRanges.isNotEmpty())
                            gradeRanges = configToRanges(newConfig, max, currentColors)
                    } catch (_: Exception) {}
                }
                showConfigDialog = false
            },
            onReset    = {
                scope.launch {
                    try {
                        val dbDefaults = supabase.from("grade_config")
                            .select { filter { eq("system_type", selectedSystem) } }
                            .decodeList<GradeConfigEntry>()
                        if (dbDefaults.isNotEmpty()) {
                            if (selectedSystem == "1-6") config1_6  = dbDefaults
                            else                         config0_15 = dbDefaults
                        }
                        activeProfileName = null
                        val conf = if (selectedSystem == "1-6") config1_6 else config0_15
                        val max  = maxPointsText.toIntOrNull()
                        if (max != null && gradeRanges.isNotEmpty())
                            gradeRanges = configToRanges(conf, max, currentColors)
                    } catch (_: Exception) {}
                }
                showConfigDialog = false
            }
        )
    }

    if (showSaveDialog) {
        SaveProfileDialog(
            systemType    = selectedSystem,
            currentConfig = currentConfig,
            onDismiss     = { showSaveDialog = false },
            onSaved       = { name -> activeProfileName = name; showSaveDialog = false }
        )
    }

    if (showLoadDialog) {
        LoadProfileDialog(
            systemType = selectedSystem,
            onDismiss  = { showLoadDialog = false },
            onLoaded   = { profile, loadedConfig ->
                if (selectedSystem == "1-6") config1_6  = loadedConfig
                else                         config0_15 = loadedConfig
                activeProfileName = profile.name
                val max = maxPointsText.toIntOrNull()
                if (max != null)
                    gradeRanges = configToRanges(loadedConfig, max, currentColors)
                showLoadDialog = false
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profil speichern-Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SaveProfileDialog(
    systemType: String,
    currentConfig: List<GradeConfigEntry>,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var existingProfiles by remember { mutableStateOf<List<GradeProfile>>(emptyList()) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            existingProfiles = supabase.from("grade_profiles")
                .select { filter { eq("system_type", systemType) } }
                .decodeList<GradeProfile>()
        } catch (_: Exception) {}
    }

    val matchingProfile = existingProfiles.firstOrNull {
        it.name.equals(name.trim(), ignoreCase = true)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Profil speichern", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (systemType == "1-6") "Klasse 1–10" else "Oberstufe",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Profilname") },
                    placeholder = { Text("z.B. Mathe Abschluss") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null
                )

                if (matchingProfile != null) {
                    Spacer(Modifier.height(6.dp))
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)) {
                        Text("⚠ Profil \"${matchingProfile.name}\" wird überschrieben",
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val trimmed = name.trim()
                            if (trimmed.isBlank()) return@Button
                            isSaving = true
                            error = null
                            scope.launch {
                                try {
                                    val entriesArray = configToJsonArray(currentConfig)
                                    if (matchingProfile != null) {
                                        supabase.from("grade_profiles").update(
                                            GradeProfileUpdate(
                                                entries   = entriesArray,
                                                updatedAt = java.time.Instant.now().toString()
                                            )
                                        ) { filter { eq("id", matchingProfile.id) } }
                                    } else {
                                        supabase.from("grade_profiles").insert(
                                            GradeProfileInsert(
                                                name       = trimmed,
                                                systemType = systemType,
                                                entries    = entriesArray
                                            )
                                        )
                                    }
                                    isSaving = false
                                    onSaved(trimmed)
                                } catch (e: Exception) {
                                    error = "Fehler: ${e.message?.take(80)}"
                                    isSaving = false
                                }
                            }
                        },
                        enabled = name.isNotBlank() && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(if (matchingProfile != null) "Überschreiben" else "Speichern")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Profil laden-Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun LoadProfileDialog(
    systemType: String,
    onDismiss: () -> Unit,
    onLoaded: (GradeProfile, List<GradeConfigEntry>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var profiles by remember { mutableStateOf<List<GradeProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var deleteTarget by remember { mutableStateOf<GradeProfile?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            isLoading = true
            error = null
            try {
                profiles = supabase.from("grade_profiles")
                    .select { filter { eq("system_type", systemType) } }
                    .decodeList<GradeProfile>()
                    .sortedByDescending { it.updatedAt ?: it.createdAt ?: "" }
            } catch (e: Exception) {
                error = e.message?.take(80)
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Profil laden", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (systemType == "1-6") "Klasse 1–10" else "Oberstufe",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))

                when {
                    isLoading -> Box(Modifier.fillMaxWidth().height(80.dp),
                        contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    error != null -> Text("Fehler: $error",
                        color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    profiles.isEmpty() -> Text("Noch keine Profile gespeichert.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            Surface(shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(profile.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                        val count = try { jsonArrayToConfig(systemType, profile.entries).size }
                                                    catch (_: Exception) { 0 }
                                        Text("$count Noten", fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = {
                                        val loaded = try { jsonArrayToConfig(systemType, profile.entries) }
                                                     catch (_: Exception) { emptyList() }
                                        if (loaded.isNotEmpty()) onLoaded(profile, loaded)
                                    }) { Text("Laden") }
                                    IconButton(onClick = { deleteTarget = profile },
                                        modifier = Modifier.size(36.dp)) {
                                        Icon(Icons.Default.Delete, contentDescription = "Löschen",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Profil löschen?") },
            text = { Text("\"${target.name}\" wird unwiderruflich gelöscht.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            supabase.from("grade_profiles")
                                .delete { filter { eq("id", target.id) } }
                            deleteTarget = null
                            load()
                        } catch (_: Exception) { deleteTarget = null }
                    }
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Konfigurierungs-Dialog
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GradeConfigDialog(
    config: List<GradeConfigEntry>,
    systemType: String,
    onDismiss: () -> Unit,
    onSave: (List<GradeConfigEntry>) -> Unit,
    onReset: () -> Unit
) {
    val sliderValues = remember {
        config.map { mutableFloatStateOf((it.minPct * 100).roundToInt().toFloat()) }
    }
    val colors = if (systemType == "1-6") COLORS_1_6 else COLORS_0_15

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mindestprozente", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onReset) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Zurücksetzen",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(if (systemType == "1-6") "Klasse 1–10" else "Oberstufe",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(modifier = Modifier.heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(config.size) { i ->
                        val barColor   = colors.getOrElse(i) { Color.Gray }
                        val currentVal = sliderValues[i].floatValue.roundToInt()
                        val upperLimit = if (i == 0) 100f
                                         else (sliderValues[i - 1].floatValue.roundToInt() - 1)
                                             .toFloat().coerceAtLeast(0f)
                        val lowerLimit = if (i == config.lastIndex) 0f
                                         else (sliderValues[i + 1].floatValue.roundToInt() + 1)
                                             .toFloat().coerceAtMost(100f)
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp)
                                        .background(barColor, RoundedCornerShape(2.dp)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(config[i].gradeLabel, fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium)
                                }
                                Surface(shape = RoundedCornerShape(8.dp),
                                    color = barColor.copy(alpha = 0.15f)) {
                                    Text("ab $currentVal%",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = barColor)
                                }
                            }
                            Slider(
                                value = sliderValues[i].floatValue,
                                onValueChange = { newVal ->
                                    val clamped = newVal.coerceAtLeast(lowerLimit).coerceAtMost(upperLimit)
                                    sliderValues[i].floatValue = clamped.roundToInt().toFloat()
                                },
                                valueRange = 0f..100f,
                                steps = 99,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor         = barColor,
                                    activeTrackColor   = barColor,
                                    inactiveTrackColor = barColor.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val newConfig = config.mapIndexed { i, entry ->
                            val minPct = sliderValues[i].floatValue.roundToInt() / 100.0
                            val maxPct = if (i == 0) 1.00
                                         else (sliderValues[i - 1].floatValue.roundToInt() / 100.0) - 0.01
                            entry.copy(minPct = minPct, maxPct = maxPct.coerceAtLeast(minPct))
                        }
                        onSave(newConfig)
                    }) { Text("Speichern") }
                }
            }
        }
    }
}
