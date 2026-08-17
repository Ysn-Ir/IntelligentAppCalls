package com.example.appcall.presentation.dashboard

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.data.local.AppLocalDatabase
import com.example.appcall.domain.repository.VoipRepository
import com.example.appcall.presentation.theme.ElectricViolet
import com.example.appcall.presentation.theme.NeonTeal
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AgendaSection(localDatabase: AppLocalDatabase, voipRepository: VoipRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var appointments by remember { mutableStateOf(localDatabase.getAgendaAppointments()) }
    var newTitle by remember { mutableStateOf("") }
    var isAddingAgenda by remember { mutableStateOf(false) }

    val todayFormatted = remember {
        SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH).format(Date()).replaceFirstChar { it.uppercase() }
    }

    // Dynamic date and time selectors
    var selectedDayOffset by remember { mutableStateOf(1) } // 0 = Today, 1 = Tomorrow, 2 = +2 days, etc.
    var customSelectedDateStr by remember { mutableStateOf<String?>(null) }
    var selectedTime by remember { mutableStateOf("14:00") }

    fun calculateScheduledDate(): String {
        val dayPart = if (customSelectedDateStr != null) {
            customSelectedDateStr!!
        } else {
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, selectedDayOffset)
            SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH).format(cal.time)
        }
        return "$dayPart à $selectedTime"
    }

    var dynamicScheduledAt by remember {
        mutableStateOf(calculateScheduledDate())
    }

    LaunchedEffect(selectedDayOffset, selectedTime, customSelectedDateStr) {
        dynamicScheduledAt = calculateScheduledDate()
    }

    fun openClockPicker() {
        val currentHour = selectedTime.substringBefore(":").toIntOrNull() ?: 14
        val currentMinute = selectedTime.substringAfter(":").toIntOrNull() ?: 0
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                selectedTime = String.format(Locale.US, "%02d:%02d", hourOfDay, minute)
            },
            currentHour,
            currentMinute,
            true
        ).show()
    }

    fun openCalendarPicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val pickedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                customSelectedDateStr = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH).format(pickedCal.time)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    LaunchedEffect(Unit) {
        voipRepository.fetchAgenda().onSuccess {
            appointments = localDatabase.getAgendaAppointments()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // ── HEADER ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Mon Agenda",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = todayFormatted,
                    color = NeonTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            voipRepository.fetchAgenda().onSuccess {
                                appointments = localDatabase.getAgendaAppointments()
                            }
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualiser",
                        tint = NeonTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Button(
                    onClick = { isAddingAgenda = !isAddingAgenda },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAddingAgenda) Color(0x33EF4444) else Color(0x3300F2FE)
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = if (isAddingAgenda) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null,
                        tint = if (isAddingAgenda) Color(0xFFF87171) else NeonTeal,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isAddingAgenda) "Fermer" else "Ajouter",
                        color = if (isAddingAgenda) Color(0xFFF87171) else NeonTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── COLLAPSIBLE COMPACT APPOINTMENT CREATION DRAWER ──
        AnimatedVisibility(
            visible = isAddingAgenda,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            placeholder = { Text("Titre du RDV...", color = Color.Gray, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonTeal,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newTitle.isNotBlank()) {
                                    val title = newTitle.trim()
                                    val time = dynamicScheduledAt
                                    val id = "app-${System.currentTimeMillis()}"
                                    newTitle = ""
                                    isAddingAgenda = false
                                    coroutineScope.launch {
                                        voipRepository.createAgenda(id, title, time)
                                        appointments = localDatabase.getAgendaAppointments()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("OK", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Day Selection Chips with dynamic calendar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("JOUR:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        listOf(
                            0 to "Aujourd'hui",
                            1 to "Demain",
                            2 to "Après-demain",
                            7 to "+1 sem."
                        ).forEach { (offset, label) ->
                            FilterChip(
                                selected = customSelectedDateStr == null && selectedDayOffset == offset,
                                onClick = {
                                    customSelectedDateStr = null
                                    selectedDayOffset = offset
                                },
                                label = { Text(label, fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonTeal,
                                    selectedLabelColor = Color(0xFF0F172A),
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = Color.White
                                )
                            )
                        }
                        // Custom Calendar Picker Chip
                        FilterChip(
                            selected = customSelectedDateStr != null,
                            onClick = { openCalendarPicker() },
                            label = { Text(customSelectedDateStr?.let { "📅 $it" } ?: "📅 Autre date...", fontSize = 9.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonTeal,
                                selectedLabelColor = Color(0xFF0F172A),
                                containerColor = Color(0xFF0F172A),
                                labelColor = NeonTeal
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Time Selection Chips with dynamic Clock Picker
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("HEURE:", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        listOf("09:00", "11:00", "14:00", "16:30", "18:00").forEach { time ->
                            FilterChip(
                                selected = selectedTime == time,
                                onClick = { selectedTime = time },
                                label = { Text(time, fontSize = 9.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricViolet,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color(0xFF0F172A),
                                    labelColor = Color.White
                                )
                            )
                        }
                        // Custom Dynamic Clock Picker Chip
                        FilterChip(
                            selected = true,
                            onClick = { openClockPicker() },
                            label = { Text("⏰ $selectedTime (Modifier)", fontSize = 9.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF3B82F6),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF0F172A),
                                labelColor = Color.White
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "📅 RDV prévu : $dynamicScheduledAt",
                        color = NeonTeal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── APPOINTMENTS LIST ──
        Text(
            text = "RENDEZ-VOUS PROGRAMMÉS (${appointments.size})",
            color = NeonTeal,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )

        if (appointments.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun rendez-vous planifié.\nLes rendez-vous détectés lors de vos appels apparaîtront ici automatiquement avec les coordonnées de l'interlocuteur.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(appointments, key = { it.id }) { app ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(NeonTeal.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = NeonTeal,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = app.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                // Status badge
                                val statusLabel = when (app.status) {
                                    "VALIDATED" -> "VALIDÉ"
                                    "CONFIRMED" -> "CONFIRMÉ"
                                    "PROPOSED" -> "PROPOSÉ"
                                    else -> app.status ?: "CONFIRMÉ"
                                }
                                Card(
                                    shape = RoundedCornerShape(4.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (app.status == "PROPOSED") Color(0x337C3AED) else Color(0x3300F2FE)
                                    )
                                ) {
                                    Text(
                                        text = statusLabel,
                                        color = if (app.status == "PROPOSED") ElectricViolet else NeonTeal,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Date & Time display
                            val dateClean = try {
                                if (app.scheduledAt.contains("T")) {
                                    val d = app.scheduledAt.substringBefore("T")
                                    val t = app.scheduledAt.substringAfter("T").replace("Z", "").take(5)
                                    "$d à $t"
                                } else app.scheduledAt
                            } catch (e: Exception) { app.scheduledAt }

                            Text(
                                text = "📅 Prévu le : $dateClean",
                                color = Color(0xFF93C5FD),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Caller / Contact Name & Number
                            if (!app.contactName.isNullOrBlank() || !app.phoneNumber.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "👤 Interlocuteur : ${app.contactName ?: "Contact"} ${if (!app.phoneNumber.isNullOrBlank()) "(${app.phoneNumber})" else ""}".trim(),
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Call Reference ID
                            if (!app.callId.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "🆔 Réf. Appel : ${app.callId.take(16)}...",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Bottom Actions: Quick Call & Delete
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!app.phoneNumber.isNullOrBlank()) {
                                    Button(
                                        onClick = {
                                            try {
                                                val dialIntent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${app.phoneNumber}"))
                                                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(dialIntent)
                                            } catch (e: Exception) {}
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x3300F2FE)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text("📞 Rappeler (${app.phoneNumber})", color = NeonTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(1.dp))
                                }

                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            localDatabase.deleteAgendaAppointment(app.id)
                                            appointments = localDatabase.getAgendaAppointments()
                                        }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Supprimer",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

