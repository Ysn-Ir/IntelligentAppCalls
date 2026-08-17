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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.example.appcall.presentation.theme.NeonTeal
import com.example.appcall.presentation.theme.ElectricViolet
import com.example.appcall.domain.repository.VoipRepository
import javax.inject.Inject
import android.widget.Toast
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
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
    private val selectedSectionState = mutableStateOf(4)
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
                                    containerColor = Color(0xFF111B21),
                                    contentColor = Color.White
                                ) {
                                    val sections = listOf(
                                        "To-do list", "Agenda", "Assistant IA",
                                        "Fichiers", "Appels", "Paramètres"
                                    )
                                    val icons = listOf(
                                        Icons.Default.Check,
                                        Icons.Default.Home,
                                        Icons.Default.Face,
                                        Icons.Default.Menu,
                                        Icons.Default.Call,
                                        Icons.Default.Settings
                                    )
                                    sections.forEachIndexed { index, title ->
                                        NavigationBarItem(
                                            selected = selectedSection == index,
                                            onClick = { selectedSection = index },
                                            icon = { Icon(icons[index], contentDescription = title) },
                                            label = { Text(title, fontSize = 9.sp) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = NeonTeal,
                                                unselectedIconColor = Color.Gray,
                                                selectedTextColor = NeonTeal,
                                                unselectedTextColor = Color.Gray,
                                                indicatorColor = Color(0x3300F2FE)
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
                                        TasksSection(localDatabase, voipRepository)
                                    }
                                    1 -> {
                                        AgendaSection(localDatabase, voipRepository)
                                    }
                                    2 -> {
                                        AiAssistantSection(localDatabase, voipRepository)
                                    }
                                    3 -> {
                                        FilesSection(localDatabase)
                                    }
                                    4 -> {
                                        CallScreen(
                                            viewModel = callViewModel,
                                            onLogout = {
                                                tokenStorage.clear()
                                                loginViewModel.resetState()
                                                selectedSection = 4
                                                currentScreen = AppScreen.LOGIN
                                            },
                                            onNavigateToSummary = { callId ->
                                                activeCallIdForSummary = callId
                                                currentScreen = AppScreen.SUMMARY
                                            }
                                        )
                                    }
                                    5 -> {
                                        SettingsSection(
                                            voipRepository = voipRepository,
                                            shizukuManager = shizukuManager,
                                            onLogout = {
                                                tokenStorage.clear()
                                                loginViewModel.resetState()
                                                selectedSection = 4
                                                currentScreen = AppScreen.LOGIN
                                            }
                                        )
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
        mutableStateOf(
            listOf(
                ChatDisplayItem(
                    isUser = false,
                    text = "Bonjour ! Je suis votre assistant IA. Vous pouvez me poser des questions sur l'ensemble de vos appels ou cibler un contact spécifique."
                )
            )
        )
    }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var selectedContactId by remember { mutableStateOf<String?>(null) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        voipRepository.getContacts().onSuccess { contacts = it }
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
                    text = "Assistant IA (RAG)",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Recherche intelligente dans l'historique d'appels",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            if (chatMessages.size > 1) {
                TextButton(
                    onClick = {
                        chatMessages = listOf(
                            ChatDisplayItem(
                                isUser = false,
                                text = "Historique réinitialisé. Posez une nouvelle question."
                            )
                        )
                        currentSessionId = null
                    }
                ) {
                    Text("Effacer", color = Color.Gray, fontSize = 12.sp)
                }
            }
        }

        // Contact Filter Dropdown / Row
        if (contacts.isNotEmpty()) {
            var showContactMenu by remember { mutableStateOf(false) }
            val selectedName = contacts.firstOrNull { it.id == selectedContactId }?.let { "${it.firstName} ${it.lastName}" } ?: "🌍 Tous les contacts (Chatbot Global)"

            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                OutlinedButton(
                    onClick = { showContactMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonTeal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Contexte : $selectedName",
                        color = NeonTeal,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                androidx.compose.material3.DropdownMenu(
                    expanded = showContactMenu,
                    onDismissRequest = { showContactMenu = false },
                    modifier = Modifier.background(Color(0xFF1E293B))
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("🌍 Tous les contacts (Chatbot Global)", color = Color.White) },
                        onClick = {
                            selectedContactId = null
                            showContactMenu = false
                        }
                    )
                    contacts.forEach { c ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text("👤 ${c.firstName} ${c.lastName}", color = Color.White) },
                            onClick = {
                                selectedContactId = c.id
                                showContactMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Chat Message List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(chatMessages) { item ->
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (item.isUser) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Column(
                        horizontalAlignment = if (item.isUser) Alignment.End else Alignment.Start
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (item.isUser) ElectricViolet else Color(0xFF1E293B)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.widthIn(max = 300.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = item.text,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        // Display RAG sources citation chips
                        if (!item.isUser && item.sources.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            item.sources.forEach { src ->
                                val dateStr = src.callDate?.substringBefore("T") ?: ""
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0x3300F2FE)),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.padding(top = 2.dp).widthIn(max = 280.dp)
                                ) {
                                    Text(
                                        text = "📞 Appel ${if (dateStr.isNotBlank()) "du $dateStr" else ""} : \"${src.excerpt ?: ""}\"",
                                        color = NeonTeal,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(6.dp)
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
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = NeonTeal,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("L'assistant analyse vos appels...", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Input Field + Send Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                placeholder = { Text("Posez une question sur vos appels...", color = Color.Gray, fontSize = 13.sp) },
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonTeal,
                    unfocusedBorderColor = Color.Gray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (promptText.isNotBlank() && !isLoading) {
                        val userText = promptText.trim()
                        promptText = ""
                        val newMessages = chatMessages.toMutableList()
                        newMessages.add(ChatDisplayItem(isUser = true, text = userText))
                        chatMessages = newMessages
                        isLoading = true

                        coroutineScope.launch {
                            // First check if it's an offline local command
                            val lower = userText.lowercase()
                            if (lower.startsWith("tâche") || lower.startsWith("todo") || lower.startsWith("créer tâche")) {
                                val taskTitle = userText.substringAfter("tâche").substringAfter("todo").trim()
                                if (taskTitle.isNotBlank()) {
                                    localDatabase.saveTask("task-${System.currentTimeMillis()}", taskTitle, false)
                                    val reply = "J'ai ajouté la tâche : '$taskTitle'"
                                    chatMessages = chatMessages + ChatDisplayItem(isUser = false, text = reply)
                                    isLoading = false
                                    return@launch
                                }
                            }

                            // Otherwise execute RAG Chatbot query
                            val contactId = selectedContactId
                            val result = if (contactId != null) {
                                voipRepository.chatWithContact(contactId, userText, currentSessionId)
                            } else {
                                voipRepository.globalChat(userText, currentSessionId)
                            }

                            result.onSuccess { res ->
                                currentSessionId = res.sessionId
                                chatMessages = chatMessages + ChatDisplayItem(
                                    isUser = false,
                                    text = res.reply,
                                    sources = res.sources
                                )
                            }.onFailure { err ->
                                chatMessages = chatMessages + ChatDisplayItem(
                                    isUser = false,
                                    text = "Erreur: ${err.message}"
                                )
                            }
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading && promptText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonTeal)
            ) {
                Text("Envoyer", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
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
                                .getString("custom_base_url", "") ?: ""
                        ) 
                    }
                    OutlinedTextField(
                        value = customUrlText,
                        onValueChange = { customUrlText = it },
                        label = { Text("URL du serveur (ex: http://192.168.1.50:8000)", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonTeal,
                            unfocusedBorderColor = Color.Gray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putString("custom_base_url", customUrlText.trim())
                                .apply()
                            Toast.makeText(context, "URL du serveur mise à jour", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonTeal)
                    ) {
                        Text("Enregistrer l'URL", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
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
                                    .onSuccess {
                                        Toast.makeText(context, "Export complet téléchargé avec succès (Art. 15/20)", Toast.LENGTH_LONG).show()
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
            text = { Text("Voulez-vous supprimer les enregistrements audio et transcriptions ? Les fiches contacts seront conservées.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            voipRepository.deleteVoiceData()
                                .onSuccess { Toast.makeText(context, "Données vocales supprimées", Toast.LENGTH_SHORT).show() }
                                .onFailure { Toast.makeText(context, "Erreur de suppression", Toast.LENGTH_SHORT).show() }
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
