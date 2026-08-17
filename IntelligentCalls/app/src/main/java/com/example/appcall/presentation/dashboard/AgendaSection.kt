package com.example.appcall.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    val coroutineScope = rememberCoroutineScope()
    var appointments by remember { mutableStateOf(localDatabase.getAgendaAppointments()) }
    var newTitle by remember { mutableStateOf("") }

    val todayFormatted = remember {
        SimpleDateFormat("EEEE d MMMM yyyy", Locale.FRENCH).format(Date()).replaceFirstChar { it.uppercase() }
    }

    // Dynamic date and time selectors
    var selectedDayOffset by remember { mutableStateOf(1) } // 0 = Today, 1 = Tomorrow, 2 = +2 days, etc.
    var selectedTime by remember { mutableStateOf("14:00") }

    fun calculateScheduledDate(offsetDays: Int, timeStr: String): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, offsetDays)
        val dayPart = SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH).format(cal.time)
        return "$dayPart à $timeStr"
    }

    var dynamicScheduledAt by remember {
        mutableStateOf(calculateScheduledDate(selectedDayOffset, selectedTime))
    }

    LaunchedEffect(selectedDayOffset, selectedTime) {
        dynamicScheduledAt = calculateScheduledDate(selectedDayOffset, selectedTime)
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
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Mon Agenda",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = todayFormatted,
                    color = NeonTeal,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Button(
                onClick = {
                    coroutineScope.launch {
                        voipRepository.fetchAgenda().onSuccess {
                            appointments = localDatabase.getAgendaAppointments()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x1F293754)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("🔄 Actualiser", color = NeonTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // ── APPOINTMENT CREATION CARD ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Nouveau rendez-vous / réunion", color = NeonTeal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Titre (ex: Point projet avec Marc)", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonTeal,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Day Selection Chips
                Text("JOUR :", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        0 to "Aujourd'hui",
                        1 to "Demain",
                        2 to "Après-demain",
                        7 to "Dans 1 sem."
                    ).forEach { (offset, label) ->
                        FilterChip(
                            selected = selectedDayOffset == offset,
                            onClick = { selectedDayOffset = offset },
                            label = { Text(label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonTeal,
                                selectedLabelColor = Color(0xFF0F172A),
                                containerColor = Color(0xFF0F172A),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Time Selection Chips
                Text("HEURE :", color = Color.LightGray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("09:00", "11:00", "14:00", "16:30", "18:00").forEach { time ->
                        FilterChip(
                            selected = selectedTime == time,
                            onClick = { selectedTime = time },
                            label = { Text(time, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricViolet,
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF0F172A),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📅 $dynamicScheduledAt",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Button(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                val title = newTitle.trim()
                                val time = dynamicScheduledAt
                                val id = "app-${System.currentTimeMillis()}"
                                newTitle = ""
                                coroutineScope.launch {
                                    voipRepository.createAgenda(id, title, time)
                                    appointments = localDatabase.getAgendaAppointments()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Ajouter", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── APPOINTMENTS LIST ──
        Text(
            text = "RENDEZ-VOUS PROGRAMMÉS (${appointments.size})",
            color = NeonTeal,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        if (appointments.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun rendez-vous planifié.\nLes rendez-vous détectés lors de vos appels apparaîtront ici automatiquement.",
                    color = Color.Gray,
                    fontSize = 13.sp,
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
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = NeonTeal,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "📅 ${app.scheduledAt}",
                                    color = Color(0xFF93C5FD),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        localDatabase.deleteAgendaAppointment(app.id)
                                        appointments = localDatabase.getAgendaAppointments()
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Supprimer",
                                    tint = Color(0xFF64748B),
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
