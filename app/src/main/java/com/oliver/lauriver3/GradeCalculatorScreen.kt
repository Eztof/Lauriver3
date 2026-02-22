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

data class GradeRange(
    val grade: String,
    val minPoints: Int,
    val maxPoints: Int,
    val color: Color
)

// Klassen 1–10: 1=96–100%, 2=80–95%, 3=60–79%, 4=45–59%, 5=16–44%, 6=0–15%
fun calculateGrades1to6(maxPoints: Int): List<GradeRange> {
    data class Def(val grade: String, val minPct: Double, val maxPct: Double, val color: Color)
    val defs = listOf(
        Def("1 – sehr gut",     0.96, 1.00, Color(0xFF1B5E20)),
        Def("2 – gut",          0.80, 0.95, Color(0xFF388E3C)),
        Def("3 – befriedigend", 0.60, 0.79, Color(0xFF9E9D24)),
        Def("4 – ausreichend",  0.45, 0.59, Color(0xFFF57F17)),
        Def("5 – mangelhaft",   0.16, 0.44, Color(0xFFE65100)),
        Def("6 – ungenügend",   0.00, 0.15, Color(0xFFC62828)),
    )
    return defs.map { d ->
        val minPts = ceil(d.minPct * maxPoints).toInt()
        val maxPts = floor(d.maxPct * maxPoints).toInt()
        GradeRange(d.grade, minPts, maxPts.coerceAtLeast(minPts), d.color)
    }
}

// Oberstufe: 15=95–100%, 14=90–94%, 13=85–89%, 12=80–84%, 11=75–79%, 10=70–74%,
//  9=65–69%, 8=60–64%, 7=55–59%, 6=50–54%, 5=45–49%, 4=40–44%,
//  3=33–39%, 2=27–32%, 1=20–26%, 0=0–19%
fun calculateGrades0to15(maxPoints: Int): List<GradeRange> {
    data class Def(val grade: String, val minPct: Double, val maxPct: Double, val color: Color)
    val defs = listOf(
        Def("15 – 1+", 0.95, 1.00, Color(0xFF1B5E20)),
        Def("14 – 1",  0.90, 0.94, Color(0xFF2E7D32)),
        Def("13 – 1−", 0.85, 0.89, Color(0xFF388E3C)),
        Def("12 – 2+", 0.80, 0.84, Color(0xFF558B2F)),
        Def("11 – 2",  0.75, 0.79, Color(0xFF689F38)),
        Def("10 – 2−", 0.70, 0.74, Color(0xFF8BC34A)),
        Def( "9 – 3+", 0.65, 0.69, Color(0xFFC6D122)),
        Def( "8 – 3",  0.60, 0.64, Color(0xFFF9A825)),
        Def( "7 – 3−", 0.55, 0.59, Color(0xFFF57F17)),
        Def( "6 – 4+", 0.50, 0.54, Color(0xFFEF6C00)),
        Def( "5 – 4",  0.45, 0.49, Color(0xFFE64A19)),
        Def( "4 – 4−", 0.40, 0.44, Color(0xFFD84315)),
        Def( "3 – 5+", 0.33, 0.39, Color(0xFFBF360C)),
        Def( "2 – 5",  0.27, 0.32, Color(0xFFB71C1C)),
        Def( "1 – 5−", 0.20, 0.26, Color(0xFFC62828)),
        Def( "0 – 6",  0.00, 0.19, Color(0xFF4A148C)),
    )
    return defs.map { d ->
        val minPts = ceil(d.minPct * maxPoints).toInt()
        val maxPts = floor(d.maxPct * maxPoints).toInt()
        GradeRange(d.grade, minPts, maxPts.coerceAtLeast(minPts), d.color)
    }
}

@Composable
fun GradeCalculatorScreen() {
    var maxPointsText by remember { mutableStateOf("") }
    var selectedSystem by remember { mutableStateOf("1-6") }
    var gradeRanges by remember { mutableStateOf<List<GradeRange>>(emptyList()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📝 Notenrechner", fontSize = 26.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp))
        Text("NRW Notenspiegel", fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp))

        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1-6" to "Klasse 1–10", "0-15" to "Oberstufe").forEach { (key, label) ->
                FilterChip(
                    selected = selectedSystem == key,
                    onClick = { selectedSystem = key; gradeRanges = emptyList() },
                    label = { Text(label, fontWeight = FontWeight.Medium) },
                    modifier = Modifier.weight(1f)
                )
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

        Button(
            onClick = {
                val max = maxPointsText.toIntOrNull() ?: return@Button
                gradeRanges = if (selectedSystem == "1-6") calculateGrades1to6(max)
                              else calculateGrades0to15(max)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = maxPointsText.isNotBlank()
        ) {
            Text("Berechnen", fontWeight = FontWeight.SemiBold)
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
}
