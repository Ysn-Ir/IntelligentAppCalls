package com.example.appcall.presentation.dashboard

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.appcall.data.local.AppLocalDatabase
import com.example.appcall.presentation.theme.NeonTeal
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilesSection(localDatabase: AppLocalDatabase) {
    val context = LocalContext.current
    val netPrefs = remember { context.getSharedPreferences("network_settings", Context.MODE_PRIVATE) }
    val appLanguageCode = remember { netPrefs.getString("app_language", "en") ?: "en" }
    val strings = com.example.appcall.presentation.theme.getAppStrings(appLanguageCode)

    fun getRecordingsList(): List<File> {
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
        return allFiles.distinctBy { it.name }.sortedByDescending { it.lastModified() }
    }

    var recordingFiles by remember { mutableStateOf(getRecordingsList()) }
    var currentlyPlayingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val isBiometricLockEnabled = remember { netPrefs.getBoolean("biometric_vault_lock", false) }
    var isUnlocked by remember { mutableStateOf(!isBiometricLockEnabled) }

    LaunchedEffect(Unit) {
        recordingFiles = getRecordingsList()
        if (isBiometricLockEnabled && !isUnlocked) {
            (context as? com.example.appcall.MainActivity)?.authenticateWithBiometrics(
                title = "Unlock Audio Vault",
                subtitle = "Scan your fingerprint or face to view recordings",
                onSuccess = { isUnlocked = true },
                onError = { /* Keep locked */ }
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    if (!isUnlocked) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color(0x332DD4BF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔒", fontSize = 32.sp)
                }
                Text(
                    text = "Audio Vault Locked",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Biometric authentication is required to access your audio recordings.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        (context as? com.example.appcall.MainActivity)?.authenticateWithBiometrics(
                            title = "Unlock Audio Vault",
                            subtitle = "Scan your fingerprint or face to view recordings",
                            onSuccess = { isUnlocked = true },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                        ) ?: run { isUnlocked = true }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Unlock with Biometrics", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(strings.gdprDeleteVoice, color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Tous les enregistrements audio locaux seront définitivement effacés de cet appareil.", color = Color.LightGray) },
            confirmButton = {
                Button(
                    onClick = {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = null
                        currentlyPlayingPath = null

                        getRecordingsList().forEach { it.delete() }
                        recordingFiles = getRecordingsList()
                        showDeleteAllDialog = false
                        Toast.makeText(context, strings.profileUpdatedSuccess, Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text(strings.delete, color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteAllDialog = false }) {
                    Text(strings.close, color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
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
                    text = strings.audioVault,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${recordingFiles.size} audio file(s)",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (recordingFiles.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showDeleteAllDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(strings.delete, color = Color(0xFFEF4444), fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = { recordingFiles = getRecordingsList() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(strings.retry, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }

        if (recordingFiles.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = strings.noAudioRecordings,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recordingFiles) { file ->
                    val isPlaying = currentlyPlayingPath == file.absolutePath
                    val fileSizeKb = file.length() / 1024
                    val history = remember { localDatabase.getCallHistory() }
                    val (displayTitle, displaySubtitle) = remember(file.name, file.lastModified()) {
                        val rawName = file.name
                        val matched = history.firstOrNull { h ->
                            rawName.contains(h.id, ignoreCase = true) ||
                            (h.id.length >= 6 && rawName.contains(h.id.take(6), ignoreCase = true)) ||
                            (h.id.startsWith("native-") && rawName.contains(h.id.removePrefix("native-"), ignoreCase = true))
                        }

                        val title = when {
                            matched?.contactName != null && matched.contactName.isNotBlank() && !matched.contactName.startsWith("Appel") ->
                                "Appel avec ${matched.contactName}"
                            matched?.contactName != null && matched.contactName.isNotBlank() ->
                                matched.contactName
                            rawName.startsWith("Appel_") -> {
                                val clean = rawName.removePrefix("Appel_").removeSuffix(".mp4").removeSuffix(".wav").removeSuffix(".m4a")
                                val parts = clean.split("_")
                                if (parts.isNotEmpty() && parts[0].isNotBlank() && !parts[0].all { it.isDigit() }) {
                                    "Appel avec ${parts[0].replace("-", " ")}"
                                } else {
                                    "Appel Enregistré"
                                }
                            }
                            else -> "Appel Enregistré"
                        }

                        val locale = com.example.appcall.presentation.theme.getAppLocale(appLanguageCode)
                        val dateFormatted = SimpleDateFormat("d MMMM yyyy 'à' HH:mm", locale).format(Date(file.lastModified()))
                        val sizeMb = String.format(Locale.getDefault(), "%.1f MB", file.length().toDouble() / (1024 * 1024))
                        Pair(title, "$dateFormatted · $sizeMb")
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🎙️ $displayTitle",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = displaySubtitle,
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                            }

                            // ── PLAY / STOP BUTTON ──
                            Button(
                                onClick = {
                                    try {
                                        if (isPlaying) {
                                            mediaPlayer?.stop()
                                            mediaPlayer?.release()
                                            mediaPlayer = null
                                            currentlyPlayingPath = null
                                        } else {
                                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                                            @Suppress("DEPRECATION")
                                            audioManager.requestAudioFocus(
                                                null,
                                                AudioManager.STREAM_MUSIC,
                                                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                                            )

                                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                            audioManager.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                (maxVol * 0.9).toInt(),
                                                0
                                            )

                                            if (file.length() <= 512) {
                                                Toast.makeText(context, "Audio file is empty or too short", Toast.LENGTH_SHORT).show()
                                                return@Button
                                            }

                                            val player = MediaPlayer()
                                            val attrib = AudioAttributes.Builder()
                                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                                .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                                                .build()
                                            player.setAudioAttributes(attrib)

                                            player.setDataSource(file.absolutePath)
                                            player.setVolume(1.0f, 1.0f)
                                            player.prepare()
                                            player.start()
                                            player.setOnCompletionListener {
                                                currentlyPlayingPath = null
                                                mediaPlayer = null
                                                it.release()
                                            }
                                            mediaPlayer = player
                                            currentlyPlayingPath = file.absolutePath
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erreur lecture: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPlaying) Color.Red else NeonTeal
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isPlaying) "STOP" else "PLAY",
                                    color = if (isPlaying) Color.White else Color(0xFF0F172A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            // ── EXPORT / SHARE BUTTON ──
                            IconButton(
                                onClick = {
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = if (file.name.endsWith(".wav")) "audio/wav" else "audio/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, strings.shareRecording))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erreur export: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = strings.shareRecording,
                                    tint = NeonTeal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // ── DELETE BUTTON ──
                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                        mediaPlayer = null
                                        currentlyPlayingPath = null
                                    }
                                    if (file.delete()) {
                                        recordingFiles = getRecordingsList()
                                        Toast.makeText(context, strings.profileUpdatedSuccess, Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Impossible de supprimer", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = strings.delete,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

