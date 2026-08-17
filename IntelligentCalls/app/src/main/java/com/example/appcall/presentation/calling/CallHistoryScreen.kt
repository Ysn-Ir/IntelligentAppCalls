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
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Avatar with initials
                    val displayName = item.contactName ?: item.phoneNumber ?: "Appel"
                    val initials = displayName.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("")
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ElectricViolet.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (initials.isNotBlank()) initials.uppercase() else "📞",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = displayName,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        if (!item.phoneNumber.isNullOrBlank() && item.phoneNumber != item.contactName) {
                            Text(
                                text = item.phoneNumber,
                                color = Color(0xFF93C5FD),
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isCompleted = item.status == "COMPLETED"
                            val iconColor = if (isCompleted) NeonTeal else Color(0xFFF59E0B)
                            val statusIcon = if (isCompleted) Icons.Default.Call else Icons.Default.Close

                            Icon(
                                imageVector = statusIcon,
                                contentDescription = item.status,
                                tint = iconColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val dirLabel = if (item.direction.contains("OUT", ignoreCase = true)) "Sortant" else "Entrant"
                            Text(
                                text = "$dirLabel • ${if (isCompleted) "Terminé" else "Manqué"}",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Date / Time text
                Column(horizontalAlignment = Alignment.End) {
                    val timeText = try {
                        if (item.startedAt?.contains("T") == true) {
                            item.startedAt.substringAfter("T").replace("Z", "").take(5)
                        } else ""
                    } catch (e: Exception) { "" }

                    val dateText = try {
                        if (item.startedAt?.contains("T") == true) {
                            item.startedAt.substringBefore("T")
                        } else item.startedAt ?: ""
                    } catch (e: Exception) { "" }

                    if (timeText.isNotBlank()) {
                        Text(
                            text = timeText,
                            color = NeonTeal,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Text(
                        text = dateText,
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }

            // Summary snippet preview if available
            if (!item.summaryPreview.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "📝 ${item.summaryPreview}",
                        color = Color(0xFFD1D5DB),
                        fontSize = 12.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
