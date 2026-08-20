package com.example.appcall.presentation.summary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
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

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    callId: String,
    viewModel: SummaryViewModel,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val netPrefs = remember { context.getSharedPreferences("network_settings", Context.MODE_PRIVATE) }
    val appLanguageCode = remember { netPrefs.getString("app_language", "en") ?: "en" }
    val strings = getAppStrings(appLanguageCode)

    val uiState by viewModel.uiState.collectAsState()
    val summaryText by viewModel.summaryText.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val isLowConfidence by viewModel.isLowConfidence.collectAsState()
    val aiStatus by viewModel.aiStatus.collectAsState()
    val transcript by viewModel.transcript.collectAsState()

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPositionMs by remember { mutableStateOf(0) }
    var totalDurationMs by remember { mutableStateOf(0) }
    var selectedSpeed by remember { mutableStateOf("1×") }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var voiceCommandText by remember { mutableStateOf("") }

    fun findCallAudioFile(targetCallId: String): File? {
        val targetDirs = listOf(
            File(context.filesDir, "recordings"),
            File(context.filesDir, "recordings_native"),
            context.getExternalFilesDir(null)?.let { File(it, "recordings") },
            context.cacheDir
        ).filterNotNull()

        val allFiles = mutableListOf<File>()
        targetDirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.filter {
                    it.isFile && (it.name.endsWith(".wav") || it.name.endsWith(".mp4") || it.name.endsWith(".m4a")) && it.length() > 0
                }?.let { allFiles.addAll(it) }
            }
        }
        val targetPrefix = if (targetCallId.length >= 8) targetCallId.take(8) else targetCallId
        return allFiles.firstOrNull { it.name.contains(targetPrefix, ignoreCase = true) }
            ?: allFiles.firstOrNull { it.name.contains(targetCallId, ignoreCase = true) }
            ?: allFiles.maxByOrNull { it.lastModified() }
    }

    val matchedAudioFile = remember(callId) { findCallAudioFile(callId) }

    fun formatDuration(ms: Int): String {
        val totalSec = ms / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.getDefault(), "%d:%02d", m, s)
    }

    // Live playback tracker
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        currentPositionMs = player.currentPosition
                        if (player.duration > 0) totalDurationMs = player.duration
                    }
                } catch (e: Exception) {}
            }
            delay(150)
        }
    }

    // Speed adjustment
    LaunchedEffect(selectedSpeed) {
        mediaPlayer?.let { player ->
            try {
                val speedFloat = when (selectedSpeed) {
                    "1.5×" -> 1.5f
                    "2×" -> 2.0f
                    else -> 1.0f
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    player.playbackParams = player.playbackParams.setSpeed(speedFloat)
                }
            } catch (e: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (e: Exception) {}
        }
    }

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
                    text = strings.navCalls,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Text2, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = strings.retry, color = Text2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
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
                            Text(text = strings.loadingAnalysis, color = Text3, fontSize = 13.sp)
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
                                Text(strings.retry, color = Text1)
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
                        // Low confidence warning banner
                        if (isLowConfidence) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(WarnDim)
                                        .border(1.dp, WarnColor, RoundedCornerShape(10.dp))
                                        .padding(12.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = WarnColor, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(text = strings.lowConfidenceWarning, color = WarnColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(text = strings.lowConfidenceSubtitle, color = Text2, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // 1. Contact Card Header
                        item {
                            val localHistoryItem = remember(callId) {
                                try {
                                    val db = com.example.appcall.data.local.AppLocalDatabase(context)
                                    db.getCallHistory().firstOrNull { it.id == callId }
                                } catch (_: Exception) { null }
                            }

                            val contactName = summary.contactName?.takeIf { it.isNotBlank() && it != "Appel Enregistré" }
                                ?: summary.appointment?.contactName?.takeIf { it.isNotBlank() }
                                ?: localHistoryItem?.contactName?.takeIf { it.isNotBlank() && !it.startsWith("+") && !it.all { c -> c.isDigit() } }
                                ?: "Appel Enregistré"

                            val phoneNumber = summary.phoneNumber?.takeIf { it.isNotBlank() }
                                ?: summary.appointment?.phoneNumber?.takeIf { it.isNotBlank() }
                                ?: localHistoryItem?.contactName?.takeIf { it.startsWith("+") || it.filter { c -> c.isDigit() }.length >= 6 }
                                ?: localHistoryItem?.contactId?.takeIf { it.startsWith("+") || it.filter { c -> c.isDigit() }.length >= 6 }
                                ?: ""

                            val initials = contactName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("").uppercase()
                            val confidencePercent = ((summary.confidenceScore ?: transcript?.confidenceScore ?: 90.0)).toInt().coerceIn(0, 100)

                            val rawSentiment = (summary.sentiment ?: "NEUTRAL").uppercase()
                            val (sentimentText, sentimentColor, sentimentBg) = when {
                                rawSentiment in listOf("HOSTILE", "MENACE", "THREAT", "CONFLICT") -> Triple("Menace / Conflit", Color(0xFFEF4444), Color(0x33EF4444))
                                rawSentiment in listOf("NEGATIVE", "NEGATIF", "NÉGATIF") -> Triple("Négatif", WarnColor, WarnDim)
                                rawSentiment in listOf("POSITIVE", "POSITIF") -> Triple("Positif", SuccessColor, SuccessDim)
                                else -> Triple("Neutre", Text2, Surface2)
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface1)
                                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                    .padding(14.dp)
                            ) {
                                Column {
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
                                            if (initials.isNotBlank()) {
                                                Text(
                                                    text = initials,
                                                    color = Text1,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                            } else {
                                                Icon(Icons.Default.Call, contentDescription = null, tint = Text1, modifier = Modifier.size(18.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = contactName,
                                                color = Text1,
                                                fontSize = 15.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val subTitle = if (phoneNumber.isNotBlank()) "$phoneNumber · Enregistré" else "Audio Enregistré"
                                            Text(
                                                text = subTitle,
                                                color = Text3,
                                                fontSize = 11.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Surface2)
                                                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                                .clickable {
                                                    if (phoneNumber.isNotBlank()) {
                                                        try {
                                                            val cleanPhone = phoneNumber.filter { it == '+' || it.isDigit() }
                                                            val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                                                                data = android.net.Uri.parse("tel:$cleanPhone")
                                                            }
                                                            context.startActivity(dialIntent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Impossible de composer le numéro", Toast.LENGTH_SHORT).show()
                                                        }
                                                    } else {
                                                        try {
                                                            val dialIntent = Intent(Intent.ACTION_DIAL)
                                                            context.startActivity(dialIntent)
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Numéro de téléphone non disponible", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Call, contentDescription = "Composer", tint = Text2, modifier = Modifier.size(14.dp))
                                        }
                                    }

                                    // AI Sentiment, Intent & Dynamic Hashtag Chips Strip
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 10.dp)
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Surface2)
                                                .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.5.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(Text3))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(text = "Audio HD", color = Text2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        // Dynamic Sentiment Pill
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(sentimentBg)
                                                .border(1.dp, sentimentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.5.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(sentimentColor))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(text = sentimentText, color = sentimentColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Dynamic Confidence Pill
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(AccentDim)
                                                .border(1.dp, AccentColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 3.5.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(AccentColor))
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(text = "IA · $confidencePercent%", color = AccentText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        // Dynamic Intent Pill
                                        summary.intent?.takeIf { it.isNotBlank() }?.let { intentStr ->
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Surface2)
                                                    .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 3.5.dp)
                                            ) {
                                                Text(text = intentStr, color = Text2, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        // Dynamic Hashtags
                                        if (summary.tags.isNotEmpty()) {
                                            summary.tags.forEach { tag ->
                                                val cleanTag = if (tag.startsWith("#")) tag else "#$tag"
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Surface2)
                                                        .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                                                        .padding(horizontal = 8.dp, vertical = 3.5.dp)
                                                ) {
                                                    Text(text = cleanTag, color = Text3, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
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
                                                .background(if (isPlaying) AccentColor else Text1)
                                                .clickable {
                                                    try {
                                                        if (isPlaying) {
                                                            mediaPlayer?.pause()
                                                            isPlaying = false
                                                        } else {
                                                            if (mediaPlayer == null) {
                                                                if (matchedAudioFile == null || matchedAudioFile.length() <= 512) {
                                                                    Toast.makeText(context, "Enregistrement audio introuvable ou vide", Toast.LENGTH_SHORT).show()
                                                                    return@clickable
                                                                }

                                                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                                                @Suppress("DEPRECATION")
                                                                audioManager.requestAudioFocus(
                                                                    null,
                                                                    AudioManager.STREAM_MUSIC,
                                                                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                                                                )

                                                                val player = MediaPlayer()
                                                                val attrib = AudioAttributes.Builder()
                                                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                                    .build()
                                                                player.setAudioAttributes(attrib)
                                                                player.setDataSource(matchedAudioFile.absolutePath)
                                                                player.setVolume(1.0f, 1.0f)
                                                                player.prepare()
                                                                totalDurationMs = player.duration

                                                                val speedFloat = when (selectedSpeed) {
                                                                    "1.5×" -> 1.5f
                                                                    "2×" -> 2.0f
                                                                    else -> 1.0f
                                                                }
                                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                                    player.playbackParams = player.playbackParams.setSpeed(speedFloat)
                                                                }

                                                                player.start()
                                                                player.setOnCompletionListener {
                                                                    isPlaying = false
                                                                    currentPositionMs = 0
                                                                    mediaPlayer?.release()
                                                                    mediaPlayer = null
                                                                }
                                                                mediaPlayer = player
                                                                isPlaying = true
                                                            } else {
                                                                mediaPlayer?.start()
                                                                isPlaying = true
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        Toast.makeText(context, "Erreur lecture: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (isPlaying) "❚❚" else "▶",
                                                color = if (isPlaying) Text1 else BgColor,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // Scrub wave with dynamic animated position & tap to seek
                                        val heights = listOf(7, 12, 5, 15, 9, 13, 6, 10, 8, 13, 5, 9, 7, 11, 8, 14, 6, 10, 9, 12)
                                        val progress = if (totalDurationMs > 0) currentPositionMs.toFloat() / totalDurationMs else 0f
                                        val activeBarIndex = (progress * heights.size).toInt()

                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(24.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
                                        ) {
                                            heights.forEachIndexed { i, h ->
                                                val barColor = if (i <= activeBarIndex) AccentColor else Surface2
                                                Box(
                                                    modifier = Modifier
                                                        .width(2.dp)
                                                        .height(h.dp)
                                                        .clip(RoundedCornerShape(1.dp))
                                                        .background(barColor)
                                                        .clickable {
                                                            if (totalDurationMs > 0 && mediaPlayer != null) {
                                                                val seekTarget = ((i.toFloat() / heights.size) * totalDurationMs).toInt()
                                                                mediaPlayer?.seekTo(seekTarget)
                                                                currentPositionMs = seekTarget
                                                            }
                                                        }
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
                                        Text(
                                            text = formatDuration(currentPositionMs),
                                            color = if (isPlaying) AccentText else Text3,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = if (totalDurationMs > 0) formatDuration(totalDurationMs) else if (matchedAudioFile != null) "Audio prêt" else "Aucun audio",
                                            color = Text3,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
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
                                            // Share / Export Audio
                                            Box(
                                                modifier = Modifier
                                                    .size(27.dp)
                                                    .clip(RoundedCornerShape(7.dp))
                                                    .background(Surface2)
                                                    .border(1.dp, BorderColor, RoundedCornerShape(7.dp))
                                                    .clickable {
                                                        if (matchedAudioFile != null && matchedAudioFile.exists()) {
                                                            try {
                                                                val uri = FileProvider.getUriForFile(
                                                                    context,
                                                                    "${context.packageName}.fileprovider",
                                                                    matchedAudioFile
                                                                )
                                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                    type = "audio/*"
                                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                }
                                                                context.startActivity(Intent.createChooser(shareIntent, "Partager l'audio de l'appel"))
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Erreur partage: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "Fichier audio non disponible", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = "⬇", color = Text2, fontSize = 11.sp)
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
                                            text = strings.callSummary.uppercase(),
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
                                                 contentDescription = strings.editSummary,
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
                                             Text(strings.save, color = BgColor, fontWeight = FontWeight.Bold)
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
                                             Text(text = strings.loadingAnalysis, color = Text3, fontSize = 13.sp)
                                         }
                                     }
                                 }
                             }
                         }

                         // 4. Detected Appointment Card (or Add Appointment Card)
                         if (summary.appointment != null && summary.appointment.status != "DISMISSED") {
                             val appointment = summary.appointment
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
                                                 text = strings.detectedAppointment.uppercase(),
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
                                                     text = if (isConfirmed) "✓" else "!",
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
                                                         Text(text = "TIME", color = Text3, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
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
                                                         Text(text = "TITLE", color = Text3, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                                         Text(text = appointment.title ?: "CRM Meeting", color = Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                                                     }
                                                 }
                                                 Box(
                                                     modifier = Modifier
                                                         .weight(1f)
                                                         .background(Surface2)
                                                         .padding(9.dp, 11.dp)
                                                 ) {
                                                     Column {
                                                         Text(text = "CONTACT", color = Text3, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
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
                                                     text = strings.confirmAppointment,
                                                     color = BgColor,
                                                     fontWeight = FontWeight.Bold,
                                                     fontSize = 12.5.sp
                                                 )
                                             }

                                             Box(
                                                 modifier = Modifier
                                                     .fillMaxWidth()
                                                     .clip(RoundedCornerShape(8.dp))
                                                     .background(Surface2)
                                                     .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                                                     .clickable {
                                                         try {
                                                             val calIntent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                                                 data = android.provider.CalendarContract.Events.CONTENT_URI
                                                                 putExtra(android.provider.CalendarContract.Events.TITLE, appointment.title ?: "CRM Meeting")
                                                                 putExtra(android.provider.CalendarContract.Events.DESCRIPTION, "Call with ${appointment.contactName ?: "Contact"}\nSummary: $summaryText")
                                                                 try {
                                                                     val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US)
                                                                     val parsed = format.parse(appointment.scheduledAt.take(16))
                                                                     if (parsed != null) {
                                                                         putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, parsed.time)
                                                                         putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, parsed.time + 3600000)
                                                                     }
                                                                 } catch (_: Exception) {}
                                                             }
                                                             context.startActivity(calIntent)
                                                         } catch (e: Exception) {
                                                             android.widget.Toast.makeText(context, "Calendar: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                                         }
                                                     }
                                                     .padding(vertical = 10.dp),
                                                 contentAlignment = Alignment.Center
                                             ) {
                                                 Text(text = "📅 Add to Calendar", color = AccentText, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
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
                                                 Text(text = strings.editSummary, color = Text2, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                                             }

                                             Box(
                                                 modifier = Modifier
                                                     .fillMaxWidth()
                                                     .clickable { viewModel.dismissAppointment() }
                                                     .padding(vertical = 6.dp),
                                                 contentAlignment = Alignment.Center
                                             ) {
                                                 Text(text = strings.dismissAppointment, color = Text3, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                             }
                                         }
                                     }
                                 }
                             }
                         } else {
                             // No appointment detected -> Allow manual creation
                             item {
                                 Box(
                                     modifier = Modifier
                                         .fillMaxWidth()
                                         .clip(RoundedCornerShape(10.dp))
                                         .background(Surface1)
                                         .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                         .padding(14.dp)
                                 ) {
                                     Row(
                                         modifier = Modifier.fillMaxWidth(),
                                         horizontalArrangement = Arrangement.SpaceBetween,
                                         verticalAlignment = Alignment.CenterVertically
                                     ) {
                                         Column(modifier = Modifier.weight(1f)) {
                                             Text(strings.noAppointments, color = Text1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                             Text(strings.addAppointment, color = Text3, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                                         }
                                         Box(
                                             modifier = Modifier
                                                 .clip(RoundedCornerShape(7.dp))
                                                 .background(Surface2)
                                                 .border(1.dp, BorderColor, RoundedCornerShape(7.dp))
                                                 .clickable { showVoiceDialog = true }
                                                 .padding(horizontal = 10.dp, vertical = 6.dp)
                                         ) {
                                             Row(verticalAlignment = Alignment.CenterVertically) {
                                                 Icon(Icons.Default.Add, contentDescription = null, tint = Text1, modifier = Modifier.size(13.dp))
                                                 Spacer(modifier = Modifier.width(4.dp))
                                                 Text(strings.addAppointment, color = Text1, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                             }
                                         }
                                     }
                                 }
                             }
                         }

                         // 5. Validation Button at the bottom
                         item {
                             val isValidated = summary.status == "VALIDATED"
                             Box(
                                 modifier = Modifier
                                     .fillMaxWidth()
                                     .clip(RoundedCornerShape(10.dp))
                                     .background(if (isValidated) SuccessDim else Text1)
                                     .clickable(enabled = !isValidated) { viewModel.validateSummary() }
                                     .padding(vertical = 14.dp),
                                 contentAlignment = Alignment.Center
                             ) {
                                 Row(verticalAlignment = Alignment.CenterVertically) {
                                     if (isValidated) {
                                         Icon(Icons.Default.Check, contentDescription = null, tint = SuccessColor, modifier = Modifier.size(16.dp))
                                         Spacer(modifier = Modifier.width(6.dp))
                                     }
                                     Text(
                                         text = if (isValidated) "✓ ${strings.validateSummary}" else strings.validateSummary,
                                         color = if (isValidated) SuccessColor else BgColor,
                                         fontWeight = FontWeight.Bold,
                                         fontSize = 13.5.sp
                                     )
                                 }
                             }
                         }

                         // 6. Transcript Block
                         item {
                             Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                 Row(
                                     modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                     horizontalArrangement = Arrangement.SpaceBetween,
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     Text(
                                         text = strings.audioTranscript.uppercase(),
                                         color = Text2,
                                         fontSize = 11.5.sp,
                                         fontWeight = FontWeight.Bold,
                                         letterSpacing = 0.5.sp
                                     )
                                     IconButton(
                                         onClick = { viewModel.refreshTranscript() },
                                         modifier = Modifier.size(24.dp)
                                     ) {
                                         Icon(
                                             imageVector = Icons.Default.Refresh,
                                             contentDescription = "Sync Transcript",
                                             tint = Text3,
                                             modifier = Modifier.size(15.dp)
                                         )
                                     }
                                 }

                                 val segments = transcript?.speakerSegments
                                 val raw = transcript?.rawText?.trim()

                                 if (!segments.isNullOrEmpty()) {
                                     Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                         segments.forEach { seg ->
                                             val isAgent = seg.speaker == "agent" || seg.speaker == "SPEAKER_00"
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
                                                             text = if (isAgent) "Agent · ${String.format(Locale.getDefault(), "%.1f", seg.start)}s" else "${String.format(Locale.getDefault(), "%.1f", seg.start)}s · Contact",
                                                             color = Text3,
                                                             fontSize = 9.5.sp,
                                                             fontFamily = FontFamily.Monospace
                                                         )
                                                     }
                                                 }
                                             }
                                         }
                                     }
                                 } else if (!raw.isNullOrBlank() && raw != "...") {
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
                                     val summaryFallback = summary.summaryText.takeIf { !it.startsWith("Traitement IA") }?.trim()
                                     if (!summaryFallback.isNullOrBlank()) {
                                         Box(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .clip(RoundedCornerShape(10.dp))
                                                 .background(Surface1)
                                                 .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                                                 .padding(14.dp)
                                         ) {
                                             Column {
                                                 Text(text = summaryFallback, color = Text1, fontSize = 13.sp, lineHeight = 19.sp)
                                                 Spacer(modifier = Modifier.height(8.dp))
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween,
                                                     verticalAlignment = Alignment.CenterVertically
                                                 ) {
                                                     Text(
                                                         text = "Contexte extrait de l'appel",
                                                         color = Text3,
                                                         fontSize = 10.5.sp,
                                                         fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                                     )
                                                     TextButton(onClick = { viewModel.refreshTranscript() }) {
                                                         Text("Recharger l'audio", color = AccentText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                     }
                                                 }
                                             }
                                         }
                                     } else {
                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             verticalAlignment = Alignment.CenterVertically,
                                             horizontalArrangement = Arrangement.SpaceBetween
                                         ) {
                                             Text(text = strings.noSpeechDetected, color = Text3, fontSize = 12.5.sp)
                                             TextButton(onClick = { viewModel.refreshTranscript() }) {
                                                 Text("Recharger", color = AccentText, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                             }
                                         }
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
                title = { Text(strings.editSummary, color = Text1, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(strings.taskTitlePlaceholder, color = Text3, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                        OutlinedTextField(
                            value = voiceCommandText,
                            onValueChange = { voiceCommandText = it },
                            placeholder = { Text(strings.taskTitlePlaceholder, color = Text3) },
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
                        Text(strings.validate, color = BgColor, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showVoiceDialog = false }) {
                        Text(strings.close, color = Text3)
                    }
                }
            )
        }
    }
}


