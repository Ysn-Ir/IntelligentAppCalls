package com.example.appcall

import com.example.appcall.presentation.dashboard.TasksSection
import com.example.appcall.presentation.dashboard.AgendaSection
import com.example.appcall.presentation.dashboard.FilesSection
import com.example.appcall.domain.model.Contact
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import kotlinx.coroutines.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Divider
import androidx.compose.material3.CircularProgressIndicator
import com.example.appcall.presentation.theme.*
import com.example.appcall.domain.repository.VoipRepository
import javax.inject.Inject
import android.widget.Toast
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import com.example.appcall.presentation.auth.LoginScreen
import com.example.appcall.presentation.auth.LoginViewModel
import com.example.appcall.presentation.calling.CallScreen
import com.example.appcall.presentation.calling.CallViewModel
import com.example.appcall.presentation.summary.SummaryScreen
import com.example.appcall.presentation.summary.SummaryViewModel
import com.example.appcall.presentation.theme.AppCallTheme
import com.example.appcall.data.calling.CallingManager
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.example.appcall.data.local.LocalTask
import com.example.appcall.data.local.LocalAgendaItem
import com.example.appcall.data.local.LocalFileItem
import com.example.appcall.data.local.AppLocalDatabase
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.media.MediaPlayer
import java.io.File
import dagger.hilt.android.AndroidEntryPoint

enum class AppScreen {
    LOGIN,
    REGISTER,
    DASHBOARD,
    SUMMARY
}

