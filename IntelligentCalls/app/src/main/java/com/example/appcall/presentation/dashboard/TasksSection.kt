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
import com.example.appcall.domain.repository.VoipRepository
import com.example.appcall.presentation.theme.*
import kotlinx.coroutines.launch

import android.content.Context
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
fun TasksSection(localDatabase: AppLocalDatabase, voipRepository: VoipRepository) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf(localDatabase.getTasks()) }
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
            Text(
                text = "TÂCHES DÉTECTÉES PAR L'IA",
                color = Text3,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 9.dp)
            )
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
                                .background(Surface1)
                                .border(1.dp, BorderColor, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "▶", color = Text2, fontSize = 10.5.sp)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                color = Text1,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val sizeMb = String.format("%.1f MB", file.length().toDouble() / (1024 * 1024))
                            Text(
                                text = sizeMb,
                                color = Text3,
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
                                .border(1.dp, BorderColor, RoundedCornerShape(7.dp)),
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

            // Action: Exporter mes données
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(Surface1)
                    .border(1.dp, BorderColor, RoundedCornerShape(9.dp))
                    .clickable { }
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
                    .clickable { }
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

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}


