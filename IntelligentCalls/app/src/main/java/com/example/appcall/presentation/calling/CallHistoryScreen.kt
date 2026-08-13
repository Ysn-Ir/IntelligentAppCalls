package com.example.appcall.presentation.calling

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.data.model.CallHistoryItemDto
import com.example.appcall.presentation.theme.ElectricViolet
import com.example.appcall.presentation.theme.NeonTeal

@Composable
fun CallHistoryScreen(
    callHistory: List<CallHistoryItemDto>,
    onCallClick: (String) -> Unit
) {
    if (callHistory.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No call history available",
                color = Color.Gray,
                fontSize = 15.sp
            )
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(callHistory) { item ->
                CallHistoryRow(item = item, onClick = { onCallClick(item.id) })
            }
        }
    }
}

@Composable
fun CallHistoryRow(
    item: CallHistoryItemDto,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0x1F293754)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar with initials
                val initials = item.contactName?.split(" ")?.mapNotNull { it.firstOrNull() }?.joinToString("") ?: "C"
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ElectricViolet.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = item.contactName ?: "Unknown Contact",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Call direction & status icon
                        val iconColor = when (item.status) {
                            "COMPLETED" -> NeonTeal
                            "MISSED" -> Color(0xFFF59E0B)
                            else -> Color.Red
                        }
                        val statusIcon = when (item.status) {
                            "COMPLETED" -> Icons.Default.Call
                            "MISSED" -> Icons.Default.Close
                            else -> Icons.Default.Warning
                        }

                        Icon(
                            imageVector = statusIcon,
                            contentDescription = item.status,
                            tint = iconColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${item.direction} • ${item.status.lowercase()}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Date / Time text
            Column(horizontalAlignment = Alignment.End) {
                val timeText = item.startedAt?.substringAfter("T")?.substringBefore("Z")?.substring(0, 5) ?: ""
                val dateText = item.startedAt?.substringBefore("T") ?: ""
                Text(
                    text = timeText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = dateText,
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }
    }
}
