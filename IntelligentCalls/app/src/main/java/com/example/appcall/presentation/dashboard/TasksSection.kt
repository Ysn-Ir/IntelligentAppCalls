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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.data.local.AppLocalDatabase
import com.example.appcall.data.notification.AppNotificationManager
import com.example.appcall.domain.repository.VoipRepository
import com.example.appcall.presentation.theme.*
import kotlinx.coroutines.launch

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider

enum class TaskFilter {
    ALL,
    PENDING,
    COMPLETED
}

@Composable
fun TasksSection(localDatabase: AppLocalDatabase, voipRepository: VoipRepository) {
    val context = LocalContext.current
    val netPrefs = remember { context.getSharedPreferences("network_settings", android.content.Context.MODE_PRIVATE) }
    val appLanguageCode = remember { netPrefs.getString("app_language", "en") ?: "en" }
    val strings = com.example.appcall.presentation.theme.getAppStrings(appLanguageCode)

    val coroutineScope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf(localDatabase.getTasks()) }
    var selectedFilter by remember { mutableStateOf(TaskFilter.ALL) }
    var showAddTaskDialog by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf("NORMAL") }
    var gdprConsentEnabled by remember { mutableStateOf(true) }

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

    val filteredTasks = when (selectedFilter) {
        TaskFilter.ALL -> tasks
        TaskFilter.PENDING -> tasks.filter { !it.completed }
        TaskFilter.COMPLETED -> tasks.filter { it.completed }
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
                    .padding(top = 2.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = strings.tasksHeader,
                        color = Text3,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "${tasks.count { !it.completed }} ${strings.pendingTasksFilter.lowercase()}",
                        color = Text2,
                        fontSize = 11.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(6.dp))
                        .clickable { showAddTaskDialog = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Text1, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = strings.addTask,
                            color = Text1,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── FILTER TABS ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filters = listOf(
                    TaskFilter.ALL to "${strings.allTasksFilter} (${tasks.size})",
                    TaskFilter.PENDING to "${strings.pendingTasksFilter} (${tasks.count { !it.completed }})",
                    TaskFilter.COMPLETED to "${strings.completedTasksFilter} (${tasks.count { it.completed }})"
                )
                filters.forEach { (filter, label) ->
                    val isSelected = selectedFilter == filter
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSelected) Surface2 else Surface1)
                            .border(1.dp, if (isSelected) BorderStrong else BorderColor, RoundedCornerShape(7.dp))
                            .clickable { selectedFilter = filter }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Text1 else Text3,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (showAddTaskDialog) {
                AlertDialog(
                    onDismissRequest = { showAddTaskDialog = false },
                    containerColor = Surface1,
                    title = { Text(strings.addTask, color = Text1, fontWeight = FontWeight.Bold) },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = newTaskTitle,
                                onValueChange = { newTaskTitle = it },
                                placeholder = { Text(strings.taskTitlePlaceholder, color = Text3, fontSize = 13.sp) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = BorderStrong,
                                    unfocusedBorderColor = BorderColor,
                                    focusedTextColor = Text1,
                                    unfocusedTextColor = Text1
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Text(strings.priorityLabel, color = Text3, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val priorities = listOf(
                                    ("URGENT" to strings.priorityUrgent) to DangerColor,
                                    ("IMPORTANT" to strings.priorityImportant) to WarnColor,
                                    ("NORMAL" to strings.priorityNormal) to AccentColor
                                )
                                priorities.forEach { (pPair, color) ->
                                    val (pId, pLabel) = pPair
                                    val isPSelected = selectedPriority == pId
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isPSelected) color.copy(alpha = 0.2f) else Surface2)
                                            .border(1.dp, if (isPSelected) color else BorderColor, RoundedCornerShape(6.dp))
                                            .clickable { selectedPriority = pId }
                                            .padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pLabel,
                                            color = if (isPSelected) color else Text3,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newTaskTitle.isNotBlank()) {
                                    val prefix = if (selectedPriority != "NORMAL") "[$selectedPriority] " else ""
                                    val title = "$prefix${newTaskTitle.trim()}"
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
                        ) { Text(strings.addTask, color = BgColor, fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddTaskDialog = false }) { Text(strings.close, color = Text3) }
                    }
                )
            }
        }

        if (filteredTasks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.noTasks,
                        color = Text3,
                        fontSize = 12.5.sp
                    )
                }
            }
        } else {
            items(filteredTasks) { task ->
                val priority = when {
                    task.title.contains("[URGENT]", ignoreCase = true) -> "URGENT"
                    task.title.contains("[IMPORTANT]", ignoreCase = true) -> "IMPORTANT"
                    else -> null
                }
                val cleanTitle = task.title
                    .replace("[URGENT]", "", ignoreCase = true)
                    .replace("[IMPORTANT]", "", ignoreCase = true)
                    .replace("[NORMAL]", "", ignoreCase = true)
                    .trim()

                // Extract detected phone number if present
                val phoneRegex = Regex("""(?:(?:\+|00)33|0)\s*[1-9](?:[\s.-]*\d{2}){4}""")
                val detectedPhone = phoneRegex.find(cleanTitle)?.value?.replace(" ", "")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 9.dp, horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Custom Checkbox Box
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (task.completed) Text1 else Color.Transparent)
                                .border(1.5.dp, if (task.completed) Text1 else Text3, RoundedCornerShape(5.dp))
                            .clickable {
                                coroutineScope.launch {
                                    voipRepository.toggleTask(task.id, !task.completed)
                                    tasks = localDatabase.getTasks()
                                }
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.completed) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = BgColor, modifier = Modifier.size(12.dp))
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (priority != null) {
                                    val badgeColor = if (priority == "URGENT") DangerColor else WarnColor
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(badgeColor.copy(alpha = 0.2f))
                                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    ) {
                                        Text(
                                            text = priority,
                                            color = badgeColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    text = cleanTitle,
                                    color = if (task.completed) Text3 else Text1,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    style = androidx.compose.ui.text.TextStyle(
                                        textDecoration = if (task.completed) TextDecoration.LineThrough else null
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Text(
                                text = strings.detectedByAi,
                                color = Text3,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Direct Dial Action if phone detected
                        if (detectedPhone != null) {
                            IconButton(
                                onClick = {
                                    try {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$detectedPhone"))
                                        context.startActivity(dialIntent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Impossible de composer le numéro", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = strings.startCall, tint = AccentColor, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Delete Task Button
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    voipRepository.deleteTask(task.id)
                                    tasks = localDatabase.getTasks()
                                    Toast.makeText(context, "Tâche supprimée", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = strings.delete, tint = DangerColor, modifier = Modifier.size(15.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(9.dp))
                    HorizontalDivider(color = BorderColor, thickness = 1.dp)
                }
            }
        }

        // ── SECTION 2: COFFRE-FORT AUDIO ──
        item {
            Text(
                text = strings.audioVault,
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
                    text = strings.noAudioRecordings,
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
                                text = if (isPlayingThis) "PAUSE" else "PLAY",
                                color = if (isPlayingThis) AccentText else Text2,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

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

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayTitle,
                                color = if (isPlayingThis) AccentText else Text1,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isPlayingThis) strings.playingAudio else displaySubtitle,
                                color = if (isPlayingThis) SuccessColor else Text3,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
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
                                        context.startActivity(Intent.createChooser(shareIntent, strings.shareRecording))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Erreur partage: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Share, contentDescription = strings.shareRecording, tint = Text2, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(11.dp))
                    HorizontalDivider(color = BorderColor, thickness = 1.dp)
                }
            }
        }

        // ── SECTION 3: CONFIDENTIALITÉ RGPD ──
        item {
            Text(
                text = strings.gdprSection,
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
                        text = strings.gdprVoiceConsentDesc,
                        color = Text1,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = strings.aiConsentActive,
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

            HorizontalDivider(color = BorderColor, thickness = 1.dp)
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
                                        context.startActivity(Intent.createChooser(shareIntent, strings.exportGdpr))
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
                    text = strings.gdprVoiceExport,
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
                    text = strings.gdprDeleteVoice,
                    color = DangerColor,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (showDeleteVoiceDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteVoiceDialog = false },
                    containerColor = Surface1,
                    title = { Text(strings.gdprDeleteVoice, color = Text1, fontWeight = FontWeight.Bold) },
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
                        ) { Text(strings.validate, color = Text1) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteVoiceDialog = false }) { Text(strings.close, color = Text3) }
                    }
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}
