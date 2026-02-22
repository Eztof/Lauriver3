package com.oliver.lauriver3

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

// --- Datenmodell ---
enum class WasteType(
    val label: String,
    val emoji: String,
    val color: Color,
    val channelId: String
) {
    RESTMUELL_2W("Restmüll (2-wöchentlich)", "🗑️", Color(0xFF424242), "restmuell_2w"),
    RESTMUELL_4W_BLAU("Restmüll blauer Deckel (4-wöchentlich)", "🔵", Color(0xFF1565C0), "restmuell_4w_blau"),
    RESTMUELL_4W_GELB("Restmüll gelber Deckel (4-wöchentlich)", "🟡", Color(0xFFF9A825), "restmuell_4w_gelb"),
    BIO("Biotonne", "🌱", Color(0xFF2E7D32), "bio"),
    PAPIER("Altpapier", "📰", Color(0xFF1565C0), "papier"),
    LEICHTSTOFF("Leichtstoff (Gelber Sack)", "♻️", Color(0xFFF57F17), "leichtstoff"),
}

data class WasteEvent(val date: LocalDate, val type: WasteType)

// --- Alle Termine aus der ICS-Datei (Langenkamp 2026) ---
val WASTE_EVENTS: List<WasteEvent> by lazy {
    val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
    fun e(dateStr: String, type: WasteType) = WasteEvent(LocalDate.parse(dateStr, fmt), type)

    listOf(
        // Restmüll 2-wöchentlich
        e("20260107", WasteType.RESTMUELL_2W), e("20260121", WasteType.RESTMUELL_2W),
        e("20260204", WasteType.RESTMUELL_2W), e("20260218", WasteType.RESTMUELL_2W),
        e("20260304", WasteType.RESTMUELL_2W), e("20260318", WasteType.RESTMUELL_2W),
        e("20260331", WasteType.RESTMUELL_2W), e("20260415", WasteType.RESTMUELL_2W),
        e("20260429", WasteType.RESTMUELL_2W), e("20260513", WasteType.RESTMUELL_2W),
        e("20260528", WasteType.RESTMUELL_2W), e("20260610", WasteType.RESTMUELL_2W),
        e("20260624", WasteType.RESTMUELL_2W), e("20260708", WasteType.RESTMUELL_2W),
        e("20260722", WasteType.RESTMUELL_2W), e("20260805", WasteType.RESTMUELL_2W),
        e("20260819", WasteType.RESTMUELL_2W), e("20260902", WasteType.RESTMUELL_2W),
        e("20260916", WasteType.RESTMUELL_2W), e("20260930", WasteType.RESTMUELL_2W),
        e("20261014", WasteType.RESTMUELL_2W), e("20261028", WasteType.RESTMUELL_2W),
        e("20261111", WasteType.RESTMUELL_2W), e("20261125", WasteType.RESTMUELL_2W),
        e("20261209", WasteType.RESTMUELL_2W), e("20261222", WasteType.RESTMUELL_2W),

        // Restmüll 4-wöchentlich blauer Deckel
        e("20260107", WasteType.RESTMUELL_4W_BLAU), e("20260204", WasteType.RESTMUELL_4W_BLAU),
        e("20260304", WasteType.RESTMUELL_4W_BLAU), e("20260331", WasteType.RESTMUELL_4W_BLAU),
        e("20260429", WasteType.RESTMUELL_4W_BLAU), e("20260528", WasteType.RESTMUELL_4W_BLAU),
        e("20260624", WasteType.RESTMUELL_4W_BLAU), e("20260722", WasteType.RESTMUELL_4W_BLAU),
        e("20260819", WasteType.RESTMUELL_4W_BLAU), e("20260916", WasteType.RESTMUELL_4W_BLAU),
        e("20261014", WasteType.RESTMUELL_4W_BLAU), e("20261111", WasteType.RESTMUELL_4W_BLAU),
        e("20261209", WasteType.RESTMUELL_4W_BLAU), e("20261222", WasteType.RESTMUELL_4W_BLAU),

        // Restmüll 4-wöchentlich gelber Deckel
        e("20260121", WasteType.RESTMUELL_4W_GELB), e("20260218", WasteType.RESTMUELL_4W_GELB),
        e("20260318", WasteType.RESTMUELL_4W_GELB), e("20260415", WasteType.RESTMUELL_4W_GELB),
        e("20260513", WasteType.RESTMUELL_4W_GELB), e("20260610", WasteType.RESTMUELL_4W_GELB),
        e("20260708", WasteType.RESTMUELL_4W_GELB), e("20260805", WasteType.RESTMUELL_4W_GELB),
        e("20260902", WasteType.RESTMUELL_4W_GELB), e("20260930", WasteType.RESTMUELL_4W_GELB),
        e("20261028", WasteType.RESTMUELL_4W_GELB), e("20261125", WasteType.RESTMUELL_4W_GELB),

        // Biotonne
        e("20260114", WasteType.BIO), e("20260128", WasteType.BIO),
        e("20260211", WasteType.BIO), e("20260225", WasteType.BIO),
        e("20260311", WasteType.BIO), e("20260325", WasteType.BIO),
        e("20260409", WasteType.BIO), e("20260422", WasteType.BIO),
        e("20260506", WasteType.BIO), e("20260520", WasteType.BIO),
        e("20260603", WasteType.BIO), e("20260617", WasteType.BIO),
        e("20260701", WasteType.BIO), e("20260715", WasteType.BIO),
        e("20260729", WasteType.BIO), e("20260812", WasteType.BIO),
        e("20260826", WasteType.BIO), e("20260909", WasteType.BIO),
        e("20260923", WasteType.BIO), e("20261007", WasteType.BIO),
        e("20261021", WasteType.BIO), e("20261104", WasteType.BIO),
        e("20261118", WasteType.BIO), e("20261202", WasteType.BIO),
        e("20261216", WasteType.BIO), e("20261230", WasteType.BIO),

        // Altpapier (gleiche Termine wie Biotonne)
        e("20260114", WasteType.PAPIER), e("20260128", WasteType.PAPIER),
        e("20260211", WasteType.PAPIER), e("20260225", WasteType.PAPIER),
        e("20260311", WasteType.PAPIER), e("20260325", WasteType.PAPIER),
        e("20260409", WasteType.PAPIER), e("20260422", WasteType.PAPIER),
        e("20260506", WasteType.PAPIER), e("20260520", WasteType.PAPIER),
        e("20260603", WasteType.PAPIER), e("20260617", WasteType.PAPIER),
        e("20260701", WasteType.PAPIER), e("20260715", WasteType.PAPIER),
        e("20260729", WasteType.PAPIER), e("20260812", WasteType.PAPIER),
        e("20260826", WasteType.PAPIER), e("20260909", WasteType.PAPIER),
        e("20260923", WasteType.PAPIER), e("20261007", WasteType.PAPIER),
        e("20261021", WasteType.PAPIER), e("20261104", WasteType.PAPIER),
        e("20261118", WasteType.PAPIER), e("20261202", WasteType.PAPIER),
        e("20261216", WasteType.PAPIER), e("20261230", WasteType.PAPIER),

        // Leichtstoff / Gelber Sack
        e("20260115", WasteType.LEICHTSTOFF), e("20260212", WasteType.LEICHTSTOFF),
        e("20260312", WasteType.LEICHTSTOFF), e("20260410", WasteType.LEICHTSTOFF),
        e("20260507", WasteType.LEICHTSTOFF), e("20260605", WasteType.LEICHTSTOFF),
        e("20260702", WasteType.LEICHTSTOFF), e("20260730", WasteType.LEICHTSTOFF),
        e("20260827", WasteType.LEICHTSTOFF), e("20260924", WasteType.LEICHTSTOFF),
        e("20261022", WasteType.LEICHTSTOFF), e("20261119", WasteType.LEICHTSTOFF),
        e("20261217", WasteType.LEICHTSTOFF),
    ).sortedBy { it.date }
}

