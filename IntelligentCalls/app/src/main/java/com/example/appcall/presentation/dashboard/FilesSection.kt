package com.example.appcall.presentation.dashboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.data.local.AppLocalDatabase
import com.example.appcall.presentation.theme.NeonTeal
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilesSection(localDatabase: AppLocalDatabase) {
    val context = LocalContext.current
    val recordingsDir = remember { File(context.filesDir, "recordings") }
    
    fun getRecordingsList(): List<File> {
        return if (recordingsDir.exists() && recordingsDir.isDirectory) {
            recordingsDir.listFiles()?.filter { it.isFile && (it.name.endsWith(".wav") || it.name.endsWith(".mp4")) && it.length() > 512 }?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    var recordingFiles by remember { mutableStateOf(getRecordingsList()) }
    var currentlyPlayingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
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
            Text(
                text = "Enregistrements Audio",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = { recordingFiles = getRecordingsList() },
                colors = ButtonDefaults.buttonColors(containerColor = NeonTeal)
            ) {
                Text("Actualiser", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
            }
        }

        if (recordingFiles.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun enregistrement d'appel trouvé.\nPassez un appel pour enregistrer l'audio.",
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
                    val dateFormatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        .format(Date(file.lastModified()))

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
                                    text = file.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$dateFormatted | ${fileSizeKb} KB",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
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
                                                Toast.makeText(context, "Fichier vide ou trop court", Toast.LENGTH_SHORT).show()
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
                                        Toast.makeText(context, "Erreur de lecture: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isPlaying) Color.Red else NeonTeal
                                )
                            ) {
                                Text(
                                    text = if (isPlaying) "Stop" else "Écouter",
                                    color = if (isPlaying) Color.White else Color(0xFF0F172A),
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
