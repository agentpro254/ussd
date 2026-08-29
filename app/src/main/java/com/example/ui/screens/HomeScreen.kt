package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.example.ui.components.SimSelectionBottomSheet
import com.example.ui.components.UssdDialpad
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessBg
import com.example.ui.theme.IndigoInfo
import com.example.ui.theme.IndigoInfoBg
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark
import com.example.ui.viewmodel.DialerMode
import com.example.ui.viewmodel.PermissionStatus

enum class HomeDialerOption(val title: String) {
    KEYPAD("Keypad"),
    RECENT_FLOWS("Recent Flows")
}

data class FlowStepDisplay(
    val stepNumber: Int,
    val label: String,
    val value: String
)

@OptIn(ExperimentalLayoutApi::class)
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
    var pendingAutomatedSequence by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedOption by remember { mutableStateOf(HomeDialerOption.KEYPAD) }

    val quickCodesList = remember {
        listOf(
            Pair("*334#", "M-PESA Menu"),
            Pair("*144#", "Airtime Balance"),
            Pair("*544#", "Data & Bundles"),
            Pair("*247#", "Equity Eazzy"),
            Pair("*185#", "Airtel Money"),
            Pair("*123#", "Telkom Kenya"),
            Pair("*522#", "KCB Banking"),
            Pair("*667#", "Co-op M-Coop")
        )
    }

    // Default sample flow items if database history is clean
    val fallbackRecentFlows = remember {
        listOf(
            UssdHistoryItem(
                id = 101,
                timestamp = System.currentTimeMillis() - 120000,
                ussdCode = "*334#",
                serviceName = "M-PESA Send Money",
                summary = "KES 500 sent to EMMAH KILONZO (0708814308)",
                responseSequence = "1 ➔ 0708814308 ➔ 500 ➔ 1234 ➔ 1",
                amount = "KES 500.00",
                recipient = "0708814308 (EMMAH KILONZO)",
                isSuccess = true
            ),
            UssdHistoryItem(
                id = 102,
                timestamp = System.currentTimeMillis() - 3600000,
                ussdCode = "*334#",
                serviceName = "Buy Airtime",
                summary = "KES 100 Airtime top-up for primary line",
                responseSequence = "3 ➔ 1 ➔ 100 ➔ 1234",
                amount = "KES 100.00",
                recipient = "My Phone",
                isSuccess = true
            ),
            UssdHistoryItem(
                id = 103,
                timestamp = System.currentTimeMillis() - 7200000,
                ussdCode = "*334#",
                serviceName = "Paybill (KPLC Prepaid)",
                summary = "KES 1,200 Electricity Token to 888888",
                responseSequence = "4 ➔ 888888 ➔ 142890123 ➔ 1200 ➔ 1234",
                amount = "KES 1,200.00",
                recipient = "888888 (KPLC)",
                isSuccess = true
            ),
            UssdHistoryItem(
                id = 104,
                timestamp = System.currentTimeMillis() - 14400000,
                ussdCode = "*544#",
                serviceName = "Data Bundles (2.5GB)",
                summary = "2.5GB 24hr Daily bundle purchased",
                responseSequence = "1 ➔ 2 ➔ 1",
                amount = "KES 100.00",
                recipient = "Self",
                isSuccess = true
            ),
            UssdHistoryItem(
                id = 105,
                timestamp = System.currentTimeMillis() - 86400000,
                ussdCode = "*185#",
                serviceName = "Airtel Money Transfer",
                summary = "KES 200 transferred to 0733123456",
                responseSequence = "1 ➔ 0733123456 ➔ 200 ➔ 1234",
                amount = "KES 200.00",
                recipient = "0733123456",
                isSuccess = true
            )
        )
    }

    val displayFlows = if (recentHistory.isNotEmpty()) recentHistory else fallbackRecentFlows

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                            text = "Smart USSD Keypad & Flow Runner",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 1. QUICK CODES SHIFTED TO THE TOP
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Codes",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tap to dial",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(quickCodesList) { (code, label) ->
                        Surface(
                            onClick = {
                                onDialClear()
                                code.forEach { onDialChar(it) }
                                pendingDialCode = code
                                pendingAutomatedSequence = emptyList()
                                showSimChooserDialog = true
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.testTag("quick_code_chip_${code.replace("*","").replace("#","")}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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

        // 2. TWO-SIDED OPTION SWITCHER (Left: Dialpad Keypad | Right: Recent Codes & Flows)
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Left Option: Keypad
                    val isKeypad = selectedOption == HomeDialerOption.KEYPAD
                    Surface(
                        onClick = { selectedOption = HomeDialerOption.KEYPAD },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isKeypad) TealPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("option_left_keypad")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dialpad,
                                contentDescription = null,
                                tint = if (isKeypad) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Dialpad",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isKeypad) FontWeight.Bold else FontWeight.Medium,
                                color = if (isKeypad) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Right Option: Recent Codes & Flows
                    val isFlows = selectedOption == HomeDialerOption.RECENT_FLOWS
                    Surface(
                        onClick = { selectedOption = HomeDialerOption.RECENT_FLOWS },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isFlows) TealPrimary else Color.Transparent,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("option_right_recent_flows")
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 9.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isFlows) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Recent Flows (${displayFlows.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isFlows) FontWeight.Bold else FontWeight.Medium,
                                color = if (isFlows) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // 3. MAIN CONTENT CONTAINER (Switchable between Left Dialpad and Right Recent Flows)
        if (selectedOption == HomeDialerOption.KEYPAD) {
            // LEFT SIDE: Default Dialpad Keypad
            item {
                UssdDialpad(
                    codeText = dialpadText,
                    onCharClick = onDialChar,
                    onDeleteClick = onDialDelete,
                    onClearClick = onDialClear,
                    onDialClick = {
                        val codeToDial = dialpadText.trim()
                        if (codeToDial.isNotBlank()) {
                            pendingDialCode = codeToDial
                            pendingAutomatedSequence = emptyList()
                            showSimChooserDialog = true
                        }
                    },
                    modifier = Modifier.testTag("main_ussd_dialpad")
                )
            }
        } else {
            // RIGHT SIDE: Recent Codes & Step Flow Automation
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Recent USSD Flows & Choices",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Click any flow to run automation automatically",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (onNavigateToHistory != null) {
                            Surface(
                                onClick = onNavigateToHistory,
                                shape = RoundedCornerShape(10.dp),
                                color = TealContainer.copy(alpha = 0.5f)
                            ) {
                                Text(
                                    text = "All Logs",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimaryDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    // List of recent flow items with step sequence badges
                    displayFlows.forEach { item ->
                        val sequenceSteps = extractStepsFromHistory(item)

                        Card(
                            onClick = {
                                pendingDialCode = item.ussdCode
                                pendingAutomatedSequence = sequenceSteps
                                showSimChooserDialog = true
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_flow_card_${item.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Top row: Code + Service Name + Time
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = TealContainer
                                        ) {
                                            Text(
                                                text = item.ussdCode,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = TealPrimaryDark,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Text(
                                            text = item.serviceName.ifBlank { "USSD Service" },
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Text(
                                        text = formatTimeAgo(item.timestamp),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }

                                // Step Flow & Numbers Selected Display
                                val displaySequence = parseStepDisplayList(item.responseSequence, sequenceSteps)
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "Recorded Flow & Numbers:",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        displaySequence.forEachIndexed { index, step ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "Step ${index + 1}:",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontSize = 10.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = step,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = TealPrimary
                                                    )
                                                }
                                            }

                                            if (index < displaySequence.size - 1) {
                                                Icon(
                                                    imageVector = Icons.Default.ArrowForward,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                    modifier = Modifier
                                                        .size(12.dp)
                                                        .align(Alignment.CenterVertically)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Summary text
                                if (item.summary.isNotBlank()) {
                                    Text(
                                        text = item.summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }

                                // Bottom action row: Run Automation button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.amount != null) {
                                        Text(
                                            text = item.amount,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldSuccess
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(1.dp))
                                    }

                                    Button(
                                        onClick = {
                                            pendingDialCode = item.ussdCode
                                            pendingAutomatedSequence = sequenceSteps
                                            showSimChooserDialog = true
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TealPrimary,
                                            contentColor = Color.White
                                        ),
                                        modifier = Modifier.testTag("run_automation_button_${item.id}")
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Run Automation",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
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
        val targetSequence = pendingAutomatedSequence
        SimSelectionBottomSheet(
            codeTitle = if (targetSequence.isNotEmpty()) "Run Automated Flow" else "USSD Dial",
            ussdCode = targetCode,
            simCards = simCards,
            selectedSimSlot = selectedSimSlot,
            onSelectSimSlot = { slot ->
                onSelectSimSlot(slot)
                showSimChooserDialog = false
                val stepsToRun = targetSequence
                pendingAutomatedSequence = emptyList()
                onInitiateSession(targetCode, slot, stepsToRun, true)
            },
            onDismiss = {
                showSimChooserDialog = false
                pendingAutomatedSequence = emptyList()
            }
        )
    }
}

/**
 * Extracts raw step strings from history item sequence
 */
private fun extractStepsFromHistory(item: UssdHistoryItem): List<String> {
    if (item.responseSequence.isNotBlank()) {
        val tokens = item.responseSequence.split("➔", "->", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isNotEmpty()) return tokens
    }
    if (item.stepsSummary.isNotBlank()) {
        val lines = item.stepsSummary.lines()
        val inputs = lines.mapNotNull { line ->
            val part = line.substringAfter("Input:", "").trim()
            if (part.isNotEmpty() && part != "null" && part != "-") part else null
        }
        if (inputs.isNotEmpty()) return inputs
    }
    return listOf("1")
}

/**
 * Converts sequence tokens into readable step tokens
 */
private fun parseStepDisplayList(rawSequence: String, fallbackList: List<String>): List<String> {
    if (rawSequence.isNotBlank()) {
        val tokens = rawSequence.split("➔", "->", ",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isNotEmpty()) return tokens
    }
    return fallbackList
}

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 1440 -> "${minutes / 60}h ago"
        else -> "${minutes / 1440}d ago"
    }
}

