package com.oliver.lauriver3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

@Serializable
data class GradeConfigEntry(
    val id: String = "",
    @SerialName("system_type") val systemType: String,
    @SerialName("grade_label") val gradeLabel: String,
    @SerialName("min_pct") val minPct: Double,
    @SerialName("max_pct") val maxPct: Double
)

data class GradeRange(
    val grade: String,
    val minPoints: Int,
    val maxPoints: Int,
    val color: Color
)

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

val DEFAULT_CONFIG_1_6 = listOf(
    GradeConfigEntry(systemType = "1-6", gradeLabel = "1 – sehr gut",     minPct = 0.96, maxPct = 1.00),
    GradeConfigEntry(systemType = "1-6", gradeLabel = "2 – gut",          minPct = 0.80, maxPct = 0.95),
    GradeConfigEntry(systemType = "1-6", gradeLabel = "3 – befriedigend", minPct = 0.60, maxPct = 0.79),
    GradeConfigEntry(systemType = "1-6", gradeLabel = "4 – ausreichend",  minPct = 0.45, maxPct = 0.59),
    GradeConfigEntry(systemType = "1-6", gradeLabel = "5 – mangelhaft",   minPct = 0.16, maxPct = 0.44),
    GradeConfigEntry(systemType = "1-6", gradeLabel = "6 – ungenügend",   minPct = 0.00, maxPct = 0.15),
)

val DEFAULT_CONFIG_0_15 = listOf(
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

fun configToRanges(config: List<GradeConfigEntry>, maxPoints: Int, colors: List<Color>): List<GradeRange> {
    return config.mapIndexed { i, entry ->
        val minPts = ceil(entry.minPct * maxPoints).toInt()
        val maxPts = floor(entry.maxPct * maxPoints).toInt()
        GradeRange(entry.gradeLabel, minPts, maxPts.coerceAtLeast(minPts), colors.getOrElse(i) { Color.Gray })
    }
}

@Composable
fun GradeCalculatorScreen() {
    val scope = rememberCoroutineScope()
    var maxPointsText by remember { mutableStateOf("") }
    var selectedSystem by remember { mutableStateOf("1-6") }
    var gradeRanges by remember { mutableStateOf<List<GradeRange>>(emptyList()) }

    var config1_6 by remember { mutableStateOf(DEFAULT_CONFIG_1_6) }
    var config0_15 by remember { mutableStateOf(DEFAULT_CONFIG_0_15) }
    var showConfigDialog by remember { mutableStateOf(false) }

    fun loadConfig() {
        scope.launch {
            try {
                val all = supabase.from("grade_config")
                    .select()
                    .decodeList<GradeConfigEntry>()
                val c16 = all.filter { it.systemType == "1-6" }
                val c015 = all.filter { it.systemType == "0-15" }
                if (c16.isNotEmpty()) config1_6 = c16
                if (c015.isNotEmpty()) config0_15 = c015
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) { loadConfig() }

    val currentConfig = if (selectedSystem == "1-6") config1_6 else config0_15
    val currentColors = if (selectedSystem == "1-6") COLORS_1_6 else COLORS_0_15

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("1-6" to "Klasse 1–10", "0-15" to "Oberstufe").forEach { (key, label) ->
                val isSelected = selectedSystem == key
                Button(
                    onClick = { selectedSystem = key; gradeRanges = emptyList() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary
                                         else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
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
                enabled = maxPointsText.isNotBlank()
            ) {
                Text("Berechnen", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = { showConfigDialog = true },
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Konfigurieren", modifier = Modifier.size(20.dp))
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
                Text("Note", fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f))
                Text("von", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("bis", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("ab %", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
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
            config = currentConfig,
            systemType = selectedSystem,
            onDismiss = { showConfigDialog = false },
            onSave = { newConfig ->
                scope.launch {
                    try {
                        supabase.from("grade_config")
                            .delete { filter { eq("system_type", selectedSystem) } }
                        supabase.from("grade_config").insert(newConfig)
                        if (selectedSystem == "1-6") config1_6 = newConfig
                        else config0_15 = newConfig
                        val max = maxPointsText.toIntOrNull()
                        if (max != null && gradeRanges.isNotEmpty()) {
                            gradeRanges = configToRanges(newConfig, max, currentColors)
                        }
                    } catch (_: Exception) {}
                }
                showConfigDialog = false
            },
            onReset = {
                scope.launch {
                    val defaults = if (selectedSystem == "1-6") DEFAULT_CONFIG_1_6 else DEFAULT_CONFIG_0_15
                    try {
                        supabase.from("grade_config")
                            .delete { filter { eq("system_type", selectedSystem) } }
                        supabase.from("grade_config").insert(defaults)
                        if (selectedSystem == "1-6") config1_6 = defaults
                        else config0_15 = defaults
                        val max = maxPointsText.toIntOrNull()
                        if (max != null && gradeRanges.isNotEmpty()) {
                            gradeRanges = configToRanges(defaults, max, currentColors)
                        }
                    } catch (_: Exception) {}
                }
                showConfigDialog = false
            }
        )
    }
}

@Composable
fun GradeConfigDialog(
    config: List<GradeConfigEntry>,
    systemType: String,
    onDismiss: () -> Unit,
    onSave: (List<GradeConfigEntry>) -> Unit,
    onReset: () -> Unit
) {
    // Slider-Werte in ganzen Prozent (0..100)
    val sliderValues = remember {
        config.map { mutableFloatStateOf((it.minPct * 100).roundToInt().toFloat()) }
    }
    val colors = if (systemType == "1-6") COLORS_1_6 else COLORS_0_15

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Mindestprozente",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onReset) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = "Zurücksetzen",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    if (systemType == "1-6") "Klasse 1–10" else "Oberstufe",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(config.size) { i ->
                        val barColor = colors.getOrElse(i) { Color.Gray }
                        val currentVal = sliderValues[i].floatValue.roundToInt()

                        // Slider darf nicht über Schwelle der besseren Note steigen
                        val upperLimit = if (i == 0) 100f
                                         else (sliderValues[i - 1].floatValue.roundToInt() - 1)
                                             .toFloat().coerceAtLeast(0f)
                        // Slider darf nicht unter Schwelle der schlechteren Note fallen
                        val lowerLimit = if (i == config.lastIndex) 0f
                                         else (sliderValues[i + 1].floatValue.roundToInt() + 1)
                                             .toFloat().coerceAtMost(100f)

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(barColor, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        config[i].gradeLabel,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                // Prozentwert als gut lesbares Badge
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = barColor.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        "ab $currentVal%",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = barColor
                                    )
                                }
                            }

                            Slider(
                                value = sliderValues[i].floatValue,
                                onValueChange = { newVal ->
                                    val clamped = newVal
                                        .coerceAtLeast(lowerLimit)
                                        .coerceAtMost(upperLimit)
                                    sliderValues[i].floatValue = clamped.roundToInt().toFloat()
                                },
                                valueRange = 0f..100f,
                                steps = 99,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = barColor,
                                    activeTrackColor = barColor,
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
                    }) {
                        Text("Speichern")
                    }
                }
            }
        }
    }
}
