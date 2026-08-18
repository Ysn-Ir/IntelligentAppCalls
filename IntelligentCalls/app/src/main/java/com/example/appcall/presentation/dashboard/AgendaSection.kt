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
                verticalAlignment = Alignment.Bottom
            ) {
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
                            }

                            val callerInfo = "${appt.contactName ?: "Contact"} · ${appt.phoneNumber ?: appt.scheduledAt}"
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

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Surface2)
                                        .border(1.dp, BorderStrong, RoundedCornerShape(7.dp))
                                        .clickable {
                                            if (!appt.phoneNumber.isNullOrBlank()) {
                                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                                    data = android.net.Uri.parse("tel:${appt.phoneNumber}")
                                                }
                                                context.startActivity(intent)
                                            }
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

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}


