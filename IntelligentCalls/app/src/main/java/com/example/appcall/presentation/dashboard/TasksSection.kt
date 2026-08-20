package com.example.appcall.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.data.local.AppLocalDatabase
import com.example.appcall.data.notification.AppNotificationManager
import com.example.appcall.domain.repository.VoipRepository
import com.example.appcall.presentation.theme.*
import kotlinx.coroutines.launch

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun TasksSection(localDatabase: AppLocalDatabase, voipRepository: VoipRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf(localDatabase.getTasks()) }
    var gdprConsentEnabled by remember { mutableStateOf(true) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }

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

    var audioFiles by remember { mutableStateOf(getRecordingsList()) }
    var currentlyPlayingPath by remember { mutableStateOf<String?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayer?.stop()
                mediaPlayer?.release()
                mediaPlayer = null
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        voipRepository.fetchTasks().onSuccess {
            tasks = localDatabase.getTasks()
        }
        audioFiles = getRecordingsList()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(vertical = 18.dp, horizontal = 0.dp)
    ) {
        // ── SECTION 1: TÂCHES DÉTECTÉES PAR L'IA ──
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TÂCHES DÉTECTÉES PAR L'IA",
                    color = Text3,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                        .clickable { showAddTaskDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "＋ Ajouter",
                        color = Text1,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (showAddTaskDialog) {
                AlertDialog(
                    onDismissRequest = { showAddTaskDialog = false },
                    containerColor = Surface1,
                    title = { Text("Nouvelle tâche", color = Text1, fontWeight = FontWeight.Bold) },
                    text = {
                        OutlinedTextField(
                            value = newTaskTitle,
                            onValueChange = { newTaskTitle = it },
                            placeholder = { Text("Ex: Rappeler le client demain...", color = Text3, fontSize = 13.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BorderStrong,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = Text1,
                                unfocusedTextColor = Text1
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newTaskTitle.isNotBlank()) {
                                    val title = newTaskTitle.trim()
                                    val taskId = "task-${System.currentTimeMillis()}"
                                    newTaskTitle = ""
                                    showAddTaskDialog = false
                                    coroutineScope.launch {
                                        localDatabase.saveTask(
                                            id = taskId,
                                            title = title,
                                            completed = false
                                        )
                                        tasks = localDatabase.getTasks()
                                        voipRepository.createTask(taskId, title, completed = false)
                                        AppNotificationManager.showTaskNotification(context, taskId, title)
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Text1)
                        ) { Text("Ajouter", color = BgColor, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddTaskDialog = false }) { Text("Annuler", color = Text3) }
                    }
                )
            }
        }

        if (tasks.isEmpty()) {
            item {
                Text(
                    text = "Aucune tâche détectée",
                    color = Text3,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(tasks) { task ->
                val category = if (task.title.startsWith("[")) task.title.substringAfter("[").substringBefore("]") else null
                val cleanTitle = if (task.title.startsWith("[")) task.title.substringAfter("] ").trim() else task.title

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp, horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Custom Checkbox Box
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (task.completed) Text1 else Color.Transparent)
                                .border(1.5.dp, if (task.completed) Text1 else Text3, RoundedCornerShape(4.dp))
                                .clickable {
                                    coroutineScope.launch {
                                        voipRepository.toggleTask(task.id, !task.completed)
                                        tasks = localDatabase.getTasks()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.completed) {
                                Text(
                                    text = "✓",
                                    color = BgColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cleanTitle,
                                color = if (task.completed) Text3 else Text1,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                style = androidx.compose.ui.text.TextStyle(
                                    textDecoration = if (task.completed) TextDecoration.LineThrough else null
                                )
                            )
                            Text(
                                text = "Appel enregistré · IA",
                                color = Text3,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(11.dp))
                    Divider(color = BorderColor, thickness = 1.dp)
                }
            }
        }

        // ── SECTION 2: COFFRE-FORT AUDIO ──
        item {
            Text(
                text = "COFFRE-FORT AUDIO",
                color = Text3,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 22.dp, bottom = 9.dp)
            )
        }

        if (audioFiles.isEmpty()) {
            item {
                Text(
                    text = "Aucun enregistrement audio pour le moment",
                    color = Text3,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        } else {
            items(audioFiles) { file ->
                val isPlayingThis = currentlyPlayingPath == file.absolutePath
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 11.dp, horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (isPlayingThis) AccentDim else Surface1)
                                .border(1.dp, if (isPlayingThis) AccentColor else BorderColor, RoundedCornerShape(7.dp))
                                .clickable {
                                    try {
                                        if (isPlayingThis) {
                                            mediaPlayer?.stop()
                                            mediaPlayer?.release()
                                            mediaPlayer = null
                                            currentlyPlayingPath = null
                                        } else {
                                            mediaPlayer?.stop()
                                            mediaPlayer?.release()
                                            mediaPlayer = null

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
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isPlayingThis) "❚❚" else "▶",
                                color = if (isPlayingThis) AccentText else Text2,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                color = if (isPlayingThis) AccentText else Text1,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val sizeMb = String.format("%.1f MB", file.length().toDouble() / (1024 * 1024))
                            Text(
                                text = if (isPlayingThis) "En cours de lecture..." else sizeMb,
                                color = if (isPlayingThis) SuccessColor else Text3,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(27.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Surface2)
                                .border(1.dp, BorderColor, RoundedCornerShape(7.dp))
                                .clickable {
                                    try {
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/*"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Partager l'enregistrement"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erreur partage: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "⬇", color = Text2, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(11.dp))
                    Divider(color = BorderColor, thickness = 1.dp)
                }
            }
        }

        // ── SECTION 3: CONFIDENTIALITÉ RGPD ──
        item {
            Text(
                text = "CONFIDENTIALITÉ RGPD",
                color = Text3,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 22.dp, bottom = 9.dp)
            )
        }

        item {
            // GDPR Toggle Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 13.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Consentement données vocales",
                        color = Text1,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Autoriser l'enregistrement et l'analyse IA",
                        color = Text3,
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Switch(
                    checked = gdprConsentEnabled,
                    onCheckedChange = { gdprConsentEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgColor,
                        checkedTrackColor = Text1,
                        uncheckedThumbColor = Text3,
                        uncheckedTrackColor = Surface2
                    )
                )
            }

            Divider(color = BorderColor, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            var showDeleteVoiceDialog by remember { mutableStateOf(false) }

            // Action: Exporter mes données
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Surface1)
                    .border(1.dp, BorderColor, RoundedCornerShape(9.dp))
                    .clickable {
                        coroutineScope.launch {
                            voipRepository.exportAllData()
                                .onSuccess { jsonContent ->
                                    try {
                                        val exportFile = java.io.File(context.cacheDir, "appcall_gdpr_export.json")
                                        exportFile.writeText(jsonContent)
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            exportFile
                                        )
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            putExtra(Intent.EXTRA_SUBJECT, "Export Données RGPD AppCall")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Exporter mes données (JSON)"))
                                        Toast.makeText(context, "Export généré avec succès", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erreur export: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .onFailure {
                                    Toast.makeText(context, "Export échoué : ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .padding(12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Exporter mes données audio",
                    color = Text2,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action: Droit à l'oubli
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Surface1)
                    .border(1.dp, BorderColor, RoundedCornerShape(9.dp))
                    .clickable { showDeleteVoiceDialog = true }
                    .padding(12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Effacer mes enregistrements — droit à l'oubli",
                    color = DangerColor,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (showDeleteVoiceDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteVoiceDialog = false },
                    containerColor = Surface1,
                    title = { Text("Supprimer les enregistrements vocaux", color = Text1, fontWeight = FontWeight.Bold) },
                    text = { Text("Voulez-vous supprimer les enregistrements audio et transcriptions ? Les fichiers locaux et distants seront effacés.", color = Text2, fontSize = 12.sp) },
                    confirmButton = {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val targetDirs = listOf(
                                        File(context.filesDir, "recordings"),
                                        File(context.filesDir, "recordings_native"),
                                        context.getExternalFilesDir(null)?.let { File(it, "recordings") }
                                    ).filterNotNull()

                                    targetDirs.forEach { dir ->
                                        if (dir.exists() && dir.isDirectory) {
                                            dir.listFiles()?.forEach { it.delete() }
                                        }
                                    }
                                    audioFiles = getRecordingsList()
                                    voipRepository.deleteVoiceData()
                                        .onSuccess { Toast.makeText(context, "Tous les enregistrements vocaux ont été supprimés", Toast.LENGTH_SHORT).show() }
                                        .onFailure { Toast.makeText(context, "Enregistrements locaux supprimés", Toast.LENGTH_SHORT).show() }
                                }
                                showDeleteVoiceDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerColor)
                        ) { Text("Confirmer", color = Text1) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteVoiceDialog = false }) { Text("Annuler", color = Text3) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}


