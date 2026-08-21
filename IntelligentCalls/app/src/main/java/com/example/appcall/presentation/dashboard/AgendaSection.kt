package com.example.appcall.presentation.dashboard

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.data.local.AppLocalDatabase
import com.example.appcall.data.local.LocalAgendaItem
import com.example.appcall.data.notification.AppNotificationManager
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
    val netPrefs = remember { context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE) }
    val appLanguageCode = remember { netPrefs.getString("app_language", "en") ?: "en" }
    val strings = com.example.appcall.presentation.theme.getAppStrings(appLanguageCode)
    val appLocale = com.example.appcall.presentation.theme.getAppLocale(appLanguageCode)

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
        DatePickerDialog(
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
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                selectedTime = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).show()
    }

    // Live dynamic clock
    var currentTimeString by remember { mutableStateOf(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())) }
    var currentDateString by remember { mutableStateOf(SimpleDateFormat("EEEE d MMMM", appLocale).format(Date()).replaceFirstChar { it.uppercase() }) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeString = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            currentDateString = SimpleDateFormat("EEEE d MMMM", appLocale).format(Date()).replaceFirstChar { it.uppercase() }
            delay(1000)
        }
    }

    // Dynamic Calendar Days for Current Week (Mon to Sun)
    data class CalendarDayItem(
        val dayName: String,
        val dayNum: Int,
        val isoDate: String,
        val isToday: Boolean
    )

    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    var selectedDayIso by remember { mutableStateOf<String?>(null) } // null = All days

    val calendarDays = remember {
        val cal = Calendar.getInstance()
        cal.firstDayOfWeek = Calendar.MONDAY
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val dayNames = listOf("LUN", "MAR", "MER", "JEU", "VEN", "SAM", "DIM")
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dayNames.map { name ->
            val dayNum = cal.get(Calendar.DAY_OF_MONTH)
            val iso = isoFormat.format(cal.time)
            val isToday = (iso == todayIso)
            cal.add(Calendar.DAY_OF_MONTH, 1)
            CalendarDayItem(name, dayNum, iso, isToday)
        }
    }

    val filteredAppointments = remember(appointments, selectedDayIso) {
        if (selectedDayIso == null) appointments
        else appointments.filter { it.scheduledAt.startsWith(selectedDayIso!!) || it.scheduledAt.contains(selectedDayIso!!) }
    }

    LaunchedEffect(Unit) {
        voipRepository.fetchAgenda().onSuccess {
            appointments = localDatabase.getAgendaAppointments()
        }
    }

    fun formatAgendaDateTime(isoStr: String): Pair<String, String> {
        return try {
            val clean = isoStr.replace("T", " ").trim()
            val inFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val parsedDate = inFormat.parse(clean.take(16))
            if (parsedDate != null) {
                val dateFormat = SimpleDateFormat("EEE d MMM yyyy", appLocale)
                val timeFormat = SimpleDateFormat("HH:mm", appLocale)
                Pair(dateFormat.format(parsedDate).replaceFirstChar { it.uppercase() }, timeFormat.format(parsedDate))
            } else {
                Pair(isoStr.take(10), isoStr.drop(11).take(5))
            }
        } catch (e: Exception) {
            Pair(isoStr.take(10), isoStr.drop(11).take(5))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // Top Header & Digital Clock
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgColor)
                .padding(horizontal = 16.dp, vertical = 12.dp)
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
                        fontSize = 22.sp,
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

                Button(
                    onClick = { isAddingAgenda = !isAddingAgenda },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAddingAgenda) DangerDim else ElectricViolet
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (isAddingAgenda) "✕ ${strings.close}" else "＋ ${strings.addAppointment}",
                        color = if (isAddingAgenda) DangerColor else Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Day Strip Picker
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "TOUS" Filter Chip
                val isAllSelected = selectedDayIso == null
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isAllSelected) NeonTeal else Surface1)
                        .border(1.dp, if (isAllSelected) NeonTeal else BorderColor, RoundedCornerShape(10.dp))
                        .clickable { selectedDayIso = null }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.allTasksFilter.uppercase(),
                        color = if (isAllSelected) Color.Black else Text2,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                calendarDays.forEach { day ->
                    val isActive = selectedDayIso == day.isoDate
                    val count = appointments.count { it.scheduledAt.startsWith(day.isoDate) || it.scheduledAt.contains(day.isoDate) }

                    Box(
                        modifier = Modifier
                            .width(46.dp)
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isActive) ElectricViolet else Surface1)
                            .border(1.dp, if (isActive) ElectricViolet else if (day.isToday) NeonTeal else BorderColor, RoundedCornerShape(10.dp))
                            .clickable {
                                selectedDayIso = if (selectedDayIso == day.isoDate) null else day.isoDate
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = day.dayName,
                                color = if (isActive) Color.White.copy(alpha = 0.8f) else Text3,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${day.dayNum}",
                                color = if (isActive) Color.White else if (day.isToday) NeonTeal else Text1,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 1.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) Color.White else NeonTeal)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── COLLAPSIBLE APPOINTMENT CREATION DRAWER ──
        AnimatedVisibility(
            visible = isAddingAgenda,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F293754)),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📅 ${strings.planAppointment}",
                        color = NeonTeal,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text(strings.appointmentTitlePlaceholder, color = Text3, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonTeal,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Text1,
                            unfocusedTextColor = Text1
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Day Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.dayLabel, color = Text3, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        listOf(
                            0 to strings.today,
                            1 to strings.tomorrow,
                            2 to strings.afterTomorrow,
                            7 to strings.oneWeek
                        ).forEach { (offset, label) ->
                            val isSel = customSelectedDateStr == null && selectedDayOffset == offset
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) NeonTeal else Surface2)
                                    .clickable {
                                        customSelectedDateStr = null
                                        selectedDayOffset = offset
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.Black else Text2,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (customSelectedDateStr != null) NeonTeal else Surface2)
                                .clickable { openCalendarPicker() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = customSelectedDateStr?.let { "📅 $it" } ?: strings.calendarPicker,
                                color = if (customSelectedDateStr != null) Color.Black else Text2,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Time Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(strings.hourLabel, color = Text3, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                        listOf("09:00", "11:00", "14:00", "16:30", "18:00").forEach { time ->
                            val isSel = selectedTime == time
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) ElectricViolet else Surface2)
                                    .clickable { selectedTime = time }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = time,
                                    color = if (isSel) Color.White else Text2,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Surface2)
                                .clickable { openClockPicker() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "⏰ $selectedTime",
                                color = Text2,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "RDV: $dynamicScheduledAt",
                            color = NeonTeal,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )

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
                                        AppNotificationManager.showAppointmentNotification(
                                            context = context,
                                            appointmentId = id,
                                            title = title,
                                            scheduledAt = time,
                                            contactName = "Manuel"
                                        )
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(strings.validate, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Agenda List
        if (filteredAppointments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                val emptyMsg = if (selectedDayIso != null) "Aucun rendez-vous pour le $selectedDayIso" else "Aucun rendez-vous planifié dans l'agenda"
                Text(
                    text = emptyMsg,
                    color = Text3,
                    fontSize = 13.5.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredAppointments) { appt ->
                    val isConfirmed = appt.status?.contains("CONFIRM", ignoreCase = true) == true || appt.status?.contains("VALID", ignoreCase = true) == true
                    val statusText = if (isConfirmed) "✓ Confirmé" else "⏳ Détecté (IA)"
                    val (tagBg, tagColor) = if (isConfirmed) Pair(SuccessDim, SuccessColor) else Pair(WarnDim, WarnColor)
                    val (dateFormatted, timeFormatted) = formatAgendaDateTime(appt.scheduledAt)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0x1F293754)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isConfirmed) BorderColor else WarnDim)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Top Row: Date Pill & Status Badge
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Surface2)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "📅 $dateFormatted",
                                            color = Text1,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Surface2)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "⏰ $timeFormatted",
                                            color = NeonTeal,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(tagBg)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = statusText,
                                            color = tagColor,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Delete appointment button
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                localDatabase.deleteAgendaAppointment(appt.id)
                                                appointments = localDatabase.getAgendaAppointments()
                                            }
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Appointment",
                                            tint = Text3,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Meeting Title
                            Text(
                                text = appt.title.takeIf { it.isNotBlank() } ?: "Rendez-vous de suivi",
                                color = Text1,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Contact / Origin Information Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Contact Avatar Circle
                                val initials = (appt.contactName?.take(2) ?: "CO").uppercase()
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(ElectricViolet.copy(alpha = 0.3f))
                                        .border(1.dp, ElectricViolet, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = appt.contactName ?: "Contact",
                                    color = Text1,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )

                                if (!appt.phoneNumber.isNullOrBlank()) {
                                    Text(
                                        text = appt.phoneNumber,
                                        color = Text3,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = BorderColor.copy(alpha = 0.5f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            // Bottom Responsive Action Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // If unconfirmed, offer direct Validation
                                if (!isConfirmed) {
                                    Button(
                                        onClick = {
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
                                                Toast.makeText(context, "✅ Rendez-vous validé !", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessDim),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("✓ Valider", color = SuccessColor, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Sync with Native Android Calendar
                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val calIntent = Intent(Intent.ACTION_INSERT).apply {
                                                data = CalendarContract.Events.CONTENT_URI
                                                putExtra(CalendarContract.Events.TITLE, appt.title.takeIf { it.isNotBlank() } ?: "Rendez-vous")
                                                putExtra(CalendarContract.Events.DESCRIPTION, "Appel avec ${appt.contactName ?: "Contact"} · ${appt.phoneNumber ?: ""}")
                                                try {
                                                    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
                                                    val parsed = format.parse(appt.scheduledAt.take(16))
                                                    if (parsed != null) {
                                                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, parsed.time)
                                                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, parsed.time + 3600000)
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                            context.startActivity(calIntent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Calendrier: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderStrong),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1.2f)
                                ) {
                                    Text("📅 Calendrier", color = Text1, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                }

                                // Call back contact if number exists
                                if (!appt.phoneNumber.isNullOrBlank()) {
                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                                data = Uri.parse("tel:${appt.phoneNumber}")
                                            }
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("📞 Appeler", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
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
