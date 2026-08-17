package com.example.appcall.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.data.local.AppLocalDatabase
import com.example.appcall.domain.repository.VoipRepository
import com.example.appcall.presentation.theme.ElectricViolet
import com.example.appcall.presentation.theme.NeonTeal
import kotlinx.coroutines.launch

@Composable
fun TasksSection(localDatabase: AppLocalDatabase, voipRepository: VoipRepository) {
    val coroutineScope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf(localDatabase.getTasks()) }
    var newTaskTitle by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("📞 Appel") }

    LaunchedEffect(Unit) {
        voipRepository.fetchTasks().onSuccess {
            tasks = localDatabase.getTasks()
        }
    }

    val activeTasks = tasks.filter { !it.completed }
    val completedTasks = tasks.filter { it.completed }

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
                    text = "Mes Tâches",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${activeTasks.size} en cours, ${completedTasks.size} terminée(s)",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        // ── TASK CREATION CARD ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = newTaskTitle,
                    onValueChange = { newTaskTitle = it },
                    label = { Text("Nouvelle tâche...", color = Color.Gray) },
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val categories = listOf("📞 Appel", "📅 RDV", "⚡ Urgent", "📝 Suivi")
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = NeonTeal,
                                selectedLabelColor = Color(0xFF0F172A),
                                containerColor = Color(0xFF0F172A),
                                labelColor = Color.White
                            )
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            if (newTaskTitle.isNotBlank()) {
                                val fullTitle = "[$selectedCategory] ${newTaskTitle.trim()}"
                                val newId = "task-${System.currentTimeMillis()}"
                                newTaskTitle = ""
                                coroutineScope.launch {
                                    voipRepository.createTask(newId, fullTitle, false)
                                    tasks = localDatabase.getTasks()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Ajouter", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        // ── TASKS LIST ──
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (activeTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "À FAIRE (${activeTasks.size})",
                        color = NeonTeal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }
                items(activeTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { isChecked ->
                            coroutineScope.launch {
                                voipRepository.toggleTask(task.id, isChecked)
                                tasks = localDatabase.getTasks()
                            }
                        },
                        onDelete = {
                            coroutineScope.launch {
                                localDatabase.deleteTask(task.id)
                                tasks = localDatabase.getTasks()
                            }
                        }
                    )
                }
            }

            if (completedTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "TERMINÉES (${completedTasks.size})",
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                items(completedTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onToggle = { isChecked ->
                            coroutineScope.launch {
                                voipRepository.toggleTask(task.id, isChecked)
                                tasks = localDatabase.getTasks()
                            }
                        },
                        onDelete = {
                            coroutineScope.launch {
                                localDatabase.deleteTask(task.id)
                                tasks = localDatabase.getTasks()
                            }
                        }
                    )
                }
            }

            if (tasks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucune tâche pour le moment.\nAjoutez-en une ci-dessus ou parlez à l'Assistant IA.",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskCard(
    task: com.example.appcall.data.local.LocalTask,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.completed) Color(0x1F1E293B) else Color(0xFF1E293B)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = NeonTeal,
                    uncheckedColor = Color.Gray
                )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    color = if (task.completed) Color.Gray else Color.White,
                    fontWeight = if (task.completed) FontWeight.Normal else FontWeight.Medium,
                    fontSize = 14.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        textDecoration = if (task.completed) TextDecoration.LineThrough else null
                    )
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
