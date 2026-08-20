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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.text.style.TextOverflow
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
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

    @Inject
    lateinit var offlineSyncManager: com.example.appcall.data.sync.OfflineSyncManager

    // Compose-observable state
    private val currentScreenState by lazy {
        mutableStateOf(if (tokenStorage.token != null) AppScreen.DASHBOARD else AppScreen.LOGIN)
    }
    private val activeCallIdForSummaryState = mutableStateOf("")
    private val selectedSectionState = mutableStateOf(0)
    private val showInterceptConsentState = mutableStateOf(false)
    private val interceptedNumberState = mutableStateOf("")

    private var nativeCallSessionActive = false

    override fun onResume() {
        super.onResume()
        if (::offlineSyncManager.isInitialized) {
            offlineSyncManager.triggerSync()
        }
    }

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

        // Initialize Native Notification Channels
        com.example.appcall.data.notification.AppNotificationManager.initChannels(this)

        // Handle navigation from incoming intents / notifications
        val summaryCallId = intent.getStringExtra("navigate_to_summary_call_id")
        if (!summaryCallId.isNullOrBlank()) {
            activeCallIdForSummaryState.value = summaryCallId
            currentScreenState.value = AppScreen.SUMMARY
        }
        val targetSection = intent.getIntExtra("navigate_to_section", -1)
        if (targetSection >= 0) {
            selectedSectionState.value = targetSection
        }

        setContent {
            val themePrefs = remember { getSharedPreferences("app_theme", android.content.Context.MODE_PRIVATE) }
            var currentThemeMode by remember {
                val saved = themePrefs.getString("selected_theme", "DARK") ?: "DARK"
                mutableStateOf(try { AppThemeMode.valueOf(saved) } catch (e: Exception) { AppThemeMode.DARK })
            }

            AppCallTheme(themeMode = currentThemeMode) {
                var currentScreen by currentScreenState
                var activeCallIdForSummary by activeCallIdForSummaryState
                var selectedSection by selectedSectionState

                // ── PREVENT ACCIDENTAL APP CLOSING ON BACK GESTURE ──
                androidx.activity.compose.BackHandler {
                    when {
                        currentScreen == AppScreen.SUMMARY -> currentScreen = AppScreen.DASHBOARD
                        currentScreen == AppScreen.REGISTER -> currentScreen = AppScreen.LOGIN
                        currentScreen == AppScreen.DASHBOARD && selectedSection != 0 -> selectedSection = 0
                        currentScreen == AppScreen.DASHBOARD -> moveTaskToBack(true)
                    }
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
                                        Triple("Appels", Icons.Default.Call, 0),
                                        Triple("Assistant IA", Icons.Default.Face, 1),
                                        Triple("Agenda", Icons.Default.Menu, 2),
                                        Triple("Tâches", Icons.Default.Check, 3),
                                        Triple("Paramètres", Icons.Default.Settings, 4)
                                    )
                                    sections.forEach { (title, iconVector, index) ->
                                        val isSelected = selectedSection == index
                                        NavigationBarItem(
                                            selected = isSelected,
                                            onClick = { selectedSection = index },
                                            icon = {
                                                Icon(
                                                    imageVector = iconVector,
                                                    contentDescription = title,
                                                    modifier = Modifier.size(24.dp),
                                                    tint = if (isSelected) Text1 else Text3
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = title,
                                                    fontSize = 9.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Text1 else Text3,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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
                                    4 -> {
                                        SettingsSection(
                                            voipRepository = voipRepository,
                                            shizukuManager = shizukuManager,
                                            currentThemeMode = currentThemeMode,
                                            onThemeChange = { newMode -> currentThemeMode = newMode },
                                            onLogout = {
                                                tokenStorage.clear()
                                                loginViewModel.resetState()
                                                selectedSection = 0
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

        val summaryCallId = intent.getStringExtra("navigate_to_summary_call_id")
        if (!summaryCallId.isNullOrBlank()) {
            activeCallIdForSummaryState.value = summaryCallId
            currentScreenState.value = AppScreen.SUMMARY
            return
        }

        val targetSection = intent.getIntExtra("navigate_to_section", -1)
        if (targetSection >= 0) {
            selectedSectionState.value = targetSection
            currentScreenState.value = AppScreen.DASHBOARD
            return
        }

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
    val context = androidx.compose.ui.platform.LocalContext.current
    var promptText by remember { mutableStateOf("") }
    var attachedFileName by remember { mutableStateOf<String?>(null) }
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

    // ── SPEECH-TO-TEXT DICTATION LAUNCHER ──
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                promptText = if (promptText.isBlank()) spokenText else "$promptText $spokenText"
                android.widget.Toast.makeText(context, "Texte dicté ajouté !", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── FILE PICKER LAUNCHER ──
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                var fileName: String? = null
                val cursor = context.contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (c.moveToFirst() && nameIndex >= 0) {
                        fileName = c.getString(nameIndex)
                    }
                }
                if (fileName == null) {
                    fileName = it.lastPathSegment ?: "document.pdf"
                }
                attachedFileName = fileName
                android.widget.Toast.makeText(context, "Fichier joint : $fileName", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Erreur fichier: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun startNewConversation() {
        currentSessionId = "session-${System.currentTimeMillis()}"
        attachedFileName = null
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = null,
                        tint = Text1,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Historique des Conversations",
                        color = Text1,
                        fontSize = 15.sp,
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
                        colors = ButtonDefaults.buttonColors(containerColor = Text1),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = BgColor, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Nouvelle Conversation", color = BgColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    if (sessionList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Aucune conversation enregistrée.", color = Text3, fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sessionList) { session ->
                                val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH).format(Date(session.lastTimestamp))
                                val isCurrent = session.sessionId == currentSessionId

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isCurrent) Surface2 else Surface1)
                                        .border(1.dp, if (isCurrent) AccentColor else BorderColor, RoundedCornerShape(10.dp))
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = dateStr,
                                                color = if (isCurrent) AccentText else Text2,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(if (isCurrent) AccentDim else Surface2)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = if (isCurrent) "Session Active" else "${session.messageCount} msg",
                                                    color = if (isCurrent) AccentText else Text3,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = session.previewText,
                                            color = Text1,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
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
                                                    tint = DangerColor,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Button(
                                                onClick = { openSession(session.sessionId) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isCurrent) AccentColor else Surface2
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = if (isCurrent) "Actif" else "Ouvrir",
                                                    color = if (isCurrent) Text1 else Text2,
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
                    Text("Fermer", color = Text1, fontWeight = FontWeight.Bold)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Assistant Intelligent Calls",
                        color = Text1,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "gpt-4o · groq-llama-3.3",
                        color = Text3,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface1)
                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                            .clickable {
                                sessionList = localDatabase.getChatSessions(selectedContactId)
                                showHistoryDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Historique", tint = Text2, modifier = Modifier.size(16.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface1)
                            .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                            .clickable { startNewConversation() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Nouveau", tint = AccentColor, modifier = Modifier.size(16.dp))
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

        // ── ATTACHED FILE CHIP (If selected) ──
        if (attachedFileName != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgColor)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Surface2)
                        .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = AccentText, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = attachedFileName!!,
                            color = Text1,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Supprimer fichier",
                            tint = Text3,
                            modifier = Modifier
                                .size(14.dp)
                                .clickable { attachedFileName = null }
                        )
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
            // File Attachment Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface1)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .clickable {
                        filePickerLauncher.launch("*/*")
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Share, contentDescription = "Joindre un document", tint = Text2, modifier = Modifier.size(16.dp))
            }

            // Input TextField
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

            // Microphone Dictation Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface1)
                    .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                    .clickable {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Parlez à votre assistant AppCall...")
                            }
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Dictée vocale non disponible sur ce terminal", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Call, contentDescription = "Dictée vocale", tint = AccentColor, modifier = Modifier.size(16.dp))
            }

            // Send Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (promptText.isNotBlank()) Text1 else Surface2)
                    .clickable(enabled = promptText.isNotBlank() && !isLoading) {
                        val basePrompt = promptText.trim()
                        val fullPrompt = if (attachedFileName != null) {
                            "$basePrompt\n[Fichier joint: $attachedFileName]"
                        } else basePrompt

                        promptText = ""
                        attachedFileName = null
                        if (currentSessionId.isNullOrBlank()) {
                            currentSessionId = "session-${System.currentTimeMillis()}"
                        }
                        val activeSessionId = currentSessionId!!

                        chatMessages = chatMessages + ChatDisplayItem(isUser = true, text = fullPrompt)
                        isLoading = true

                        // Save user message to persistent local history
                        localDatabase.saveChatMessage(
                            contactId = selectedContactId,
                            isUser = true,
                            text = fullPrompt,
                            sessionId = activeSessionId
                        )

                        coroutineScope.launch {
                            // Check offline local command
                            val lower = fullPrompt.lowercase()
                            if (lower.startsWith("tâche") || lower.startsWith("todo") || lower.startsWith("créer tâche")) {
                                val taskTitle = fullPrompt.substringAfter("tâche").substringAfter("todo").trim()
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
                                voipRepository.chatWithContact(contactId, fullPrompt, activeSessionId)
                            } else {
                                voipRepository.globalChat(fullPrompt, activeSessionId)
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
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Envoyer",
                    tint = if (promptText.isNotBlank()) BgColor else Text3,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun SettingsSection(
    voipRepository: VoipRepository,
    shizukuManager: com.example.appcall.data.calling.ShizukuManager? = null,
    currentThemeMode: AppThemeMode = AppThemeMode.DARK,
    onThemeChange: (AppThemeMode) -> Unit = {},
    onLogout: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Profil & Préférences, 1: Paramètres Avancés
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showDeleteVoiceDialog by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showChangePasswordDialog by remember { mutableStateOf(false) }

    // User Profile state
    var userProfile by remember { mutableStateOf<com.example.appcall.data.model.UserProfileDto?>(null) }
    var profileFirstName by remember { mutableStateOf("") }
    var profileLastName by remember { mutableStateOf("") }
    var profileEmail by remember { mutableStateOf("") }
    var profileNumber by remember { mutableStateOf("") }

    // Change Password state
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        voipRepository.getProfile().onSuccess {
            userProfile = it
            profileFirstName = it.firstName
            profileLastName = it.lastName
            profileEmail = it.email
            profileNumber = it.number ?: ""
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Paramètres", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Text1)
            Text("Profil, Thème, IA, VoIP, Réseau & RGPD", fontSize = 11.5.sp, color = Text3, modifier = Modifier.padding(top = 2.dp))
        }

        // ── TOP SEGMENTED TAB SELECTOR (PROFIL VS ADVANCED) ─────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Surface1)
                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Tab 0: Profil & Préférences
                val isTab0 = selectedTab == 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTab0) Surface2 else Color.Transparent)
                        .border(1.dp, if (isTab0) BorderStrong else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Face,
                            contentDescription = null,
                            tint = if (isTab0) Text1 else Text3,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Profil & Préférences",
                            color = if (isTab0) Text1 else Text3,
                            fontSize = 11.5.sp,
                            fontWeight = if (isTab0) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Tab 1: Paramètres Avancés
                val isTab1 = selectedTab == 1
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isTab1) Surface2 else Color.Transparent)
                        .border(1.dp, if (isTab1) BorderStrong else Color.Transparent, RoundedCornerShape(8.dp))
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            tint = if (isTab1) Text1 else Text3,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Paramètres Avancés",
                            color = if (isTab1) Text1 else Text3,
                            fontSize = 11.5.sp,
                            fontWeight = if (isTab1) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (selectedTab == 0) {
            // ═════════════════════════════════════════════════════════════════════
            // TAB 1: PROFIL & PRÉFÉRENCES GÉNÉRALES
            // ═════════════════════════════════════════════════════════════════════

            // ── 1. USER PROFILE CARD ────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("MON PROFIL & IDENTIFIANTS", color = Text3, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.5.sp)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(AccentDim)
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Text("Compte Actif", color = AccentText, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val initials = "${profileFirstName.take(1)}${profileLastName.take(1)}".uppercase().ifBlank { "U" }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Surface2)
                                    .border(1.dp, AccentColor.copy(alpha = 0.4f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = initials, color = Text1, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                val displayName = if (profileFirstName.isNotBlank() || profileLastName.isNotBlank()) {
                                    "$profileFirstName $profileLastName".trim()
                                } else "Utilisateur"
                                Text(text = displayName, color = Text1, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                                Text(text = profileEmail.ifBlank { "Non renseigné" }, color = Text2, fontSize = 12.sp)
                                if (profileNumber.isNotBlank()) {
                                    Text(text = profileNumber, color = Text3, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showEditProfileDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text("Modifier Profil", color = Text1, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Button(
                                onClick = { 
                                    oldPassword = ""
                                    newPassword = ""
                                    confirmPassword = ""
                                    showChangePasswordDialog = true 
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                            ) {
                                Text("Mot de passe", color = Text1, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // ── 2. APPARENCE & THÈME ───────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("APPARENCE & THÈME", color = Text3, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.5.sp)
                                Text("Personnalisez l'affichage visuel", color = Text2, fontSize = 11.5.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val themeOptions = listOf(
                            Triple(AppThemeMode.DARK, "Sombre", "Obscur"),
                            Triple(AppThemeMode.LIGHT, "Clair", "Lumineux"),
                            Triple(AppThemeMode.SYSTEM, "Système", "Auto")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            themeOptions.forEach { (mode, title, sub) ->
                                val isSelected = currentThemeMode == mode
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentColor else Surface2)
                                        .border(1.dp, if (isSelected) AccentColor else BorderColor, RoundedCornerShape(8.dp))
                                        .clickable {
                                            onThemeChange(mode)
                                            context.getSharedPreferences("app_theme", android.content.Context.MODE_PRIVATE)
                                                .edit()
                                                .putString("selected_theme", mode.name)
                                                .apply()
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = title,
                                            color = if (isSelected) Text1 else Text2,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = sub,
                                            color = if (isSelected) Text1.copy(alpha = 0.8f) else Text3,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 2.5 LANGUAGE & AI SPEECH (7 LANGUAGES + AUTO) ───────────────────
            item {
                val netPrefs = remember { context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE) }
                var currentLanguage by remember { mutableStateOf(netPrefs.getString("app_language", "en") ?: "en") }

                val languages = listOf(
                    Triple("en", "English 🇬🇧", "EN"),
                    Triple("fr", "Français 🇫🇷", "FR"),
                    Triple("ar", "العربية 🇸🇦", "AR"),
                    Triple("es", "Español 🇪🇸", "ES"),
                    Triple("de", "Deutsch 🇩🇪", "DE"),
                    Triple("zh", "中文 🇨🇳", "ZH"),
                    Triple("ja", "日本語 🇯🇵", "JA"),
                    Triple("auto", "Auto 🌐", "AUTO")
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("LANGUE & INTELLIGENCE IA", color = Text3, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.5.sp)
                                Text("Transcription Whisper, Résumés IA & Assistant", color = Text2, fontSize = 11.5.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Grid of 8 options (4 rows of 2 or 2 rows of 4)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            languages.chunked(4).forEach { rowLangs ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowLangs.forEach { (code, title, shortLabel) ->
                                        val isSelected = currentLanguage == code
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) AccentColor else Surface2)
                                                .border(1.dp, if (isSelected) AccentColor else BorderColor, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    currentLanguage = code
                                                    netPrefs.edit().putString("app_language", code).apply()
                                                }
                                                .padding(vertical = 9.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = title,
                                                    color = if (isSelected) Text1 else Text2,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
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

            // ── 3. PRÉFÉRENCES D'APPLICATION ────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("PRÉFÉRENCES D'ENREGISTREMENT & APPELS", color = Text3, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(10.dp))

                        val callPrefs = remember { context.getSharedPreferences("call_recording_prefs", android.content.Context.MODE_PRIVATE) }
                        var autoRecord by remember { mutableStateOf(callPrefs.getBoolean("auto_record_calls", true)) }
                        var highQualityAudio by remember { mutableStateOf(callPrefs.getBoolean("hq_audio", true)) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Enregistrement automatique des appels", color = Text1, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                Text("Interception native lors du décrochage", color = Text3, fontSize = 10.5.sp)
                            }
                            Switch(
                                checked = autoRecord,
                                onCheckedChange = {
                                    autoRecord = it
                                    callPrefs.edit().putBoolean("auto_record_calls", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = BgColor, checkedTrackColor = Text1)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Divider(color = BorderColor, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Format Audio HD (16kHz WAV)", color = Text1, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                Text("Précision optimale pour Whisper & Deepgram", color = Text3, fontSize = 10.5.sp)
                            }
                            Switch(
                                checked = highQualityAudio,
                                onCheckedChange = {
                                    highQualityAudio = it
                                    callPrefs.edit().putBoolean("hq_audio", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = BgColor, checkedTrackColor = Text1)
                            )
                        }
                    }
                }
            }

            // ── 4. LOGOUT BUTTON ────────────────────────────────────────────────
            item {
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("DÉCONNEXION", color = DangerColor, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(60.dp))
            }
        } else {
            // ═════════════════════════════════════════════════════════════════════
            // TAB 2: PARAMÈTRES AVANCÉS (IA, VOIP, ADB, RÉSEAU & RGPD)
            // ═════════════════════════════════════════════════════════════════════

            // ── 1. AI & LLM PROVIDER API KEYS & ENGINE CONFIGURATION ────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        val aiPrefs = remember { context.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE) }
                        var groqApiKey by remember { mutableStateOf(aiPrefs.getString("groq_api_key", "") ?: "") }
                        var deepgramApiKey by remember { mutableStateOf(aiPrefs.getString("deepgram_api_key", "") ?: "") }
                        var openAiApiKey by remember { mutableStateOf(aiPrefs.getString("openai_api_key", "") ?: "") }
                        var selectedWhisperModel by remember { mutableStateOf(aiPrefs.getString("whisper_model", "faster-whisper-small") ?: "faster-whisper-small") }
                        var selectedLlmModel by remember { mutableStateOf(aiPrefs.getString("llm_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile") }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("MOTEURS IA & CLÉS D'ACCÈS API", color = Text3, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.5.sp)
                                Text("Groq, Deepgram STT, OpenAI & Whisper", color = Text2, fontSize = 11.5.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(AccentDim)
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Text("Pipeline Actif", color = AccentText, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Renseignez vos clés d'API personnelles pour activer la transcription vocale haute fidélité et les résumés automatiques :",
                            color = Text3,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = groqApiKey,
                            onValueChange = { groqApiKey = it },
                            label = { Text("Clé API Groq (gsk_...)", color = Text3, fontSize = 11.5.sp) },
                            placeholder = { Text("gsk_...", color = Text3) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BorderStrong,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Text1,
                                unfocusedTextColor = Text1
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = deepgramApiKey,
                            onValueChange = { deepgramApiKey = it },
                            label = { Text("Clé API Deepgram (Nova-2 Speech-to-Text)", color = Text3, fontSize = 11.5.sp) },
                            placeholder = { Text("Token Deepgram streaming audio", color = Text3) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BorderStrong,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Text1,
                                unfocusedTextColor = Text1
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = openAiApiKey,
                            onValueChange = { openAiApiKey = it },
                            label = { Text("Clé API OpenAI / Claude / Gemini", color = Text3, fontSize = 11.5.sp) },
                            placeholder = { Text("sk-...", color = Text3) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BorderStrong,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Text1,
                                unfocusedTextColor = Text1
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Modèle Transcription Vocale (STT) :", color = Text2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        val whisperModels = listOf(
                            "faster-whisper-small" to "Whisper Small",
                            "faster-whisper-medium" to "Whisper Med",
                            "faster-whisper-large-v3" to "Large-v3 HD"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            whisperModels.forEach { (mId, mLabel) ->
                                val isSelected = selectedWhisperModel == mId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Text1 else Surface2)
                                        .border(1.dp, if (isSelected) Text1 else BorderColor, RoundedCornerShape(8.dp))
                                        .clickable { selectedWhisperModel = mId }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mLabel,
                                        color = if (isSelected) BgColor else Text2,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Moteur LLM Synthèse & RDV :", color = Text2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(6.dp))
                        val llmModels = listOf(
                            "llama-3.3-70b-versatile" to "Llama 3.3",
                            "mixtral-8x7b-32768" to "Mixtral",
                            "gpt-4o" to "GPT-4o"
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            llmModels.forEach { (mId, mLabel) ->
                                val isSelected = selectedLlmModel == mId
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Text1 else Surface2)
                                        .border(1.dp, if (isSelected) Text1 else BorderColor, RoundedCornerShape(8.dp))
                                        .clickable { selectedLlmModel = mId }
                                        .padding(vertical = 7.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = mLabel,
                                        color = if (isSelected) BgColor else Text2,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                aiPrefs.edit()
                                    .putString("groq_api_key", groqApiKey.trim())
                                    .putString("deepgram_api_key", deepgramApiKey.trim())
                                    .putString("openai_api_key", openAiApiKey.trim())
                                    .putString("whisper_model", selectedWhisperModel)
                                    .putString("llm_model", selectedLlmModel)
                                    .apply()
                                Toast.makeText(context, "Clés API IA enregistrées avec succès", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Text1),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 11.dp)
                        ) {
                            Text("Enregistrer les Clés API IA", color = BgColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // ── 2. CLOUD TELEPHONY & VOIP PROVIDERS (MULTI-FOURNISSEUR) ────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        val voipPrefs = remember { context.getSharedPreferences("twilio_settings", android.content.Context.MODE_PRIVATE) }
                        var voipEnabled by remember { mutableStateOf(voipPrefs.getBoolean("twilio_enabled", false)) }
                        var selectedProvider by remember { mutableStateOf(voipPrefs.getString("voip_provider", "TWILIO") ?: "TWILIO") }
                        var accountSid by remember { mutableStateOf(voipPrefs.getString("twilio_account_sid", "") ?: "") }
                        var authToken by remember { mutableStateOf(voipPrefs.getString("twilio_auth_token", "") ?: "") }
                        var voipNumber by remember { mutableStateOf(voipPrefs.getString("twilio_phone_number", "") ?: "") }
                        var twimlAppSid by remember { mutableStateOf(voipPrefs.getString("twilio_twiml_app_sid", "") ?: "") }

                        val providers = listOf(
                            "TWILIO" to "Twilio",
                            "TELNYX" to "Telnyx",
                            "PLIVO" to "Plivo",
                            "VONAGE" to "Vonage",
                            "SIP_PBX" to "SIP Trunk / PBX"
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("CLOUD TELEPHONY & MULTI-VOIP", color = Text3, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.5.sp)
                                Text("Twilio, Telnyx, Plivo, Vonage & SIP Trunk", color = Text2, fontSize = 11.5.sp)
                            }
                            Switch(
                                checked = voipEnabled,
                                onCheckedChange = {
                                    voipEnabled = it
                                    voipPrefs.edit().putBoolean("twilio_enabled", it).apply()
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = BgColor, checkedTrackColor = Text1)
                            )
                        }

                        if (voipEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Sélectionnez votre opérateur VoIP Cloud pour l'enregistrement 2-Way HD et la transcription automatique :",
                                color = Text3,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            // Provider selector chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                providers.take(3).forEach { (id, label) ->
                                    val isSelected = selectedProvider == id
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) Text1 else Surface2)
                                        .border(1.dp, if (isSelected) Text1 else BorderColor, RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedProvider = id
                                            voipPrefs.edit().putString("voip_provider", id).apply()
                                        }
                                        .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) BgColor else Text2,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                providers.drop(3).forEach { (id, label) ->
                                    val isSelected = selectedProvider == id
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) Text1 else Surface2)
                                            .border(1.dp, if (isSelected) Text1 else BorderColor, RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedProvider = id
                                                voipPrefs.edit().putString("voip_provider", id).apply()
                                            }
                                            .padding(vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) BgColor else Text2,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = accountSid,
                                onValueChange = { accountSid = it },
                                label = {
                                    Text(
                                        when (selectedProvider) {
                                            "TELNYX" -> "API Key / Connection ID"
                                            "PLIVO" -> "Auth ID"
                                            "VONAGE" -> "API Key / Application ID"
                                            "SIP_PBX" -> "SIP Server Host (sip.company.com)"
                                            else -> "Account SID (ACxxxxxxxx...)"
                                        },
                                        color = Text3,
                                        fontSize = 11.5.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BorderStrong,
                                    unfocusedBorderColor = BorderColor,
                                    focusedTextColor = Text1,
                                    unfocusedTextColor = Text1
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = authToken,
                                onValueChange = { authToken = it },
                                label = {
                                    Text(
                                        when (selectedProvider) {
                                            "TELNYX" -> "Telnyx Secret Key"
                                            "PLIVO" -> "Auth Token"
                                            "VONAGE" -> "API Secret / Private Key"
                                            "SIP_PBX" -> "SIP Password / Secret"
                                            else -> "Auth Token"
                                        },
                                        color = Text3,
                                        fontSize = 11.5.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BorderStrong,
                                    unfocusedBorderColor = BorderColor,
                                    focusedTextColor = Text1,
                                    unfocusedTextColor = Text1
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = voipNumber,
                                onValueChange = { voipNumber = it },
                                label = { Text("Numéro Virtuel / Passerelle (+33...)", color = Text3, fontSize = 11.5.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BorderStrong,
                                    unfocusedBorderColor = BorderColor,
                                    focusedTextColor = Text1,
                                    unfocusedTextColor = Text1
                                )
                            )

                            if (selectedProvider == "TWILIO" || selectedProvider == "TELNYX") {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = twimlAppSid,
                                    onValueChange = { twimlAppSid = it },
                                    label = { Text("App SID / TeXML App ID", color = Text3, fontSize = 11.5.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = BorderStrong,
                                        unfocusedBorderColor = BorderColor,
                                        focusedTextColor = Text1,
                                        unfocusedTextColor = Text1
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    voipPrefs.edit()
                                        .putBoolean("twilio_enabled", true)
                                        .putString("voip_provider", selectedProvider)
                                        .putString("twilio_account_sid", accountSid.trim())
                                        .putString("twilio_auth_token", authToken.trim())
                                        .putString("twilio_phone_number", voipNumber.trim())
                                        .putString("twilio_twiml_app_sid", twimlAppSid.trim())
                                        .apply()
                                    Toast.makeText(context, "Configuration $selectedProvider enregistrée", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Text1),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 11.dp)
                            ) {
                                Text("Enregistrer les identifiants $selectedProvider", color = BgColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // ── 3. SHIZUKU STATUS CARD ──────────────────────────────────────────
            item {
                val isShizukuAvailable = remember { shizukuManager?.isShizukuAvailable() == true }
                val hasShizukuPerm = remember { shizukuManager?.hasShizukuPermission() == true }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("SHIZUKU API — PERMISSIONS ÉLEVÉES ADB", color = Text3, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (isShizukuAvailable && hasShizukuPerm) SuccessColor else DangerColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isShizukuAvailable && hasShizukuPerm -> "Shizuku Connecté (Permissions ADB Actives)"
                                    isShizukuAvailable -> "Shizuku Détecté (Permission requise)"
                                    else -> "Shizuku Non Détecté / Service arrêté"
                                },
                                color = Text1,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isShizukuAvailable && !hasShizukuPerm) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { shizukuManager?.requestShizukuPermission() },
                                colors = ButtonDefaults.buttonColors(containerColor = Text1),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text("Autoriser Shizuku", color = BgColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── 4. SERVER CONFIG CARD ───────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("CONFIGURATION SERVEUR BACKEND", color = Text3, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        var customUrlText by remember { 
                            mutableStateOf(
                                context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE)
                                    .getString("custom_base_url", "http://127.0.0.1:8000") ?: "http://127.0.0.1:8000"
                            ) 
                        }
                        OutlinedTextField(
                            value = customUrlText,
                            onValueChange = { customUrlText = it },
                            label = { Text("URL du serveur (ex: http://127.0.0.1:8000)", color = Text3, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BorderStrong,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Text1,
                                unfocusedTextColor = Text1
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
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
                                colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text("USB Local", color = Text1, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Button(
                                onClick = {
                                    customUrlText = "http://192.168.1.12:8000"
                                    context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE)
                                        .edit()
                                        .putString("custom_base_url", "http://192.168.1.12:8000")
                                        .apply()
                                    Toast.makeText(context, "Mode Wi-Fi sélectionné (192.168.1.12:8000)", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text("Wi-Fi Réseau", color = AccentText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                colors = ButtonDefaults.buttonColors(containerColor = Text1),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text("Enregistrer", color = BgColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                                    Toast.makeText(context, "Backend connecté (HTTP $code)", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Réponse inattendue: HTTP $code", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                Toast.makeText(context, "Impossible de joindre le serveur: ${e.message}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Text2, modifier = Modifier.size(13.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tester", color = Text2, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // ── 5. RGPD & GESTION DES DONNÉES ──────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Text("RGPD & PROTECTION DES DONNÉES", color = Text3, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Conformément au RGPD (Règlement Général sur la Protection des Données), vous disposez d'un droit d'accès, d'export et d'effacement complet de vos données.",
                            color = Text2, fontSize = 11.5.sp, lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))

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
                            colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Text1, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Exporter mes données (Art. 15 RGPD)", color = Text1, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Button 2: Delete voice recordings
                        OutlinedButton(
                            onClick = { showDeleteVoiceDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = WarnColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = WarnColor, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Effacer uniquement les enregistrements", color = WarnColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Button 3: Delete full account (Art. 17)
                        Button(
                            onClick = { showDeleteAccountDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Text1, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Supprimer mon compte & mes données", color = Text1, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            // ── 6. LOGOUT BUTTON IN ADVANCED TAB ────────────────────────────────
            item {
                Button(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("DÉCONNEXION", color = DangerColor, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    // Dialog for Voice Delete
    if (showDeleteVoiceDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteVoiceDialog = false },
            title = { Text("Supprimer les enregistrements vocaux", color = Text1, fontWeight = FontWeight.Bold) },
            text = { Text("Voulez-vous supprimer les enregistrements audio et transcriptions ? Les fichiers locaux et distants seront effacés.", color = Text2) },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
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
                    colors = ButtonDefaults.buttonColors(containerColor = WarnColor)
                ) { Text("Confirmer", color = BgColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteVoiceDialog = false }) { Text("Annuler", color = Text3) }
            },
            containerColor = Surface1
        )
    }

    // Dialog for Full Account Deletion (Art. 17)
    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = DangerColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Suppression définitive du compte", color = DangerColor, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Conformément à l'Art. 17 du RGPD (Droit à l'oubli), votre compte, tous vos contacts, appels, enregistrements et résumés seront définitivement effacés. Cette action est irréversible.",
                    color = Text2
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
                    colors = ButtonDefaults.buttonColors(containerColor = DangerColor)
                ) { Text("Supprimer Définitivement", color = Text1, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) { Text("Annuler", color = Text3) }
            },
            containerColor = Surface1
        )
    }

    // Dialog for Edit Profile Information
    if (showEditProfileDialog) {
        var editFirst by remember { mutableStateOf(profileFirstName) }
        var editLast by remember { mutableStateOf(profileLastName) }
        var editMail by remember { mutableStateOf(profileEmail) }
        var editPhone by remember { mutableStateOf(profileNumber) }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Modifier mes informations", color = Text1, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editFirst,
                        onValueChange = { editFirst = it },
                        label = { Text("Prénom", color = Text3) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderStrong,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Text1,
                            unfocusedTextColor = Text1
                        )
                    )
                    OutlinedTextField(
                        value = editLast,
                        onValueChange = { editLast = it },
                        label = { Text("Nom", color = Text3) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderStrong,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Text1,
                            unfocusedTextColor = Text1
                        )
                    )
                    OutlinedTextField(
                        value = editMail,
                        onValueChange = { editMail = it },
                        label = { Text("Email", color = Text3) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderStrong,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Text1,
                            unfocusedTextColor = Text1
                        )
                    )
                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text("Numéro de téléphone", color = Text3) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderStrong,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Text1,
                            unfocusedTextColor = Text1
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            voipRepository.updateProfile(
                                firstName = editFirst.trim(),
                                lastName = editLast.trim(),
                                email = editMail.trim(),
                                number = editPhone.trim()
                            ).onSuccess {
                                profileFirstName = it.firstName
                                profileLastName = it.lastName
                                profileEmail = it.email
                                profileNumber = it.number ?: ""
                                Toast.makeText(context, "Profil mis à jour", Toast.LENGTH_SHORT).show()
                            }.onFailure {
                                Toast.makeText(context, "Erreur mise à jour: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showEditProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Text1)
                ) { Text("Enregistrer", color = BgColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) { Text("Annuler", color = Text3) }
            },
            containerColor = Surface1
        )
    }

    // Dialog for Change Password
    if (showChangePasswordDialog) {
        var oldP by remember { mutableStateOf("") }
        var newP by remember { mutableStateOf("") }
        var confirmP by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showChangePasswordDialog = false },
            title = { Text("Changer mon mot de passe", color = Text1, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = oldP,
                        onValueChange = { oldP = it },
                        label = { Text("Ancien mot de passe", color = Text3) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderStrong,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Text1,
                            unfocusedTextColor = Text1
                        )
                    )
                    OutlinedTextField(
                        value = newP,
                        onValueChange = { newP = it },
                        label = { Text("Nouveau mot de passe", color = Text3) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderStrong,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Text1,
                            unfocusedTextColor = Text1
                        )
                    )
                    OutlinedTextField(
                        value = confirmP,
                        onValueChange = { confirmP = it },
                        label = { Text("Confirmer le mot de passe", color = Text3) },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BorderStrong,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = Text1,
                            unfocusedTextColor = Text1
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newP.isBlank()) {
                            Toast.makeText(context, "Le mot de passe ne peut pas être vide", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newP != confirmP) {
                            Toast.makeText(context, "Les mots de passe ne correspondent pas", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            voipRepository.changePassword(oldP, newP)
                                .onSuccess {
                                    Toast.makeText(context, "Mot de passe modifié avec succès", Toast.LENGTH_SHORT).show()
                                    showChangePasswordDialog = false
                                }
                                .onFailure {
                                    Toast.makeText(context, "Erreur : ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Text1)
                ) { Text("Changer", color = BgColor, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showChangePasswordDialog = false }) { Text("Annuler", color = Text3) }
            },
            containerColor = Surface1
        )
    }
}
