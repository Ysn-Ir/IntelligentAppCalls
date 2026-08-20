package com.example.appcall.presentation.calling

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appcall.data.model.CallHistoryItemDto
import com.example.appcall.presentation.theme.*

@Composable
fun CallHistoryScreen(
    callHistory: List<CallHistoryItemDto>,
    onCallClick: (String) -> Unit,
    onFabClick: (() -> Unit)? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterIndex by remember { mutableIntStateOf(0) } // 0 = Tous, 1 = Manqués, 2 = Avec résumé

    val filteredList = remember(callHistory, searchQuery, selectedFilterIndex) {
        callHistory.filter { item ->
            val matchesSearch = searchQuery.isBlank() ||
                (item.contactName?.contains(searchQuery, ignoreCase = true) == true) ||
                (item.phoneNumber?.contains(searchQuery, ignoreCase = true) == true) ||
                (item.summaryPreview?.contains(searchQuery, ignoreCase = true) == true)

            val matchesFilter = when (selectedFilterIndex) {
                1 -> item.status != "COMPLETED"
                2 -> !item.summaryPreview.isNullOrBlank()
                else -> true
            }

            matchesSearch && matchesFilter
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header & Sync Status
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgColor)
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Appels",
                        color = Text1,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(SuccessColor)
                        )
                        Text(
                            text = "Synchronisé",
                            color = Text3,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(9.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⌕", color = Text3, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Text1,
                            fontSize = 13.5.sp
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Rechercher un contact...",
                                    color = Text3,
                                    fontSize = 13.5.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Tabs: Tous / Manqués / Avec résumé
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(Surface1)
                        .border(1.dp, BorderColor, RoundedCornerShape(9.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val tabs = listOf("Tous", "Manqués", "Avec résumé")
                    tabs.forEachIndexed { index, tabTitle ->
                        val isSelected = selectedFilterIndex == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Surface2 else Color.Transparent)
                                .clickable { selectedFilterIndex = index }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabTitle,
                                color = if (isSelected) Text1 else Text3,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Call List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Aucun appel dans l'historique",
                        color = Text3,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    items(filteredList) { item ->
                        CallHistoryRow(item = item, onClick = { onCallClick(item.id) })
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }

        // Floating Action Button (FAB)
        if (onFabClick != null) {
            FloatingActionButton(
                onClick = onFabClick,
                containerColor = Text1,
                contentColor = BgColor,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 24.dp)
                    .size(48.dp)
            ) {
                Text(text = "📞", fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun CallHistoryRow(
    item: CallHistoryItemDto,
    onClick: () -> Unit
) {
    val displayName = item.contactName?.takeIf { it.isNotBlank() }
        ?: item.phoneNumber?.takeIf { it.isNotBlank() }
        ?: "Appel"

    val initials = displayName.split(" ")
        .mapNotNull { it.firstOrNull() }
        .take(2)
        .joinToString("")
        .uppercase()

    // Deterministic avatar color based on name hash
    val avatarBg = remember(displayName) {
        val colors = listOf(AvatarBgA, AvatarBgB, AvatarBgC, AvatarBgD, AvatarBgE)
        colors[Math.abs(displayName.hashCode()) % colors.size]
    }

    val isOutbound = item.direction.contains("OUT", ignoreCase = true)
    val isCompleted = item.status == "COMPLETED"

    val (dirSymbol, dirColor) = when {
        !isCompleted -> Pair("✕", DangerColor)
        isOutbound -> Pair("↗", Text2)
        else -> Pair("↙", SuccessColor)
    }

    val timeText = remember(item.startedAt) {
        try {
            if (item.startedAt?.contains("T") == true) {
                item.startedAt.substringAfter("T").replace("Z", "").take(5)
            } else item.startedAt ?: ""
        } catch (e: Exception) { "" }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 11.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(avatarBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (initials.isNotBlank()) initials else "📞",
                    color = Text1,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }

            Spacer(modifier = Modifier.width(11.dp))

            // Main Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = dirSymbol,
                            color = dirColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = displayName,
                            color = Text1,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (timeText.isNotBlank()) {
                        Text(
                            text = timeText,
                            color = Text3,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Phone number
                if (!item.phoneNumber.isNullOrBlank() && item.phoneNumber != displayName) {
                    Text(
                        text = item.phoneNumber,
                        color = Text3,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                // Snippet line with accent border
                if (!item.summaryPreview.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(16.dp)
                                .background(AccentColor)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = item.summaryPreview,
                            color = Text2,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 2
                        )
                    }
                }

                // Waveform footer tag & AI Stickers
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.height(11.dp)
                    ) {
                        Box(modifier = Modifier.width(2.dp).height(4.dp).background(Text3))
                        Box(modifier = Modifier.width(2.dp).height(9.dp).background(Text3))
                        Box(modifier = Modifier.width(2.dp).height(6.dp).background(Text3))
                        Box(modifier = Modifier.width(2.dp).height(11.dp).background(Text3))
                        Box(modifier = Modifier.width(2.dp).height(5.dp).background(Text3))
                        Box(modifier = Modifier.width(2.dp).height(8.dp).background(Text3))
                    }
                    Text(
                        text = "Enregistré",
                        color = Text3,
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    if (!item.summaryPreview.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(SuccessDim)
                                .border(0.5.dp, SuccessColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(SuccessColor))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Positif",
                                    color = SuccessColor,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Surface2)
                                .border(0.5.dp, BorderColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#IA",
                                color = Text3,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(11.dp))
        Divider(color = BorderColor, thickness = 1.dp)
    }
}
