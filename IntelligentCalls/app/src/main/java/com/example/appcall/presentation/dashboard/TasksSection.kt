package com.example.appcall.presentation.dashboard

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.data.local.AppLocalDatabase
import com.example.appcall.domain.repository.VoipRepository
import com.example.appcall.presentation.theme.NeonTeal
import kotlinx.coroutines.launch

@Composable
fun TasksSection(localDatabase: AppLocalDatabase, voipRepository: VoipRepository) {
    val coroutineScope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf(localDatabase.getTasks()) }
    var newTaskTitle by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        voipRepository.fetchTasks().onSuccess {
            tasks = localDatabase.getTasks()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Text(
            text = "Mes Tâches",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newTaskTitle,
                onValueChange = { newTaskTitle = it },
                label = { Text("Nouvelle tâche...", color = Color.Gray) },
                modifier = Modifier.weight(1f),
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
                    if (newTaskTitle.isNotBlank()) {
                        val title = newTaskTitle
                        val newId = "task-${System.currentTimeMillis()}"
                        newTaskTitle = ""
                        coroutineScope.launch {
                            voipRepository.createTask(newId, title, false)
                            tasks = localDatabase.getTasks()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonTeal)
            ) {
                Text("Ajouter", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tasks) { task ->
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
                        Checkbox(
                            checked = task.completed,
                            onCheckedChange = { isChecked ->
                                coroutineScope.launch {
                                    voipRepository.toggleTask(task.id, isChecked)
                                    tasks = localDatabase.getTasks()
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = NeonTeal)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = task.title,
                            color = if (task.completed) Color.Gray else Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                            style = androidx.compose.ui.text.TextStyle(
                                textDecoration = if (task.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                            )
                        )
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    voipRepository.deleteTask(task.id)
                                    tasks = localDatabase.getTasks()
                                }
                            }
                        ) {
                            Text("🗑️", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
