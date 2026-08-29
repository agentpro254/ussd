package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneForwarded
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.SavedUssdRoutine
import com.example.data.local.UssdHistoryItem
import com.example.data.model.SimCardInfo
import com.example.ui.components.PermissionStatusBanner
import com.example.ui.components.SimSelectionBottomSheet
import com.example.ui.components.UssdDialpad
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessBg
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark
import com.example.ui.viewmodel.DialerMode
import com.example.ui.viewmodel.PermissionStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    dialpadText: String,
    onDialChar: (Char) -> Unit,
    onDialDelete: () -> Unit,
    onDialClear: () -> Unit,
    onDialSubmit: () -> Unit,
    onInitiateSession: (code: String, simSlot: Int, sequence: List<String>, isSimulation: Boolean) -> Unit,
    simCards: List<SimCardInfo>,
    selectedSimSlot: Int,
    onSelectSimSlot: (Int) -> Unit,
    isDemoMode: Boolean,
    onToggleDemoMode: () -> Unit,
    dialerMode: DialerMode = DialerMode.CODEE_OVERLAY,
    onSelectDialerMode: ((DialerMode) -> Unit)? = null,
    savedRoutines: List<SavedUssdRoutine> = emptyList(),
    onRunRoutine: ((SavedUssdRoutine) -> Unit)? = null,
    onToggleFavorite: ((SavedUssdRoutine) -> Unit)? = null,
    onDeleteRoutine: ((SavedUssdRoutine) -> Unit)? = null,
    favoriteCodeIds: Set<String> = emptySet(),
    onToggleFavoriteCode: ((String) -> Unit)? = null,
    permissionStatus: PermissionStatus,
    onOpenPermissionWizard: () -> Unit,
    onCreateRoutineClick: (() -> Unit)? = null,
    recentHistory: List<UssdHistoryItem> = emptyList(),
    onNavigateToHistory: (() -> Unit)? = null,
    onNavigateToSimulator: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showSimChooserDialog by remember { mutableStateOf(false) }
    var pendingDialCode by remember { mutableStateOf<String?>(null) }

    val recentFrequentCodes = remember {
        listOf(
            Pair("*334#", "M-PESA Menu"),
            Pair("*144#", "Airtime Balance"),
            Pair("*544#", "Data & Bundles"),
            Pair("*247#", "Equity Eazzy"),
            Pair("*185#", "Airtel Money"),
            Pair("*123#", "Telkom Kenya")
        )
    }

    val activeSimDisplayName = remember(selectedSimSlot, simCards) {
        val found = simCards.firstOrNull { it.slotIndex == selectedSimSlot }
        found?.carrierName ?: "SIM ${selectedSimSlot + 1} (Primary)"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // App Bar / Top Identity
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(TealPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Codee",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Smart USSD Keypad",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Main USSD Dialpad (Keypad)
        item {
            UssdDialpad(
                codeText = dialpadText,
                onCharClick = onDialChar,
                onDeleteClick = onDialDelete,
                onClearClick = onDialClear,
                onDialClick = {
                    val codeToDial = dialpadText.trim()
                    if (codeToDial.isNotBlank()) {
                        // Prompt SIM line selector when code is initiated
                        pendingDialCode = codeToDial
                        showSimChooserDialog = true
                    }
                },
                modifier = Modifier.testTag("main_ussd_dialpad")
            )
        }

        // Quick Suggestion Chips Row
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Quick Codes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(recentFrequentCodes) { (code, label) ->
                        Surface(
                            onClick = {
                                onDialClear()
                                code.forEach { onDialChar(it) }
                                pendingDialCode = code
                                showSimChooserDialog = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = code,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimary
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recent Dialed Sessions History
        if (recentHistory.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Sessions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        if (onNavigateToHistory != null) {
                            Surface(
                                onClick = onNavigateToHistory,
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Transparent
                            ) {
                                Text(
                                    text = "View All",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimary,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentHistory.take(3).forEach { item ->
                            Card(
                                onClick = {
                                    pendingDialCode = item.ussdCode
                                    showSimChooserDialog = true
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(TealContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.History,
                                                contentDescription = null,
                                                tint = TealPrimaryDark,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }

                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = item.ussdCode,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TealPrimary
                                                )
                                                Text(
                                                    text = item.serviceName.ifBlank { "USSD Service" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                            Text(
                                                text = item.summary.take(45),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Icon(
                                        imageVector = Icons.Default.PhoneForwarded,
                                        contentDescription = "Re-dial",
                                        tint = TealPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Discoverability Card for Simulator Tab
        item {
            Card(
                onClick = { onNavigateToSimulator?.invoke() },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "⚡", fontSize = 20.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Looking for Quick Actions & M-PESA Shortcuts?",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Send Money, Paybill, Till & Banking shortcuts are available in the Simulate tab.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // SIM Selection Dialog / Bottom Sheet before launching session
    if (showSimChooserDialog) {
        val targetCode = pendingDialCode ?: dialpadText.ifBlank { "*334#" }
        SimSelectionBottomSheet(
            codeTitle = "USSD Dial",
            ussdCode = targetCode,
            simCards = simCards,
            selectedSimSlot = selectedSimSlot,
            onSelectSimSlot = { slot ->
                onSelectSimSlot(slot)
                showSimChooserDialog = false
                onInitiateSession(targetCode, slot, emptyList(), true)
            },
            onDismiss = { showSimChooserDialog = false }
        )
    }
}
