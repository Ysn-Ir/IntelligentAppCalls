package com.example.appcall.presentation.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.presentation.theme.DarkIndigo
import com.example.appcall.presentation.theme.ElectricViolet
import com.example.appcall.presentation.theme.NeonTeal

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

    // Load summary once when callId changes
    LaunchedEffect(callId) {
        viewModel.loadSummary(callId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DarkIndigo, Color(0xFF0A0F24))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Call Summary",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Review and validate call outcomes",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                is SummaryScreenState.Loading -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = NeonTeal)
                    }
                }
                is SummaryScreenState.Error -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = state.message, color = Color.Red, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.loadSummary(callId) },
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                            ) {
                                Text("Retry", color = Color.White)
                            }
                        }
                    }
                }
                is SummaryScreenState.Success -> {
                    val summary = state.summary

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // Low Confidence Banner
                        if (isLowConfidence) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0x33F59E0B) // translucent orange/yellow
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Warning",
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "Low Audio Quality Warning",
                                            color = Color(0xFFF59E0B),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "The AI transcription confidence was lower than 60%. Please verify accuracy and edit where necessary.",
                                            color = Color.LightGray,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Summary Info / Status Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Status Badge
                            val statusColor = when (summary.status) {
                                "VALIDATED" -> NeonTeal
                                "MODIFIED" -> ElectricViolet
                                else -> Color.Gray
                            }
                            Card(
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.2f))
                            ) {
                                Text(
                                    text = summary.status,
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            // Confidence Score Badge
                            summary.confidenceScore?.let { score ->
                                Text(
                                    text = "Confidence: ${String.format("%.1f", score)}%",
                                    color = if (score < 60.0) Color(0xFFF59E0B) else NeonTeal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Main Summary Text Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0x1F293754)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxSize()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "TRANSCRIPTION SUMMARY",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeonTeal
                                    )
                                    if (!isEditing) {
                                        IconButton(onClick = { viewModel.toggleEdit() }) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                if (isEditing) {
                                    OutlinedTextField(
                                        value = summaryText,
                                        onValueChange = { viewModel.updateSummaryText(it) },
                                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 15.sp),
                                        modifier = Modifier.fillMaxSize(),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonTeal,
                                            unfocusedBorderColor = Color.Gray,
                                            focusedContainerColor = Color(0x0AFFFFFF),
                                            unfocusedContainerColor = Color.Transparent
                                        )
                                    )
                                } else {
                                    Text(
                                        text = summaryText,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp
                                    )
                                }
                            }
                        }

                        // Voice Command dialog state
                        var showVoiceDialog by remember { mutableStateOf(false) }
                        var voiceCommandText by remember { mutableStateOf("") }

                        if (showVoiceDialog) {
                            AlertDialog(
                                onDismissRequest = { showVoiceDialog = false },
                                title = { Text("Voice Correction Command", color = Color.White) },
                                text = {
                                    Column {
                                        Text("Simulate a voice edit command transcript:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                                        OutlinedTextField(
                                            value = voiceCommandText,
                                            onValueChange = { voiceCommandText = it },
                                            placeholder = { Text("e.g. Décale à jeudi 15h") },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedContainerColor = Color(0x1F293754)
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
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonTeal)
                                    ) {
                                        Text("Send Command", color = Color.Black)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showVoiceDialog = false }) {
                                        Text("Cancel", color = Color.Gray)
                                    }
                                },
                                containerColor = Color(0xFF111B21)
                            )
                        }

                        // Proposed Appointment Card
                        summary.appointment?.let { appointment ->
                            if (appointment.status != "DISMISSED") {
                                Spacer(modifier = Modifier.height(16.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0x3300F2FE) // translucent NeonTeal theme
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "PROPOSED APPOINTMENT",
                                                color = NeonTeal,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Card(
                                                shape = RoundedCornerShape(6.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = Color(0x1AFFFFFF)
                                                )
                                            ) {
                                                Text(
                                                    text = appointment.status,
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(
                                            text = appointment.title ?: "Réunion sans titre",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Stubbed date/time representation as per UI contract §4.3
                                        Text(
                                            text = "Date: ${appointment.scheduledAt.substringBefore("T")}  •  Time: ${appointment.scheduledAt.substringAfter("T").substringBefore("Z").substring(0, 5)}",
                                            color = Color.LightGray,
                                            fontSize = 13.sp
                                        )

                                        if (appointment.status == "PROPOSED" || appointment.status == "MODIFIED") {
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                OutlinedButton(
                                                    onClick = { viewModel.dismissAppointment() },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.outlinedButtonColors(
                                                        contentColor = Color.Red
                                                    )
                                                ) {
                                                    Text("Dismiss")
                                                }

                                                Button(
                                                    onClick = { showVoiceDialog = true },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = ElectricViolet
                                                    )
                                                ) {
                                                    Text("Voice Edit", color = Color.White)
                                                }

                                                Button(
                                                    onClick = { viewModel.validateAppointment() },
                                                    modifier = Modifier.weight(1f),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = NeonTeal
                                                    )
                                                ) {
                                                    Text("Confirm", color = Color.Black, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls Bottom Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (isEditing) {
                                OutlinedButton(
                                    onClick = { viewModel.toggleEdit() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color.LightGray
                                    ),
                                    // Disable cancel option if in low confidence mode to push for validation
                                    enabled = !isLowConfidence
                                ) {
                                    Text("Cancel")
                                }

                                Button(
                                    onClick = { viewModel.saveSummary() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricViolet
                                    )
                                ) {
                                    Text("Save Changes", color = Color.White)
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.validateSummary() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NeonTeal
                                    ),
                                    enabled = summary.status != "VALIDATED"
                                ) {
                                    Text(
                                        text = if (summary.status == "VALIDATED") "Validated ✓" else "Validate Summary",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Development simulator trigger for low confidence testing
                        if (!isLowConfidence && summary.status != "VALIDATED") {
                            TextButton(
                                onClick = { viewModel.triggerMockLowConfidence() },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Simulate Low Confidence (<60%)", color = Color(0xFFF59E0B), fontSize = 11.sp)
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
