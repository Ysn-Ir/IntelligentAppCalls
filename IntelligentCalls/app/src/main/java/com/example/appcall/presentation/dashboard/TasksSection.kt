package com.example.appcall.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
    var isAddingTask by remember { mutableStateOf(false) }

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
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // ── HEADER ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Mes Tâches",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${activeTasks.size} en cours • ${completedTasks.size} terminée(s)",
                    color = NeonTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Button(
                onClick = { isAddingTask = !isAddingTask },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAddingTask) Color(0x33EF4444) else Color(0x3300F2FE)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = if (isAddingTask) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = null,
                    tint = if (isAddingTask) Color(0xFFF87171) else NeonTeal,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isAddingTask) "Fermer" else "Ajouter",
                    color = if (isAddingTask) Color(0xFFF87171) else NeonTeal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // ── COLLAPSIBLE COMPACT TASK CREATION DRAWER ──
        AnimatedVisibility(
            visible = isAddingTask,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newTaskTitle,
                            onValueChange = { newTaskTitle = it },
                            placeholder = { Text("Nouvelle tâche...", color = Color.Gray, fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonTeal,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newTaskTitle.isNotBlank()) {
                                    val fullTitle = "[$selectedCategory] ${newTaskTitle.trim()}"
                                    val newId = "task-${System.currentTimeMillis()}"
                                    newTaskTitle = ""
                                    isAddingTask = false
                                    coroutineScope.launch {
                                        voipRepository.createTask(newId, fullTitle, false)
                                        tasks = localDatabase.getTasks()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonTeal),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("OK", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val categories = listOf("📞 Appel", "📅 RDV", "⚡ Urgent", "📝 Suivi", "💼 Client")
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
                    }
                }
            }
        }

        // ── COMPACT TASKS LIST ──
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (activeTasks.isNotEmpty()) {
                item {
                    Text(
                        text = "À FAIRE (${activeTasks.size})",
                        color = NeonTeal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                    )
                }
                items(activeTasks, key = { it.id }) { task ->
                    CompactTaskCard(
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
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(completedTasks, key = { it.id }) { task ->
                    CompactTaskCard(
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
                            .padding(top = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucune tâche pour le moment.\nCliquez sur '+ Ajouter' ou créez-en une avec l'IA.",
                            color = Color.Gray,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompactTaskCard(
    task: com.example.appcall.data.local.LocalTask,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val category = if (task.title.startsWith("[")) task.title.substringAfter("[").substringBefore("]") else null
    val cleanTitle = if (task.title.startsWith("[")) task.title.substringAfter("] ").trim() else task.title

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.completed) Color(0x1F1E293B) else Color(0xFF1E293B)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = task.completed,
                onCheckedChange = onToggle,
                modifier = Modifier.size(28.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = NeonTeal,
                    uncheckedColor = Color.Gray
                )
            )
            Spacer(modifier = Modifier.width(6.dp))

            if (!category.isNullOrBlank()) {
                val tagColor = when {
                    category.contains("Urgent") -> Color(0xFFEF4444)
                    category.contains("RDV") -> Color(0xFF3B82F6)
                    category.contains("Appel") -> NeonTeal
                    else -> ElectricViolet
                }
                Box(
                    modifier = Modifier
                        .background(tagColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = category,
                        color = tagColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = cleanTitle,
                color = if (task.completed) Color.Gray else Color.White,
                fontWeight = if (task.completed) FontWeight.Normal else FontWeight.Medium,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                style = androidx.compose.ui.text.TextStyle(
                    textDecoration = if (task.completed) TextDecoration.LineThrough else null
                )
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Supprimer",
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