// Intent extras used to communicate from BroadcastReceiver to the Activity
private const val EXTRA_CALL_INTERCEPTED = "call_intercepted"
private const val EXTRA_CALL_NUMBER = "call_number"
private const val EXTRA_CALL_ENDED = "call_ended"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var voipRepository: VoipRepository

    @Inject
    lateinit var callingManager: CallingManager

    @Inject
    lateinit var shizukuManager: com.example.appcall.data.calling.ShizukuManager

    private val loginViewModel: LoginViewModel by viewModels()
    private val callViewModel: CallViewModel by viewModels()
    private val summaryViewModel: SummaryViewModel by viewModels()

    @Inject
    lateinit var localDatabase: AppLocalDatabase

    @Inject
    lateinit var tokenStorage: com.example.appcall.data.repository.TokenStorage

    // Compose-observable state
    private val currentScreenState by lazy {
        mutableStateOf(if (tokenStorage.token != null) AppScreen.DASHBOARD else AppScreen.LOGIN)
    }
    private val activeCallIdForSummaryState = mutableStateOf("")
    private val selectedSectionState = mutableStateOf(0)
    private val showInterceptConsentState = mutableStateOf(false)
    private val interceptedNumberState = mutableStateOf("")

    private var nativeCallSessionActive = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val requiredPermissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.CALL_PHONE
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), 101)
        }

        // Request SYSTEM_ALERT_WINDOW (display over other apps) permission on Android 10+
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Veuillez autoriser l'affichage par-dessus les autres applications pour intercepter les appels",
                Toast.LENGTH_LONG
            ).show()
            val overlayIntent = Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            startActivity(overlayIntent)
        }

        // Schedule offline synchronization to run when device gets back online
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()
        val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.appcall.data.local.SyncWorker>()
            .setConstraints(constraints)
            .build()
        androidx.work.WorkManager.getInstance(this).enqueueUniqueWork(
            "OfflineSyncWork",
            androidx.work.ExistingWorkPolicy.KEEP,
            syncRequest
        )

        // Register the native call BroadcastReceiver
        val phoneStateReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context, intent: android.content.Intent) {
                if (intent.action != android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

                val stateStr = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_STATE)
                val number = intent.getStringExtra(android.telephony.TelephonyManager.EXTRA_INCOMING_NUMBER)
                    ?: "Appel en cours"

                android.util.Log.d("PhoneReceiver", "State: $stateStr | number: $number")

                when (stateStr) {
                    android.telephony.TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        // Call connected (outgoing or incoming answered).
                        // Bring our Activity to the foreground so the Compose dialog can appear.
                        val bringToFront = Intent(ctx, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra(EXTRA_CALL_INTERCEPTED, true)
                            putExtra(EXTRA_CALL_NUMBER, number)
                        }
                        ctx.startActivity(bringToFront)
                    }
                    android.telephony.TelephonyManager.EXTRA_STATE_IDLE -> {
                        // Call ended — tell the Activity to tear down the session
                        if (nativeCallSessionActive) {
                            val end = Intent(ctx, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                putExtra(EXTRA_CALL_ENDED, true)
                            }
                            ctx.startActivity(end)
                        }
                    }
                }
            }
        }

        val filter = android.content.IntentFilter(android.telephony.TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        registerReceiver(phoneStateReceiver, filter)

        // Handle the intent that launched this Activity (cold start from receiver)
        handleIncomingIntent(intent)

        setContent {
            AppCallTheme {
                var currentScreen by currentScreenState
                var activeCallIdForSummary by activeCallIdForSummaryState
                var selectedSection by selectedSectionState
                var showInterceptConsent by showInterceptConsentState
                val interceptedNumber by interceptedNumberState

                // ── PREVENT ACCIDENTAL APP CLOSING ON BACK GESTURE ──
                androidx.activity.compose.BackHandler {
                    when {
                        currentScreen == AppScreen.SUMMARY -> currentScreen = AppScreen.DASHBOARD
                        currentScreen == AppScreen.REGISTER -> currentScreen = AppScreen.LOGIN
                        currentScreen == AppScreen.DASHBOARD && selectedSection != 0 -> selectedSection = 0
                        currentScreen == AppScreen.DASHBOARD -> moveTaskToBack(true)
                    }
                }

                // Consent dialog — appears when a native call is detected
                if (showInterceptConsent) {
                    AlertDialog(
                        onDismissRequest = { showInterceptConsent = false },
                        containerColor = Color(0xFF1F2C34),
                        title = {
                            Text(
                                text = "Enregistrer cet appel ?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Column {
                                Text(
                                    text = "Appel : $interceptedNumber",
                                    color = NeonTeal,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "L'application peut enregistrer cet appel pour générer un résumé et détecter les rendez-vous.\n\nL'enregistrement reste privé et est supprimé après traitement.",
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showInterceptConsent = false
                                    nativeCallSessionActive = true
                                    callingManager.startCall(
                                        Contact(
                                            id = "native",
                                            firstName = interceptedNumber,
                                            lastName = "",
                                            phoneNumber = interceptedNumber,
                                            email = "",
                                            globalGdprConsent = true
                                        )
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NeonTeal)
                            ) {
                                Text("Oui, enregistrer", color = Color(0xFF111B21), fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            OutlinedButton(
                                onClick = {
                                    showInterceptConsent = false
                                    nativeCallSessionActive = true
                                    // No recording — just track the call
                                    callingManager.startCall(
                                        Contact(
                                            id = "native",
                                            firstName = interceptedNumber,
                                            lastName = "",
                                            phoneNumber = interceptedNumber,
                                            email = "",
                                            globalGdprConsent = false
                                        )
                                    )
                                }
                            ) {
                                Text("Non, juste le résumé", color = Color.Gray)
                            }
                        }
                    )
                }

                when (currentScreen) {
                    AppScreen.LOGIN -> {
                        LoginScreen(
                            viewModel = loginViewModel,
                            onLoginSuccess = { currentScreen = AppScreen.DASHBOARD },
                            onNavigateToRegister = { currentScreen = AppScreen.REGISTER }
                        )
                    }
                    AppScreen.REGISTER -> {
                        com.example.appcall.presentation.auth.RegisterScreen(
                            viewModel = loginViewModel,
                            onRegisterSuccess = { currentScreen = AppScreen.DASHBOARD },
                            onNavigateToLogin = { currentScreen = AppScreen.LOGIN }
                        )
                    }
                    AppScreen.DASHBOARD -> {
                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    containerColor = BgColor,
                                    contentColor = Text1,
                                    tonalElevation = 0.dp
                                ) {
                                    val sections = listOf(
                                        "Appels" to "📞",
                                        "Assistant IA" to "🤖",
                                        "Agenda" to "📅",
                                        "Tâches" to "📋"
                                    )
                                    sections.forEachIndexed { index, (title, iconEmoji) ->
                                        val isSelected = selectedSection == index
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { selectedSection = index },
                                            icon = { Text(text = iconEmoji, fontSize = 16.sp) },
                                            label = {
                                                Text(
                                                    text = title,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Text1 else Text3
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Text1,
                                                unselectedIconColor = Text3,
                                                selectedTextColor = Text1,
                                                unselectedTextColor = Text3,
                                                indicatorColor = Surface2
                                            )
                                        )
                                    }
                                }
                            }
                        ) { paddingValues ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(paddingValues)
                            ) {
                                when (selectedSection) {
                                    0 -> {
                                        CallScreen(
                                            viewModel = callViewModel,
                                            onLogout = {
                                                tokenStorage.clear()
                                                loginViewModel.resetState()
                                                selectedSection = 0
                                                currentScreen = AppScreen.LOGIN
                                            },
                                            onNavigateToSummary = { callId ->
                                                activeCallIdForSummary = callId
                                                currentScreen = AppScreen.SUMMARY
                                            }
                                        )
                                    }
                                    1 -> {
                                        AiAssistantSection(localDatabase, voipRepository)
                                    }
                                    2 -> {
                                        AgendaSection(localDatabase, voipRepository)
                                    }
                                    3 -> {
                                        TasksSection(localDatabase, voipRepository)
                                    }
                                }
                            }
                        }
                    }
                    AppScreen.SUMMARY -> {
                        SummaryScreen(
                            callId = activeCallIdForSummary,
                            viewModel = summaryViewModel,
                            onBackClick = { currentScreen = AppScreen.DASHBOARD }
                        )
                    }
                }
            }
        }
    }

    /**
     * Called when the Activity is already running and a new Intent arrives
     * (e.g. the BroadcastReceiver fires FLAG_ACTIVITY_SINGLE_TOP).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return

        when {
            intent.getBooleanExtra(EXTRA_CALL_INTERCEPTED, false) -> {
                val number = intent.getStringExtra(EXTRA_CALL_NUMBER) ?: "Appel en cours"
                android.util.Log.d("MainActivity", "Call intercepted: $number")
                interceptedNumberState.value = number
                currentScreenState.value = AppScreen.DASHBOARD
                selectedSectionState.value = 4
                showInterceptConsentState.value = true
            }
            intent.getBooleanExtra(EXTRA_CALL_ENDED, false) -> {
                android.util.Log.d("MainActivity", "Native call ended")
                nativeCallSessionActive = false
                showInterceptConsentState.value = false
                val callId = callingManager.currentActiveCallId ?: "native-${System.currentTimeMillis()}"
                callingManager.disconnect()
                activeCallIdForSummaryState.value = callId
                currentScreenState.value = AppScreen.SUMMARY
            }
      
        }
    }
}

data class ChatDisplayItem(
    val isUser: Boolean,
    val text: String,
    val sources: List<com.example.appcall.data.model.ChatSourceDto> = emptyList()
)

@Composable
fun AiAssistantSection(
    localDatabase: AppLocalDatabase,
    voipRepository: VoipRepository
) {
    var promptText by remember { mutableStateOf("") }
    var chatMessages by remember {
        mutableStateOf<List<ChatDisplayItem>>(emptyList())
    }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var selectedContactId by remember { mutableStateOf<String?>(null) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var sessionList by remember { mutableStateOf<List<com.example.appcall.data.local.ChatSessionSummary>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    fun startNewConversation() {
        currentSessionId = "session-${System.currentTimeMillis()}"
        chatMessages = listOf(
            ChatDisplayItem(
                isUser = false,
                text = "Nouvelle conversation démarrée ! Posez votre question sur l'ensemble de vos appels."
            )
        )
    }

    fun loadChatHistory(contactId: String?) {
        val sessions = localDatabase.getChatSessions(contactId)
        if (sessions.isNotEmpty()) {
            val latestSessionId = sessions.first().sessionId
            val msgs = localDatabase.getSessionMessages(latestSessionId)
            chatMessages = msgs.map { item ->
                ChatDisplayItem(
                    isUser = item.isUser,
                    text = item.text
                )
            }
            currentSessionId = latestSessionId
        } else {
            currentSessionId = "session-${System.currentTimeMillis()}"
            chatMessages = listOf(
                ChatDisplayItem(
                    isUser = false,
                    text = "Bonjour ! Je suis votre assistant IA. Vous pouvez me poser des questions sur l'ensemble de vos appels ou cibler un contact spécifique."
                )
            )
        }
    }

    fun openSession(sessionId: String) {
        val msgs = localDatabase.getSessionMessages(sessionId)
        if (msgs.isNotEmpty()) {
            chatMessages = msgs.map { item ->
                ChatDisplayItem(
                    isUser = item.isUser,
                    text = item.text
                )
            }
            currentSessionId = sessionId
        }
        showHistoryDialog = false
    }

    fun deleteSessionItem(sessionId: String) {
        localDatabase.deleteSession(sessionId)
        sessionList = localDatabase.getChatSessions(selectedContactId)
        if (currentSessionId == sessionId) {
            startNewConversation()
        }
    }

    LaunchedEffect(Unit) {
        voipRepository.getContacts().onSuccess { contacts = it }
    }

    LaunchedEffect(selectedContactId) {
        loadChatHistory(selectedContactId)
    }

    // ── CONVERSATION HISTORY DIALOG ──
    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📜 Historique des Conversations",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    Button(
                        onClick = {
                            startNewConversation()
                            showHistoryDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("+ Nouvelle Conversation", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (sessionList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aucune conversation enregistrée.", color = Color.Gray, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sessionList) { session ->
                                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH).format(Date(session.lastTimestamp))
                                val isCurrent = session.sessionId == currentSessionId

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isCurrent) Color(0x3300F2FE) else Color(0xFF1E293B)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = dateStr,
                                                color = if (isCurrent) NeonTeal else Color.Gray,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Card(
                                                shape = RoundedCornerShape(4.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF))
                                            ) {
                                                Text(
                                                    text = "${session.messageCount} msg",
                                                    color = Color.White,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = session.previewText,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { deleteSessionItem(session.sessionId) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Supprimer",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Button(
                                                onClick = { openSession(session.sessionId) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isCurrent) NeonTeal else ElectricViolet
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (isCurrent) "Actif" else "Ouvrir",
                                                    color = if (isCurrent) Color.Black else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) {
                    Text("Fermer", color = NeonTeal, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        // ── TOP HEADER (Screen 3) ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgColor)
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Assistant Intelligent Calls",
                        color = Text1,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "gpt-4o · groq-llama",
                        color = Text3,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Surface1)
                            .border(1.dp, BorderColor, RoundedCornerShape(7.dp))
                            .clickable {
                                sessionList = localDatabase.getChatSessions(selectedContactId)
                                showHistoryDialog = true
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Historique", color = Text2, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(Surface1)
                            .border(1.dp, BorderColor, RoundedCornerShape(7.dp))
                            .clickable { startNewConversation() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("＋ Nouveau", color = Text2, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── CONTEXT CHIPS (Horizontal Scroll) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isGlobal = selectedContactId == null
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isGlobal) Surface2 else Surface1)
                        .border(1.dp, if (isGlobal) BorderStrong else BorderColor, RoundedCornerShape(7.dp))
                        .clickable { selectedContactId = null }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Tous les appels",
                        color = if (isGlobal) Text1 else Text2,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                contacts.forEach { c ->
                    val isSelected = selectedContactId == c.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSelected) Surface2 else Surface1)
                            .border(1.dp, if (isSelected) BorderStrong else BorderColor, RoundedCornerShape(7.dp))
                            .clickable { selectedContactId = c.id }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${c.firstName} ${c.lastName}".trim(),
                            color = if (isSelected) Text1 else Text2,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ── CHAT MESSAGE LIST ──
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(chatMessages) { item ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (item.isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 290.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = if (item.isUser) 12.dp else 3.dp,
                                    bottomEnd = if (item.isUser) 3.dp else 12.dp
                                )
                            )
                            .background(if (item.isUser) Surface2 else Surface1)
                            .border(1.dp, if (item.isUser) BorderStrong else BorderColor, RoundedCornerShape(12.dp))
                            .padding(horizontal = 13.dp, vertical = 10.dp)
                    ) {
                        Column {
                            Text(
                                text = item.text,
                                color = Text1,
                                fontSize = 13.sp,
                                lineHeight = 19.sp
                            )

                            if (!item.isUser && item.sources.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(9.dp))
                                Divider(color = BorderColor, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(8.dp))
                                item.sources.forEach { src ->
                                    val dateStr = src.callDate?.substringBefore("T") ?: ""
                                    Text(
                                        text = "Source — Appel ${if (dateStr.isNotBlank()) "du $dateStr" else ""} : \"${src.excerpt ?: ""}\"",
                                        color = Text3,
                                        fontSize = 10.5.sp,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Surface1)
                                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = AccentColor, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("L'assistant analyse vos appels...", color = Text3, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // ── CHAT INPUT BAR ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgColor)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface1)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📎", fontSize = 13.sp)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Surface1)
                    .border(1.dp, BorderColor, RoundedCornerShape(9.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    textStyle = androidx.compose.ui.text.TextStyle(color = Text1, fontSize = 13.sp),
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (promptText.isEmpty()) {
                            Text("Posez une question sur vos appels...", color = Text3, fontSize = 13.sp)
                        }
                        innerTextField()
                    }
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface1)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "🎙", fontSize = 13.sp)
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (promptText.isNotBlank()) Text1 else Surface2)
                    .clickable(enabled = promptText.isNotBlank() && !isLoading) {
                        val userText = promptText.trim()
                        promptText = ""
                        if (currentSessionId.isNullOrBlank()) {
                            currentSessionId = "session-${System.currentTimeMillis()}"
                        }
                        val activeSessionId = currentSessionId!!

                        chatMessages = chatMessages + ChatDisplayItem(isUser = true, text = userText)
                        isLoading = true

                        // Save user message to persistent local history
                        localDatabase.saveChatMessage(
                            contactId = selectedContactId,
                            isUser = true,
                            text = userText,
                            sessionId = activeSessionId
                        )

                        coroutineScope.launch {
                            // Check offline local command
                            val lower = userText.lowercase()
                            if (lower.startsWith("tâche") || lower.startsWith("todo") || lower.startsWith("créer tâche")) {
                                val taskTitle = userText.substringAfter("tâche").substringAfter("todo").trim()
                                if (taskTitle.isNotBlank()) {
                                    localDatabase.saveTask("task-${System.currentTimeMillis()}", taskTitle, false)
                                    val reply = "J'ai ajouté la tâche : '$taskTitle'"
                                    localDatabase.saveChatMessage(
                                        contactId = selectedContactId,
                                        isUser = false,
                                        text = reply,
                                        sessionId = activeSessionId
                                    )
                                    chatMessages = chatMessages + ChatDisplayItem(isUser = false, text = reply)
                                    isLoading = false
                                    return@launch
                                }
                            }

                            // RAG Chatbot query
                            val contactId = selectedContactId
                            val result = if (contactId != null) {
                                voipRepository.chatWithContact(contactId, userText, activeSessionId)
                            } else {
                                voipRepository.globalChat(userText, activeSessionId)
                            }

                            result.onSuccess { res ->
                                val finalSessionId = if (!res.sessionId.isNullOrBlank()) res.sessionId else activeSessionId
                                currentSessionId = finalSessionId
                                localDatabase.saveChatMessage(
                                    contactId = selectedContactId,
                                    isUser = false,
                                    text = res.reply,
                                    sessionId = finalSessionId
                                )
                                chatMessages = chatMessages + ChatDisplayItem(
                                    isUser = false,
                                    text = res.reply,
                                    sources = res.sources
                                )
                            }.onFailure { err ->
                                val errReply = "Erreur: ${err.message}"
                                localDatabase.saveChatMessage(
                                    contactId = selectedContactId,
                                    isUser = false,
                                    text = errReply,
                                    sessionId = activeSessionId
                                )
                                chatMessages = chatMessages + ChatDisplayItem(
                                    isUser = false,
                                    text = errReply
                                )
                            }
                            isLoading = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "↑", color = if (promptText.isNotBlank()) BgColor else Text3, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SettingsSection(
    voipRepository: VoipRepository,
    shizukuManager: com.example.appcall.data.calling.ShizukuManager? = null,
    onLogout: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showDeleteVoiceDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111B21))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Paramètres", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        // ── SHIZUKU STATUS CARD ─────────────────────────────────────────────────
        item {
            val isShizukuAvailable = remember { shizukuManager?.isShizukuAvailable() == true }
            val hasShizukuPerm = remember { shizukuManager?.hasShizukuPermission() == true }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F293754))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("SHIZUKU API — PERMISSIONS ÉLEVÉES ADB", color = NeonTeal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (isShizukuAvailable && hasShizukuPerm) Color.Green else Color.Red, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                isShizukuAvailable && hasShizukuPerm -> "Shizuku Connecté (Permissions ADB Actives)"
                                isShizukuAvailable -> "Shizuku Détecté (Permission requise)"
                                else -> "Shizuku Non Détecté / Service arrêté"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (isShizukuAvailable && !hasShizukuPerm) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { shizukuManager?.requestShizukuPermission() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonTeal)
                        ) {
                            Text("Autoriser Shizuku", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── SERVER CONFIG CARD ─────────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F293754))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("CONFIGURATION SERVEUR & IA", color = NeonTeal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    var customUrlText by remember { 
                        mutableStateOf(
                            context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE)
                                .getString("custom_base_url", "http://127.0.0.1:8000") ?: "http://127.0.0.1:8000"
                        ) 
                    }
                    OutlinedTextField(
                        value = customUrlText,
                        onValueChange = { customUrlText = it },
                        label = { Text("URL du serveur (ex: http://127.0.0.1:8000)", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonTeal,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                customUrlText = "http://127.0.0.1:8000"
                                context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("custom_base_url", "http://127.0.0.1:8000")
                                    .apply()
                                Toast.makeText(context, "Mode USB sélectionné (127.0.0.1:8000)", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🔌 Mode USB", color = NeonTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                customUrlText = "http://192.168.1.177:8000"
                                context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("custom_base_url", "http://192.168.1.177:8000")
                                    .apply()
                                Toast.makeText(context, "Mode Wi-Fi sélectionné (192.168.1.177:8000)", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("📶 Mode Wi-Fi", color = Color(0xFF60A5FA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val trimmed = customUrlText.trim()
                                context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE)
                                    .edit()
                                    .putString("custom_base_url", trimmed)
                                    .apply()
                                Toast.makeText(context, "URL enregistrée", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Enregistrer", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                    try {
                                        var testUrl = customUrlText.trim()
                                        if (!testUrl.startsWith("http://") && !testUrl.startsWith("https://")) {
                                            testUrl = "http://$testUrl"
                                        }
                                        if (!testUrl.endsWith("/")) testUrl += "/"
                                        val url = java.net.URL("${testUrl}api/v1/calls")
                                        val conn = url.openConnection() as java.net.HttpURLConnection
                                        conn.connectTimeout = 3000
                                        conn.readTimeout = 3000
                                        conn.requestMethod = "GET"
                                        val code = conn.responseCode
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            if (code in 200..499) {
                                                Toast.makeText(context, "✅ Backend connecté (HTTP $code)", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "⚠️ Réponse inattendue: HTTP $code", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            Toast.makeText(context, "❌ Impossible de joindre le serveur: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("🔄 Tester", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── CALL RECORDING SETTINGS CARD ───────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F293754))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MODE D'ENREGISTREMENT APPEL SIM", color = NeonTeal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    val callPrefs = remember { context.getSharedPreferences("call_settings", android.content.Context.MODE_PRIVATE) }
                    var useBtSco by remember { mutableStateOf(callPrefs.getBoolean("use_bt_sco", true)) }
                    var useBridgeMode by remember { mutableStateOf(callPrefs.getBoolean("use_pbx_bridge", false)) }
                    var gatewayNum by remember { mutableStateOf(callPrefs.getString("pbx_gateway_number", "") ?: "") }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Micro Casque Bluetooth (SCO)", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = useBtSco,
                            onCheckedChange = {
                                useBtSco = it
                                callPrefs.edit().putBoolean("use_bt_sco", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonTeal)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pont d'Appel PBX (2-Way Server)", color = Color.White, fontSize = 13.sp)
                        Switch(
                            checked = useBridgeMode,
                            onCheckedChange = {
                                useBridgeMode = it
                                callPrefs.edit().putBoolean("use_pbx_bridge", it).apply()
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonTeal)
                        )
                    }
                    if (useBridgeMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = gatewayNum,
                            onValueChange = {
                                gatewayNum = it
                                callPrefs.edit().putString("pbx_gateway_number", it.trim()).apply()
                            },
                            label = { Text("Numéro Passerelle PBX (ex: +33180000000)", color = Color.Gray) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonTeal,
                                unfocusedBorderColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // ── RGPD & GESTION DES DONNÉES ──────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0x1F293754))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("RGPD & PROTECTION DES DONNÉES", color = NeonTeal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Conformément au RGPD (Règlement Général sur la Protection des Données), vous disposez d'un droit d'accès, d'export et d'effacement complet de vos données.",
                        color = Color.LightGray, fontSize = 12.sp, lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Button 1: Export all data (Art. 15 / 20)
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                voipRepository.exportAllData()
                                    .onSuccess { jsonContent ->
                                        try {
                                            val exportFile = java.io.File(context.cacheDir, "appcall_gdpr_export.json")
                                            exportFile.writeText(jsonContent)
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                exportFile
                                            )
                                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "application/json"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Export Données RGPD AppCall")
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Exporter mes données (JSON)"))
                                            Toast.makeText(context, "Export généré avec succès", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Erreur export: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    .onFailure {
                                        Toast.makeText(context, "Export échoué : ${it.message}", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricViolet)
                    ) {
                        Text("📥 Exporter toutes mes données (Art. 15 RGPD)", color = Color.White, fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button 2: Delete voice recordings
                    OutlinedButton(
                        onClick = { showDeleteVoiceDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B))
                    ) {
                        Text("Effacer uniquement les enregistrements", color = Color(0xFFF59E0B), fontSize = 13.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Button 3: Delete full account (Art. 17)
                    Button(
                        onClick = { showDeleteAccountDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("🗑️ Supprimer mon compte & mes données (Art. 17)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        // ── LOGOUT BUTTON ───────────────────────────────────────────────────────
        item {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))
            ) {
                Text("DÉCONNEXION (LOGOUT)", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Dialog for Voice Delete
    if (showDeleteVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteVoiceDialog = false },
            title = { Text("Supprimer les enregistrements vocaux", color = Color.White) },
            text = { Text("Voulez-vous supprimer les enregistrements audio et transcriptions ? Les fichiers locaux et distants seront effacés.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            // Purge local recording files on device
                            val recDir = java.io.File(context.filesDir, "recordings")
                            if (recDir.exists() && recDir.isDirectory) {
                                recDir.listFiles()?.forEach { it.delete() }
                            }
                            voipRepository.deleteVoiceData()
                                .onSuccess { Toast.makeText(context, "Tous les enregistrements vocaux ont été supprimés", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, "Enregistrements locaux supprimés", Toast.LENGTH_SHORT).show() }
                        }
                        showDeleteVoiceDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B))
                ) { Text("Confirmer", color = Color.Black) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteVoiceDialog = false }) { Text("Annuler", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Dialog for Full Account Deletion (Art. 17)
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = { Text("⚠️ Suppression définitive du compte", color = Color.White) },
            text = {
                Text(
                    "Conformément à l'Art. 17 du RGPD (Droit à l'oubli), votre compte, tous vos contacts, appels, enregistrements et résumés seront définitivement effacés. Cette action est irréversible.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            voipRepository.deleteAccount()
                                .onSuccess {
                                    Toast.makeText(context, "Compte et données définitivement supprimés", Toast.LENGTH_LONG).show()
                                    onLogout()
                                }
                                .onFailure {
                                    Toast.makeText(context, "Erreur : ${it.message}", Toast.LENGTH_SHORT).show()
                                    onLogout()
                                }
                        }
                        showDeleteAccountDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) { Text("Supprimer Définitivement", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Annuler", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}
