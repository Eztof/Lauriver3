package com.oliver.lauriver3

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@Serializable
data class Milestone(
    val id: String = "",
    val title: String,
    val emoji: String,
    val date: String,          // ISO-Format "YYYY-MM-DD"
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

// Vordefinierte Emoji-Auswahl
val MILESTONE_EMOJIS = listOf(
    "💕", "💋", "💍", "🥂", "✈️", "🏡", "🎂", "🌹",
    "💑", "🌍", "🎉", "⭐", "🌙", "☀️", "🐾", "🎵"
)

@Composable
fun MilestonesScreen() {
    val scope = rememberCoroutineScope()
    val today = LocalDate.now()

    var milestones by remember { mutableStateOf<List<Milestone>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Milestone?>(null) }

    fun load() {
        scope.launch {
            isLoading = true
            error = null
            try {
                milestones = supabase.from("milestones")
                    .select()
                    .decodeList<Milestone>()
                    .sortedBy { it.date }
            } catch (e: Exception) {
                error = e.message?.take(100)
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            error != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.WifiOff, contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Verbindung fehlgeschlagen", fontWeight = FontWeight.Bold)
                    Text(error ?: "", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { load() }) { Text("Erneut versuchen") }
                }
            }
            milestones.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("💕", fontSize = 64.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Noch keine Meilensteine", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Fügt euren ersten Moment hinzu!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(milestones, key = { it.id }) { milestone ->
                        MilestoneCard(
                            milestone = milestone,
                            today = today,
                            onDelete = { deleteTarget = milestone }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(72.dp)) }
                }
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Meilenstein hinzufügen",
                tint = MaterialTheme.colorScheme.onPrimary)
        }
    }

    // Hinzufügen-Dialog
    if (showAddDialog) {
        AddMilestoneDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { newMilestone ->
                scope.launch {
                    try {
                        supabase.from("milestones").insert(newMilestone)
                        showAddDialog = false
                        load()
                    } catch (e: Exception) {
                        error = e.message?.take(100)
                    }
                }
            }
        )
    }

    // Löschen-Dialog
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Text(target.emoji, fontSize = 28.sp) },
            title = { Text("Löschen?") },
            text = { Text("\"${target.title}\" wirklich löschen?") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            supabase.from("milestones")
                                .delete { filter { eq("id", target.id) } }
                            deleteTarget = null
                            load()
                        } catch (e: Exception) {
                            error = e.message?.take(100)
                        }
                    }
                }) { Text("Löschen", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
fun MilestoneCard(milestone: Milestone, today: LocalDate, onDelete: () -> Unit) {
    val date = runCatching { LocalDate.parse(milestone.date) }.getOrNull() ?: return
    val germanFmt = DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale.GERMAN)

    // Countdown: Nächstes Jubiläum
    val thisYear = date.withYear(today.year)
    val nextAnniversary = if (!thisYear.isBefore(today)) thisYear else thisYear.plusYears(1)
    val daysUntil = ChronoUnit.DAYS.between(today, nextAnniversary)
    val yearsAgo = ChronoUnit.YEARS.between(date, today).toInt()

    // Farbe basierend auf Nähe
    val cardColor = when {
        daysUntil == 0L -> MaterialTheme.colorScheme.primaryContainer
        daysUntil <= 7  -> MaterialTheme.colorScheme.secondaryContainer
        else            -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji-Kreis
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text(milestone.emoji, fontSize = 26.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(milestone.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(date.format(germanFmt), fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!milestone.note.isNullOrBlank()) {
                    Text(milestone.note, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Countdown-Bereich
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when {
                    daysUntil == 0L -> {
                        Text("🎉", fontSize = 22.sp)
                        Text("Heute!", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    daysUntil == 1L -> {
                        Text("⭐", fontSize = 22.sp)
                        Text("Morgen", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    else -> {
                        Text(
                            daysUntil.toString(),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Tage", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (yearsAgo > 0) {
                    Text(
                        "vor ${yearsAgo} J.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Löschen",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMilestoneDialog(
    onDismiss: () -> Unit,
    onConfirm: (Milestone) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("💕") }
    var note by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf(false) }
    val germanInputFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Neuer Meilenstein", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // Emoji-Auswahl
                Text("Emoji", fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                LazyEmojiRow(
                    emojis = MILESTONE_EMOJIS,
                    selected = selectedEmoji,
                    onSelect = { selectedEmoji = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Bezeichnung") },
                    placeholder = { Text("z.B. Erster Kuss") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it; dateError = false },
                    label = { Text("Datum") },
                    placeholder = { Text("TT.MM.JJJJ") },
                    singleLine = true,
                    isError = dateError,
                    supportingText = if (dateError) {
                        { Text("Format: TT.MM.JJJJ", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Notiz (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedDate = runCatching {
                                LocalDate.parse(dateText.trim(), germanInputFmt)
                            }.getOrNull()
                            if (parsedDate == null) {
                                dateError = true
                                return@Button
                            }
                            if (title.isBlank()) return@Button
                            onConfirm(
                                Milestone(
                                    title = title.trim(),
                                    emoji = selectedEmoji,
                                    date = parsedDate.toString(),
                                    note = note.trim().ifBlank { null }
                                )
                            )
                        },
                        enabled = title.isNotBlank() && dateText.isNotBlank()
                    ) {
                        Text("Speichern")
                    }
                }
            }
        }
    }
}

@Composable
fun LazyEmojiRow(emojis: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Zwei Zeilen à 8
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(emojis.take(8), emojis.drop(8)).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { emoji ->
                        val isSelected = emoji == selected
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .then(
                                    if (!isSelected) Modifier
                                    else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            TextButton(
                                onClick = { onSelect(emoji) },
                                modifier = Modifier.size(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(emoji, fontSize = 20.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}
