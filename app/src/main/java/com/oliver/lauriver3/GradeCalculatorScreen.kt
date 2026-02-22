package com.oliver.lauriver3

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

// NRW Notenspiegel für Klassenarbeiten (Standard-Prozentsätze)
// 1: >= 87,5%, 2: >= 75%, 3: >= 62,5%, 4: >= 50%, 5: >= 25%, 6: < 25%
data class GradeRange(
    val grade: String,
    val minPoints: Int,
    val maxPoints: Int,
    val color: Color
)

fun calculateGrades1to6(maxPoints: Int): List<GradeRange> {
    // NRW Prozentspiegel (Klassenarbeiten & Klausuren)
    val thresholds = listOf(
        Triple("1", 0.875, Color(0xFF2E7D32)),
        Triple("2", 0.75,  Color(0xFF558B2F)),
        Triple("3", 0.625, Color(0xFF9E9D24)),
        Triple("4", 0.50,  Color(0xFFF57F17)),
        Triple("5", 0.25,  Color(0xFFE65100)),
        Triple("6", 0.0,   Color(0xFFC62828)),
    )

    val ranges = mutableListOf<GradeRange>()
    for (i in thresholds.indices) {
        val (grade, pct, color) = thresholds[i]
        val minPts = ceil(maxPoints * pct).toInt()
        val maxPts = if (i == 0) maxPoints
                     else ceil(maxPoints * thresholds[i - 1].second).toInt() - 1
        if (minPts <= maxPts) {
            ranges.add(GradeRange(grade, minPts, maxPts, color))
        }
    }
    return ranges
}

fun calculateGrades0to15(maxPoints: Int): List<GradeRange> {
    // Punkte 0–15 linear auf Maximalpunktzahl mappen
    val gradePoints = listOf(
        Pair("15 (1+)", 15), Pair("14 (1)",  14), Pair("13 (1−)", 13),
        Pair("12 (2+)", 12), Pair("11 (2)",  11), Pair("10 (2−)", 10),
        Pair("9 (3+)",   9), Pair("8 (3)",    8), Pair("7 (3−)",   7),
        Pair("6 (4+)",   6), Pair("5 (4)",    5), Pair("4 (4−)",   4),
        Pair("3 (5+)",   3), Pair("2 (5)",    2), Pair("1 (5−)",   1),
        Pair("0 (6)",    0),
    )
    val colors = listOf(
        Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF388E3C),
        Color(0xFF558B2F), Color(0xFF689F38), Color(0xFF7CB342),
        Color(0xFF9E9D24), Color(0xFFF9A825), Color(0xFFF57F17),
        Color(0xFFE65100), Color(0xFFBF360C), Color(0xFFD84315),
        Color(0xFFB71C1C), Color(0xFFC62828), Color(0xFFD32F2F),
        Color(0xFF4A148C),
    )

    return gradePoints.mapIndexed { index, (grade, gp) ->
        // Anteil = gp / 15, dann auf maxPoints skalieren
        val ratio = gp.toDouble() / 15.0
        val pts = (ratio * maxPoints).roundToInt()
        val nextRatio = if (index > 0) gradePoints[index - 1].second.toDouble() / 15.0 else 1.0
        val maxPts = if (index == 0) maxPoints
                     else ((nextRatio * maxPoints).roundToInt()) - 1
        GradeRange(grade, pts, maxPts.coerceAtLeast(pts), colors[index])
    }
}

@Composable
fun GradeCalculatorScreen() {
    var maxPointsText by remember { mutableStateOf("") }
    var selectedSystem by remember { mutableStateOf("1-6") }
    var gradeRanges by remember { mutableStateOf<List<GradeRange>>(emptyList()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "📝 Notenrechner",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "NRW Notenspiegel",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Notensystem auswählen
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("1-6", "0-15").forEach { system ->
                FilterChip(
                    selected = selectedSystem == system,
                    onClick = {
                        selectedSystem = system
                        gradeRanges = emptyList()
                    },
                    label = {
                        Text(
                            if (system == "1-6") "Noten 1–6" else "Punkte 0–15",
                            fontWeight = FontWeight.Medium
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Punkteingabe
        OutlinedTextField(
            value = maxPointsText,
            onValueChange = { maxPointsText = it.filter { c -> c.isDigit() } },
            label = { Text("Maximale Punktzahl") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Text("Pkt.", modifier = Modifier.padding(end = 8.dp)) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val max = maxPointsText.toIntOrNull() ?: return@Button
                gradeRanges = if (selectedSystem == "1-6")
                    calculateGrades1to6(max)
                else
                    calculateGrades0to15(max)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = maxPointsText.isNotBlank()
        ) {
            Text("Berechnen", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (gradeRanges.isNotEmpty()) {
            val maxPoints = maxPointsText.toInt()

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Note", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                Text("von", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("bis", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("ab %", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(gradeRanges) { range ->
                    val pct = if (maxPoints > 0) (range.minPoints.toDouble() / maxPoints * 100) else 0.0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(range.color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(range.color, RoundedCornerShape(3.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = range.grade,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                        Text(
                            text = "${range.minPoints}",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${range.maxPoints}",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "≥ ${"%.1f".format(pct)}%",
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.End,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
