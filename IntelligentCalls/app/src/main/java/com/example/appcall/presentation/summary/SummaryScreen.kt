package com.example.appcall.presentation.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    callId: String,
    viewModel: SummaryViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val summaryText by viewModel.summaryText.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val isLowConfidence by viewModel.isLowConfidence.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()
    val transcript by viewModel.transcript.collectAsState()

    var isPlaying by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableStateOf("1×") }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var voiceCommandText by remember { mutableStateOf("") }

    // Load summary once when callId changes
    LaunchedEffect(callId) {
        viewModel.loadSummary(callId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // Back Row Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(7.dp))
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "←", color = Text2, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Appels",
                    color = Text2,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(7.dp))
                        .clickable { viewModel.refreshCurrent() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🔄 Actualiser", color = Text2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            when (val state = uiState) {
                is SummaryScreenState.Idle -> {
                    // Initial idle state
                }
                is SummaryScreenState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AccentColor, modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = "Chargement de l'analyse...", color = Text3, fontSize = 13.sp)
                        }
                    }
                }
                is SummaryScreenState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = state.message, color = DangerColor, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadSummary(callId) },
                                colors = ButtonDefaults.buttonColors(containerColor = Surface2)
                            ) {
                                Text("Réessayer", color = Text1)
                            }
                        }
                    }
                }
                is SummaryScreenState.Success -> {
                    val summary = state.summary

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        // 1. Caller Card
                        item {
                            val contactName = summary.appointment?.contactName ?: "Contact Appel"
                            val phoneNumber = summary.appointment?.phoneNumber ?: "+33 6 12 34 56 78"
                            val initials = contactName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface1)
                                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                    .padding(15.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(AvatarBgA),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (initials.isNotBlank()) initials else "📞",
                                            color = Text1,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contactName,
                                            color = Text1,
                                            fontSize = 15.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$phoneNumber · Enregistré",
                                            color = Text3,
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        Row(
                                            modifier = Modifier.padding(top = 9.dp),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(Surface2)
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(text = "Audio HD", color = Text2, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(5.dp))
                                                    .background(SuccessDim)
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(text = "Sentiment positif", color = SuccessColor, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Surface2)
                                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = "📞", fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        // 2. Audio Player Card
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface1)
                                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                    .padding(15.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Text1)
                                                .clickable { isPlaying = !isPlaying },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = if (isPlaying) "❚❚" else "▶", color = BgColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Scrub wave
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(24.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                                        ) {
                                            val heights = listOf(7, 12, 5, 15, 9, 13, 6, 10, 8, 13, 5, 9, 7, 11)
                                            heights.forEachIndexed { i, h ->
                                                val barColor = if (i < 7) Text1 else Surface2
                                                Box(
                                                    modifier = Modifier
                                                        .width(2.dp)
                                                        .height(h.dp)
                                                        .clip(RoundedCornerShape(1.dp))
                                                        .background(barColor)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(text = "1:24", color = Text3, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        Text(text = "3:12", color = Text3, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                            val speeds = listOf("1×", "1.5×", "2×")
                                            speeds.forEach { speed ->
                                                val isSelected = selectedSpeed == speed
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (isSelected) AccentDim else Surface2)
                                                        .border(1.dp, if (isSelected) AccentColor.copy(alpha = 0.3f) else BorderColor, RoundedCornerShape(6.dp))
                                                        .clickable { selectedSpeed = speed }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = speed,
                                                        color = if (isSelected) AccentText else Text3,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }

                                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                            Box(
                                                modifier = Modifier
                                                    .size(27.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(Surface2)
                                                    .border(1.dp, BorderColor, RoundedCornerShape(7.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "⬇", color = Text2, fontSize = 11.sp)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(27.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(Surface2)
                                                    .border(1.dp, BorderColor, RoundedCornerShape(7.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "⤴", color = Text2, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 3. AI Summary Card
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface1)
                                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                    .padding(15.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "RÉSUMÉ INTELLIGENT",
                                            color = Text2,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.5.sp
                                        )
                                        IconButton(
                                            onClick = { viewModel.toggleEdit() },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = Text3,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(11.dp))

                                    if (isEditing) {
                                        OutlinedTextField(
                                            value = summaryText,
                                            onValueChange = { viewModel.updateSummaryText(it) },
                                            textStyle = androidx.compose.ui.text.TextStyle(color = Text1, fontSize = 13.sp),
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = AccentColor,
                                                unfocusedBorderColor = BorderColor,
                                                focusedContainerColor = Surface2,
                                                unfocusedContainerColor = Surface2
                                            )
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { viewModel.saveSummary() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Text1),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Enregistrer les modifications", color = BgColor, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        val lines = summaryText.split("\n").filter { it.isNotBlank() }
                                        if (lines.isNotEmpty()) {
                                            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                                lines.forEach { line ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                                                    ) {
                                                        Text(text = "—", color = Text3, fontSize = 13.sp)
                                                        Text(
                                                            text = line.removePrefix("- ").removePrefix("* ").trim(),
                                                            color = Text1,
                                                            fontSize = 13.sp,
                                                            lineHeight = 18.sp
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Text(text = "Résumé en cours de traitement...", color = Text3, fontSize = 13.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 4. Detected Appointment Card
                        summary.appointment?.let { appointment ->
                            if (appointment.status != "DISMISSED") {
                                item {
                                    val isConfirmed = appointment.status == "CONFIRMED" || appointment.status == "VALIDATED"
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Surface1)
                                            .border(1.dp, BorderStrong, RoundedCornerShape(10.dp))
                                            .padding(15.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "RENDEZ-VOUS DÉTECTÉ",
                                                    color = Text2,
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 0.5.sp
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(if (isConfirmed) SuccessDim else WarnDim)
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = if (isConfirmed) "Confirmé" else "Proposé",
                                                        color = if (isConfirmed) SuccessColor else WarnColor,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // 2x2 Grid
                                            val dateStr = appointment.scheduledAt.substringBefore("T")
                                            val timeStr = appointment.scheduledAt.substringAfter("T", "14:00").take(5)

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                            ) {
                                                Row(modifier = Modifier.fillMaxWidth()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .background(Surface2)
                                                            .padding(9.dp, 11.dp)
                                                    ) {
                                                        Column {
                                                            Text(text = "DATE", color = Text3, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                                            Text(text = dateStr, color = Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                                                        }
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .background(Surface1)
                                                            .padding(9.dp, 11.dp)
                                                    ) {
                                                        Column {
                                                            Text(text = "HEURE", color = Text3, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                                            Text(text = timeStr, color = Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                                                        }
                                                    }
                                                }
                                                Row(modifier = Modifier.fillMaxWidth()) {
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .background(Surface1)
                                                            .padding(9.dp, 11.dp)
                                                    ) {
                                                        Column {
                                                            Text(text = "TITRE", color = Text3, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                                            Text(text = appointment.title ?: "Suivi contrat", color = Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                                                        }
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .background(Surface2)
                                                            .padding(9.dp, 11.dp)
                                                    ) {
                                                        Column {
                                                            Text(text = "INTERLOCUTEUR", color = Text3, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                                            Text(text = appointment.contactName ?: "Contact", color = Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(13.dp))

                                            // Appt Actions
                                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(Text1)
                                                        .clickable { viewModel.validateAppointment() }
                                                        .padding(vertical = 11.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = if (isConfirmed) "Rendez-vous synchronisé ✓" else "Confirmer et synchroniser l'agenda",
                                                        color = BgColor,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.5.sp
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .border(1.dp, BorderStrong, RoundedCornerShape(8.dp))
                                                        .clickable { showVoiceDialog = true }
                                                        .padding(vertical = 10.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(text = "Modifier", color = Text2, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewModel.dismissAppointment() }
                                                        .padding(vertical = 6.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(text = "Ignorer", color = Text3, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 5. Transcript Block
                        item {
                            Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text(
                                    text = "TRANSCRIPTION",
                                    color = Text2,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )

                                val segments = transcript?.speakerSegments
                                if (!segments.isNullOrEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                        segments.forEach { seg ->
                                            val isAgent = seg.speaker == "agent"
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = if (isAgent) Arrangement.Start else Arrangement.End
                                            ) {
                                                Column(modifier = Modifier.widthIn(max = 280.dp)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .clip(
                                                                RoundedCornerShape(
                                                                    topStart = 11.dp,
                                                                    topEnd = 11.dp,
                                                                    bottomStart = if (isAgent) 3.dp else 11.dp,
                                                                    bottomEnd = if (isAgent) 11.dp else 3.dp
                                                                )
                                                            )
                                                            .background(if (isAgent) Surface1 else Surface2)
                                                            .border(1.dp, BorderColor, RoundedCornerShape(11.dp))
                                                            .padding(horizontal = 12.dp, vertical = 9.dp)
                                                    ) {
                                                        Text(
                                                            text = seg.text,
                                                            color = Text1,
                                                            fontSize = 12.5.sp,
                                                            lineHeight = 17.sp
                                                        )
                                                    }
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(top = 4.dp, start = 4.dp, end = 4.dp),
                                                        horizontalArrangement = if (isAgent) Arrangement.Start else Arrangement.End,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = if (isAgent) "Agent · ${String.format("%.1f", seg.start)}s · 98.5%" else "97.2% · ${String.format("%.1f", seg.start)}s · Contact",
                                                            color = Text3,
                                                            fontSize = 9.5.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    val raw = transcript?.rawText
                                    if (!raw.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Surface1)
                                                .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                                .padding(14.dp)
                                        ) {
                                            Text(text = raw, color = Text1, fontSize = 13.sp, lineHeight = 19.sp)
                                        }
                                    } else {
                                        Text(text = "Transcription en cours par l'IA...", color = Text3, fontSize = 12.5.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Voice Command Modal
        if (showVoiceDialog) {
            AlertDialog(
                onDismissRequest = { showVoiceDialog = false },
                containerColor = Surface1,
                title = { Text("Modifier le rendez-vous", color = Text1, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Indiquez la modification (ex: 'Décale à jeudi 15h')", color = Text3, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedTextField(
                            value = voiceCommandText,
                            onValueChange = { voiceCommandText = it },
                            placeholder = { Text("e.g. Décale à jeudi 15h", color = Text3) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Text1),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentColor,
                                unfocusedBorderColor = BorderColor,
                                focusedContainerColor = Surface2,
                                unfocusedContainerColor = Surface2
                            )
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.editAppointmentVoice(voiceCommandText)
                            showVoiceDialog = false
                            voiceCommandText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Text1)
                    ) {
                        Text("Valider", color = BgColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVoiceDialog = false }) {
                        Text("Annuler", color = Text3)
                    }
                }
            )
        }
    }
}


