package com.example.appcall.presentation.dashboard

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.data.local.AppLocalDatabase
import com.example.appcall.domain.repository.VoipRepository
import com.example.appcall.presentation.theme.*
import kotlinx.coroutines.delay
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

    // Date & Time picker state
    var selectedDayOffset by remember { mutableIntStateOf(0) }
    var customSelectedDateStr by remember { mutableStateOf<String?>(null) }
    var selectedTime by remember { mutableStateOf("14:00") }

    val dynamicScheduledAt by remember {
        derivedStateOf {
            if (customSelectedDateStr != null) {
                "${customSelectedDateStr}T$selectedTime:00"
            } else {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, selectedDayOffset)
                val datePart = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                "${datePart}T$selectedTime:00"
            }
        }
    }

    fun openCalendarPicker() {
        val cal = Calendar.getInstance()
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val pickedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                customSelectedDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(pickedCal.time)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun openClockPicker() {
        val cal = Calendar.getInstance()
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                selectedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    // Live dynamic clock (e.g. 14:28:05)
    var currentTimeString by remember { mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())) }
    var currentDateString by remember { mutableStateOf(SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(Date()).replaceFirstChar { it.uppercase() }) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            currentDateString = SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).format(Date()).replaceFirstChar { it.uppercase() }
            delay(1000)
        }
    }

    var selectedDayIndex by remember { mutableIntStateOf(1) } // 0=Lun, 1=Mar, etc.

    val calendarDays = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val dayNames = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
        dayNames.mapIndexed { idx, name ->
            val dayNum = cal.get(Calendar.DAY_OF_MONTH)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            Pair(name, dayNum)
        }
    }

    LaunchedEffect(Unit) {
        voipRepository.fetchAgenda().onSuccess {
            appointments = localDatabase.getAgendaAppointments()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // Top Fade & Digital Clock Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgColor)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = currentTimeString,
                        color = Text1,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = currentDateString,
                        color = Text3,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isAddingAgenda) DangerDim else Surface1)
                        .border(1.dp, if (isAddingAgenda) DangerColor else BorderColor, RoundedCornerShape(8.dp))
                        .clickable { isAddingAgenda = !isAddingAgenda }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isAddingAgenda) "✕ Fermer" else "＋ Nouveau RDV",
                        color = if (isAddingAgenda) DangerColor else Text1,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Day Strip Picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                calendarDays.forEachIndexed { index, (dayName, dayNum) ->
                    val isActive = selectedDayIndex == index
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isActive) Text1 else Surface1)
                            .border(1.dp, if (isActive) Text1 else BorderColor, RoundedCornerShape(9.dp))
                            .clickable { selectedDayIndex = index }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dayName,
                                color = if (isActive) BgColor.copy(alpha = 0.6f) else Text3,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$dayNum",
                                color = if (isActive) BgColor else Text1,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // ── COLLAPSIBLE APPOINTMENT CREATION DRAWER ──
        if (isAddingAgenda) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface1)
                    .border(1.dp, BorderStrong, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        text = "PLANIFIER UN RENDEZ-VOUS",
                        color = Text3,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTitle,
                            onValueChange = { newTitle = it },
                            placeholder = { Text("Titre du RDV...", color = Text3, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BorderStrong,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Text1,
                                unfocusedTextColor = Text1
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
                                        localDatabase.saveAgendaAppointment(
                                            id = id,
                                            title = title,
                                            scheduledAt = time,
                                            status = "CONFIRMED",
                                            contactName = "Manuel",
                                            phoneNumber = ""
                                        )
                                        appointments = localDatabase.getAgendaAppointments()
                                        voipRepository.createAgenda(id, title, time)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Text1),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Ajouter", color = BgColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Day Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("JOUR :", color = Text3, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        listOf(
                            0 to "Aujourd'hui",
                            1 to "Demain",
                            2 to "Après-demain",
                            7 to "+1 sem"
                        ).forEach { (offset, label) ->
                            val isSel = customSelectedDateStr == null && selectedDayOffset == offset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) Text1 else Surface2)
                                    .clickable {
                                        customSelectedDateStr = null
                                        selectedDayOffset = offset
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) BgColor else Text2,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (customSelectedDateStr != null) Text1 else Surface2)
                                .clickable { openCalendarPicker() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = customSelectedDateStr?.let { "📅 $it" } ?: "📅 Date...",
                                color = if (customSelectedDateStr != null) BgColor else Text2,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Time Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("HEURE :", color = Text3, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        listOf("09:00", "11:00", "14:00", "16:30", "18:00").forEach { time ->
                            val isSel = selectedTime == time
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) Text1 else Surface2)
                                    .clickable { selectedTime = time }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = time,
                                    color = if (isSel) BgColor else Text2,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Surface2)
                                .clickable { openClockPicker() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "⏰ $selectedTime (Modifier)",
                                color = Text2,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "📅 RDV prévu : $dynamicScheduledAt",
                        color = AccentText,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Agenda List
        if (appointments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun rendez-vous dans l'agenda",
                    color = Text3,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(appointments) { appt ->
                    val isConfirmed = appt.status?.contains("CONFIRM", ignoreCase = true) == true || appt.status?.contains("VALID", ignoreCase = true) == true
                    val statusText = if (isConfirmed) "Confirmé" else "Proposé"
                    val (tagBg, tagColor) = if (isConfirmed) Pair(SuccessDim, SuccessColor) else Pair(WarnDim, WarnColor)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Surface1)
                            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = appt.title.takeIf { it.isNotBlank() } ?: "Rendez-vous de suivi",
                                    color = Text1,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(tagBg)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = tagColor,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Delete appointment button
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .background(Surface2)
                                            .clickable {
                                                coroutineScope.launch {
                                                    localDatabase.deleteAgendaAppointment(appt.id)
                                                    appointments = localDatabase.getAgendaAppointments()
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("🗑", fontSize = 10.sp)
                                    }
                                }
                            }

                            val callerInfo = "${appt.contactName ?: "Contact"} · ${if (!appt.phoneNumber.isNullOrBlank()) appt.phoneNumber else appt.scheduledAt}"
                            Text(
                                text = callerInfo,
                                color = Text3,
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = BorderColor, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(11.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${appt.callId ?: appt.id.take(12)}",
                                    color = Text3,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (!isConfirmed) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(SuccessDim)
                                                .border(1.dp, SuccessColor, RoundedCornerShape(7.dp))
                                                .clickable {
                                                    coroutineScope.launch {
                                                        localDatabase.saveAgendaAppointment(
                                                            id = appt.id,
                                                            title = appt.title,
                                                            scheduledAt = appt.scheduledAt,
                                                            status = "CONFIRMED",
                                                            contactName = appt.contactName,
                                                            phoneNumber = appt.phoneNumber
                                                        )
                                                        appointments = localDatabase.getAgendaAppointments()
                                                        appt.callId?.let { cId ->
                                                            voipRepository.validateAppointment(cId)
                                                        }
                                                    }
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "Valider",
                                                color = SuccessColor,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    if (!appt.phoneNumber.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(Surface2)
                                                .border(1.dp, BorderStrong, RoundedCornerShape(7.dp))
                                                .clickable {
                                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                                        data = android.net.Uri.parse("tel:${appt.phoneNumber}")
                                                    }
                                                    context.startActivity(intent)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = "Rappeler",
                                                color = Text1,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}