// --- Notification-Kanal erstellen ---
fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        WasteType.entries.forEach { type ->
            val channel = NotificationChannel(
                type.channelId,
                type.label,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Erinnerungen für ${type.label}" }
            nm.createNotificationChannel(channel)
        }
    }
}

// --- BroadcastReceiver für Benachrichtigungen ---
class WasteNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra("label") ?: return
        val emoji = intent.getStringExtra("emoji") ?: ""
        val channelId = intent.getStringExtra("channelId") ?: return
        val notifId = intent.getIntExtra("notifId", 0)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$emoji Morgen: $label")
            .setContentText("Tonne bis 6:00 Uhr an den Straßenrand stellen!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        nm.notify(notifId, notification)
    }
}

// --- Benachrichtigungen planen (Abend davor, 19:00 Uhr) ---
fun scheduleNotifications(context: Context, events: List<WasteEvent>) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val today = LocalDate.now()

    events.filter { it.date > today }.forEach { event ->
        val cal = Calendar.getInstance().apply {
            set(event.date.year, event.date.monthValue - 1, event.date.dayOfMonth - 1, 19, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val intent = Intent(context, WasteNotificationReceiver::class.java).apply {
            putExtra("label", event.type.label)
            putExtra("emoji", event.type.emoji)
            putExtra("channelId", event.type.channelId)
            putExtra("notifId", (event.date.toString() + event.type.name).hashCode())
        }
        val pi = PendingIntent.getBroadcast(
            context,
            (event.date.toString() + event.type.name).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (cal.timeInMillis > System.currentTimeMillis()) {
            try {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } catch (_: Exception) {}
        }
    }
}

fun cancelAllNotifications(context: Context, events: List<WasteEvent>) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    events.forEach { event ->
        val intent = Intent(context, WasteNotificationReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            (event.date.toString() + event.type.name).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pi)
    }
}

// --- UI ---
@Composable
fun WasteCalendarScreen() {
    val context = LocalContext.current
    val today = LocalDate.now()
    val germanFmt = DateTimeFormatter.ofPattern("EEE, dd.MM.", Locale.GERMAN)

    var notificationsEnabled by remember { mutableStateOf(false) }
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
            else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (granted && !notificationsEnabled) {
            notificationsEnabled = true
            createNotificationChannels(context)
            scheduleNotifications(context, WASTE_EVENTS)
        }
    }

    // Nächste + zukünftige Termine
    val upcoming = remember {
        WASTE_EVENTS.filter { !it.date.isBefore(today) }
            .groupBy { it.date }
            .toSortedMap()
    }

    // Nächster Termin
    val nextDate = upcoming.keys.firstOrNull()
    val daysUntilNext = nextDate?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) } ?: -1

    Column(modifier = Modifier.fillMaxSize()) {
        // Header Banner
        if (nextDate != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        when (daysUntilNext.toInt()) {
                            0 -> "🚨 Heute wird abgeholt!"
                            1 -> "⚠️ Morgen wird abgeholt!"
                            else -> "📅 Nächste Abholung in $daysUntilNext Tagen"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(nextDate.format(germanFmt), fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    upcoming[nextDate]?.forEach { event ->
                        Text("${event.type.emoji} ${event.type.label}", fontSize = 13.sp)
                    }
                }
            }
        }

        // Notification Toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Erinnerungen (Abend davor, 19:00)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotifPermission) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationsEnabled = true
                            createNotificationChannels(context)
                            scheduleNotifications(context, WASTE_EVENTS)
                        }
                    } else {
                        notificationsEnabled = false
                        cancelAllNotifications(context, WASTE_EVENTS)
                    }
                },
                thumbContent = {
                    Icon(
                        if (notificationsEnabled) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        // Liste aller Termine
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            upcoming.forEach { (date, events) ->
                item {
                    val isToday = date == today
                    val isTomorrow = date == today.plusDays(1)
                    val dayLabel = when {
                        isToday -> "Heute"
                        isTomorrow -> "Morgen"
                        else -> date.format(germanFmt)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isToday) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                                else if (isTomorrow) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            dayLabel,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = if (isToday) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        events.forEach { event ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(event.type.color, RoundedCornerShape(3.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "${event.type.emoji} ${event.type.label}",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
