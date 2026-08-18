package com.example.appcall.presentation.calling

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.data.calling.CallState
import com.example.appcall.domain.model.Contact
import com.example.appcall.presentation.theme.*

@Composable
fun CallScreen(
    viewModel: CallViewModel,
    onLogout: () -> Unit,
    onNavigateToSummary: (String) -> Unit
) {
    val contacts by viewModel.contacts.collectAsState()
    val callState by viewModel.callState.collectAsState()
    val consentGiven by viewModel.consentGiven.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val callHistory by viewModel.callHistory.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Historique Appels, 1 = Contacts
    var contactSearchQuery by remember { mutableStateOf("") }
    var showContactDialer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadCallHistory()
    }

    var lastActiveCallId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(callState) {
        val state = callState
        if (state is CallState.Active) {
            lastActiveCallId = state.callId
        }
    }

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val filteredContacts = remember(contacts, contactSearchQuery) {
        if (contactSearchQuery.isBlank()) contacts
        else contacts.filter {
            it.fullName.contains(contactSearchQuery, ignoreCase = true) ||
            it.phoneNumber.contains(contactSearchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── TOP TAB SWITCHER (Appels vs Contacts) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgColor)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTab == 0) Text1 else Surface1)
                        .border(1.dp, if (selectedTab == 0) Text1 else BorderColor, RoundedCornerShape(8.dp))
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📞 Historique (${callHistory.size})",
                        color = if (selectedTab == 0) BgColor else Text2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedTab == 1) Text1 else Surface1)
                        .border(1.dp, if (selectedTab == 1) Text1 else BorderColor, RoundedCornerShape(8.dp))
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "👥 Contacts (${contacts.size})",
                        color = if (selectedTab == 1) BgColor else Text2,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (selectedTab == 0) {
                // Screen 1: Call History Screen
                CallHistoryScreen(
                    callHistory = callHistory,
                    onCallClick = onNavigateToSummary,
                    onFabClick = { showContactDialer = true }
                )
            } else {
                // Contacts List View
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Consent Toggle Card
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Surface1)
                                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Transcription & Analyse IA",
                                        color = Text1,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (consentGiven) "Actif: Les appels seront transcrits & résumés"
                                               else "Désactivé: Appel audio classique",
                                        color = if (consentGiven) SuccessColor else Text3,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Switch(
                                    checked = consentGiven,
                                    onCheckedChange = { viewModel.setConsentGiven(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = BgColor,
                                        checkedTrackColor = Text1
                                    )
                                )
                            }
                        }
                    }

                    // Search Contacts Box
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface1)
                                .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = contactSearchQuery,
                                onValueChange = { contactSearchQuery = it },
                                textStyle = androidx.compose.ui.text.TextStyle(color = Text1, fontSize = 13.sp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    if (contactSearchQuery.isEmpty()) {
                                        Text("Rechercher un contact...", color = Text3, fontSize = 13.sp)
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    if (filteredContacts.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Aucun contact trouvé", color = Text3, fontSize = 13.sp)
                            }
                        }
                    } else {
                        items(filteredContacts) { contact ->
                            ContactRow(
                                contact = contact,
                                onCallClick = { viewModel.startCall(contact) },
                                onDirectDial = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                                        data = android.net.Uri.parse("tel:${contact.phoneNumber}")
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // Contact Dialer Dialog
        if (showContactDialer) {
            AlertDialog(
                onDismissRequest = { showContactDialer = false },
                containerColor = Surface1,
                title = {
                    Text(
                        text = "Composer un appel",
                        color = Text1,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp)
                    ) {
                        Text(
                            text = "Sélectionnez un contact pour lancer l'appel :",
                            color = Text3,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        if (contacts.isEmpty()) {
                            Text(
                                text = "Aucun contact enregistré",
                                color = Text3,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(contacts) { contact ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Surface2)
                                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                            .clickable {
                                                showContactDialer = false
                                                viewModel.startCall(contact)
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = contact.fullName,
                                                    color = Text1,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 14.sp
                                                )
                                                Text(
                                                    text = contact.phoneNumber,
                                                    color = Text3,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Text(text = "📞", fontSize = 14.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showContactDialer = false }) {
                        Text("Fermer", color = Text2, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }

        // Active Call Overlay Screen
        AnimatedVisibility(
            visible = callState !is CallState.Idle,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            ActiveCallOverlay(
                callState = callState,
                consentGiven = consentGiven,
                transcript = transcript,
                audioManager = audioManager,
                onMuteClick = { viewModel.toggleMute() },
                onSpeakerClick = { viewModel.toggleSpeaker(audioManager) },
                onHangUpClick = { viewModel.hangUp() },
                onDismiss = {
                    viewModel.resetCallState()
                    val callId = lastActiveCallId
                    if (consentGiven && callId != null) {
                        onNavigateToSummary(callId)
                    }
                }
            )
        }
    }
}

@Composable
fun ContactRow(
    contact: Contact,
    onCallClick: () -> Unit,
    onDirectDial: () -> Unit = {}
) {
    val initial = contact.fullName.trim().take(1).uppercase().ifEmpty { "C" }
    val avatarBg = remember(contact.fullName) {
        val colors = listOf(AvatarBgA, AvatarBgB, AvatarBgC, AvatarBgD, AvatarBgE)
        colors[kotlin.math.abs(contact.fullName.hashCode()) % colors.size]
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface1)
            .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    color = Text1,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.fullName,
                    color = Text1,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = contact.phoneNumber,
                    color = Text3,
                    fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Direct SIM call
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Surface2)
                        .clickable { onDirectDial() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("📱", fontSize = 12.sp)
                }

                // VoIP Call
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Text1)
                        .clickable { onCallClick() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Appeler", color = BgColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ActiveCallOverlay(
    callState: CallState,
    consentGiven: Boolean,
    transcript: String,
    audioManager: AudioManager,
    onMuteClick: () -> Unit,
    onSpeakerClick: () -> Unit,
    onHangUpClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE60F0C1B)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            when (callState) {
                is CallState.Connecting -> {
                    Text("Connecting...", color = Color.Gray, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    CircularProgressIndicator(color = NeonTeal)
                }
                is CallState.Active -> {
                    Text(
                        text = callState.contactName,
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (consentGiven) "ON CALL - AI TRANSCRIPTION ACTIVE" else "ON CALL (PLAIN VoIP)",
                        color = if (consentGiven) NeonTeal else Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Call control toggles
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onMuteClick,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (callState.isMuted) ElectricViolet else Color(0x33FFFFFF))
                        ) {
                            Text(if (callState.isMuted) "Unmute" else "Mute", color = Color.White, fontSize = 11.sp)
                        }

                        IconButton(
                            onClick = onSpeakerClick,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (callState.isSpeakerOn) NeonTeal else Color(0x33FFFFFF))
                        ) {
                            Text(if (callState.isSpeakerOn) "Receiver" else "Speaker", color = Color.White, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Live Transcript Display
                    if (consentGiven) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(horizontal = 8.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0x1F293754)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxSize()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "LIVE TRANSCRIPT",
                                        fontSize = 11.sp,
                                        color = NeonTeal,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Listening...",
                                        fontSize = 10.sp,
                                        color = Color.Green,
                                        fontWeight = FontWeight.Light
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {
                                    val scrollState = rememberScrollState()
                                    LaunchedEffect(transcript) {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                    Text(
                                        text = if (transcript.isEmpty()) "Waiting for speech..." else transcript,
                                        color = if (transcript.isEmpty()) Color.Gray else Color.White,
                                        fontSize = 13.sp,
                                        modifier = Modifier.verticalScroll(scrollState)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }

                    // End call button
                    Button(
                        onClick = onHangUpClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        shape = CircleShape,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Text("End", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                is CallState.Disconnected -> {
                    Text("Call Disconnected", color = Color.White, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)) {
                        Text("Close", color = Color.White)
                    }
                }
                is CallState.Error -> {
                    Text("Call Error", color = Color.Red, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(callState.message, color = Color.LightGray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)) {
                        Text("Close", color = Color.White)
                    }
                }
                else -> {}
            }
        }
    }
}
