package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PreloadedUssdRepository
import com.example.data.model.SimCardInfo
import com.example.data.model.UssdCodeItem
import com.example.data.parser.UssdParser
import com.example.ui.components.LiveUssdMenuCardLayout
import com.example.ui.components.SimSelectionBottomSheet
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessBg
import com.example.ui.theme.IndigoInfo
import com.example.ui.theme.IndigoInfoBg
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark

data class QuickActionData(
    val id: String,
    val title: String,
    val code: String,
    val goalKeyword: String,
    val category: String,
    val icon: ImageVector,
    val iconBgColor: Color,
    val iconColor: Color,
    val description: String
)

@Composable
fun SimulatorScreen(
    onLaunchSimulatedSession: (code: String) -> Unit,
    simCards: List<SimCardInfo> = emptyList(),
    selectedSimSlot: Int = 0,
    onSelectSimSlot: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var customRawInput by remember {
        mutableStateOf("Welcome to Telecom Self-Care:\n1. Check Account Balance\n2. Recharge Airtime\n3. High-Speed 4G Bundles\n4. Mobile Money Transfer\n0. Exit")
    }

    var selectedActionForSimPrompt by remember { mutableStateOf<QuickActionData?>(null) }
    var selectedTabCategory by remember { mutableStateOf("ALL") }

    val parsedPreview = remember(customRawInput) {
        UssdParser.parse(customRawInput)
    }

    val quickActionsList = remember {
        listOf(
            // M-PESA Actions
            QuickActionData(
                id = "send_money",
                title = "Send Money",
                code = "*334#",
                goalKeyword = "send_money",
                category = "M-PESA",
                icon = Icons.Default.Send,
                iconBgColor = Color(0xFFE8F5E9),
                iconColor = Color(0xFF2E7D32),
                description = "P2P transfer to any phone number"
            ),
            QuickActionData(
                id = "withdraw_cash",
                title = "Withdraw Cash",
                code = "*334#",
                goalKeyword = "withdraw",
                category = "M-PESA",
                icon = Icons.Default.Payments,
                iconBgColor = Color(0xFFFFF3E0),
                iconColor = Color(0xFFE65100),
                description = "Agent or ATM cash withdrawal"
            ),
            QuickActionData(
                id = "buy_airtime",
                title = "Buy Airtime",
                code = "*334#",
                goalKeyword = "buy_airtime",
                category = "M-PESA",
                icon = Icons.Default.PhoneAndroid,
                iconBgColor = Color(0xFFE3F2FD),
                iconColor = Color(0xFF1565C0),
                description = "Top up airtime for self or other"
            ),
            QuickActionData(
                id = "pay_bill",
                title = "Pay Bill",
                code = "*334#",
                goalKeyword = "pay_bill",
                category = "M-PESA",
                icon = Icons.Default.Receipt,
                iconBgColor = Color(0xFFEDE7F6),
                iconColor = Color(0xFF6A1B9A),
                description = "Pay utilities, KPLC & merchants"
            ),
            QuickActionData(
                id = "lipa_na_mpesa",
                title = "Lipa Na M-PESA",
                code = "*334#",
                goalKeyword = "lipa_na_mpesa",
                category = "M-PESA",
                icon = Icons.Default.ShoppingCart,
                iconBgColor = Color(0xFFFCE4EC),
                iconColor = Color(0xFFC2185B),
                description = "Buy Goods, Till or Pochi"
            ),
            QuickActionData(
                id = "check_balance",
                title = "Check Balance",
                code = "*334#",
                goalKeyword = "check_balance",
                category = "M-PESA",
                icon = Icons.Default.AccountBalanceWallet,
                iconBgColor = Color(0xFFE0F2F1),
                iconColor = Color(0xFF00796B),
                description = "Direct account balance enquiry"
            ),
            QuickActionData(
                id = "mini_statement",
                title = "Mini Statement",
                code = "*334#",
                goalKeyword = "mini_statement",
                category = "M-PESA",
                icon = Icons.Default.Receipt,
                iconBgColor = Color(0xFFFFF8E1),
                iconColor = Color(0xFFF57F17),
                description = "Recent 5 transactions report"
            ),
            QuickActionData(
                id = "my_account",
                title = "My Account",
                code = "*334#",
                goalKeyword = "my_account",
                category = "M-PESA",
                icon = Icons.Default.Shield,
                iconBgColor = Color(0xFFEFEBE9),
                iconColor = Color(0xFF4E342E),
                description = "Self-care, tariff & security settings"
            ),
            QuickActionData(
                id = "change_pin",
                title = "Change PIN",
                code = "*334#",
                goalKeyword = "change_pin",
                category = "M-PESA",
                icon = Icons.Default.Lock,
                iconBgColor = Color(0xFFFFEBEE),
                iconColor = Color(0xFFC62828),
                description = "Update 4-digit security PIN"
            ),
            QuickActionData(
                id = "reset_pin",
                title = "Reset PIN",
                code = "*334#",
                goalKeyword = "reset_pin",
                category = "M-PESA",
                icon = Icons.Default.LockReset,
                iconBgColor = Color(0xFFF3E5F5),
                iconColor = Color(0xFF7B1FA2),
                description = "PIN recovery & security reset"
            ),
            QuickActionData(
                id = "fuliza",
                title = "Fuliza Overdraft",
                code = "*334#",
                goalKeyword = "fuliza",
                category = "M-PESA",
                icon = Icons.Default.Bolt,
                iconBgColor = Color(0xFFFFFDE7),
                iconColor = Color(0xFFFBC02D),
                description = "Instant overdraft limit access"
            ),
            QuickActionData(
                id = "data_bundles",
                title = "Data Bundles",
                code = "*544#",
                goalKeyword = "data_bundles",
                category = "M-PESA",
                icon = Icons.Default.Wifi,
                iconBgColor = Color(0xFFE8EAF6),
                iconColor = Color(0xFF3949AB),
                description = "Safaricom Tunukiwa & 4G/5G data"
            ),
            // Banking Actions
            QuickActionData(
                id = "equity_bank",
                title = "Equity Eazzy 247",
                code = "*247#",
                goalKeyword = "send_money",
                category = "BANKING",
                icon = Icons.Default.AccountBalance,
                iconBgColor = Color(0xFFFFE0B2),
                iconColor = Color(0xFFE65100),
                description = "Equity Mobile Banking & EquiLoan"
            ),
            QuickActionData(
                id = "kcb_bank",
                title = "KCB Bank",
                code = "*522#",
                goalKeyword = "kcb",
                category = "BANKING",
                icon = Icons.Default.AccountBalance,
                iconBgColor = Color(0xFFDCEDC8),
                iconColor = Color(0xFF33691E),
                description = "KCB Mobile & M-PESA loan link"
            ),
            QuickActionData(
                id = "coop_bank",
                title = "Co-op MCo-op Cash",
                code = "*667#",
                goalKeyword = "send_money",
                category = "BANKING",
                icon = Icons.Default.AccountBalance,
                iconBgColor = Color(0xFFC8E6C9),
                iconColor = Color(0xFF1B5E20),
                description = "Co-operative Bank instant banking"
            ),
            QuickActionData(
                id = "airtel_money",
                title = "Airtel Money",
                code = "*185#",
                goalKeyword = "send_money",
                category = "NETWORKS",
                icon = Icons.Default.PhoneAndroid,
                iconBgColor = Color(0xFFFFCDD2),
                iconColor = Color(0xFFB71C1C),
                description = "Airtel Money transfer & selfcare"
            ),
            QuickActionData(
                id = "telkom_kenya",
                title = "Telkom Selfcare",
                code = "*123#",
                goalKeyword = "check_balance",
                category = "NETWORKS",
                icon = Icons.Default.Wifi,
                iconBgColor = Color(0xFFB3E5FC),
                iconColor = Color(0xFF01579B),
                description = "T-Kash & Telkom data packages"
            )
        )
    }

    val filteredActions = remember(selectedTabCategory) {
        if (selectedTabCategory == "ALL") quickActionsList
        else quickActionsList.filter { it.category == selectedTabCategory }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quick Actions & Simulator",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "All telecom, M-PESA & banking actions execute internally",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = EmeraldSuccessBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(EmeraldSuccess)
                    )
                    Text(
                        text = "100% In-App",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Category Filter Tabs (ALL, M-PESA, BANKING, NETWORKS)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("ALL" to "All Actions", "M-PESA" to "M-PESA", "BANKING" to "Banking", "NETWORKS" to "Networks").forEach { (key, label) ->
                val isSelected = selectedTabCategory == key
                Surface(
                    onClick = { selectedTabCategory = key },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isSelected) TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, if (isSelected) TealPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Actions Grid (Cards)
        Text(
            text = "Instant Quick Actions (${filteredActions.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Tap any action to launch the interactive multi-step session:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            filteredActions.forEach { action ->
                Card(
                    onClick = {
                        selectedActionForSimPrompt = action
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("quick_action_${action.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(action.iconBgColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    tint = action.iconColor,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = action.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = TealContainer
                                    ) {
                                        Text(
                                            text = action.code,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = TealPrimaryDark,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = action.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = TealPrimary,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Run",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Preset Carrier Multi-Step Workflows
        Text(
            text = "End-to-End Carrier Workflows",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SimWorkflowCard(
                title = "MTN Mobile Money Transfer",
                code = "*185#",
                steps = "Menu -> Recipient Phone -> Amount -> PIN -> Receipt",
                icon = Icons.Default.Send,
                color = Color(0xFFF59E0B),
                onLaunch = { onLaunchSimulatedSession("*185#") }
            )

            SimWorkflowCard(
                title = "Safaricom M-Pesa P2P & Bundles",
                code = "*334#",
                steps = "Send Money -> Phone -> Amount -> PIN -> Confirmation",
                icon = Icons.Default.PhoneAndroid,
                color = Color(0xFF10B981),
                onLaunch = { onLaunchSimulatedSession("*334#") }
            )

            SimWorkflowCard(
                title = "Airtel 4G High-Speed Bundles",
                code = "*141#",
                steps = "Category -> Daily 3GB -> Payment Method -> Done",
                icon = Icons.Default.Wifi,
                color = Color(0xFF6366F1),
                onLaunch = { onLaunchSimulatedSession("*141#") }
            )

            SimWorkflowCard(
                title = "Express Banking Core Statement",
                code = "*737#",
                steps = "Banking Menu -> PIN Verification -> Ledger Balance",
                icon = Icons.Default.AccountBalance,
                color = Color(0xFF8B5CF6),
                onLaunch = { onLaunchSimulatedSession("*737#") }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Live Regex Parser Inspector
        Text(
            text = "Real-Time Parser Inspector",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Type any arbitrary USSD text below to see how Codee parses it into clean UI elements:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = customRawInput,
            onValueChange = { customRawInput = it },
            label = { Text("Raw Carrier USSD Buffer") },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .testTag("simulator_raw_input")
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Parsed Output Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = TealPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Parsed Result (${parsedPreview.type})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TealContainer
                    ) {
                        Text(
                            text = "${parsedPreview.options.size} Options Found",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = parsedPreview.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (parsedPreview.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = parsedPreview.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (parsedPreview.options.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Interactive Rendered Menu:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LiveUssdMenuCardLayout(
                        options = parsedPreview.options,
                        onSelectOption = { selectedId ->
                            // Update sample input to show selected state or next step
                            customRawInput = "CON Option $selectedId Selected:\n1. Confirm Selection\n98. Back\n0. Main Menu"
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // SIM Card Selection Bottom Sheet for Quick Action Launches
    selectedActionForSimPrompt?.let { action ->
        SimSelectionBottomSheet(
            codeTitle = action.title,
            ussdCode = action.code,
            simCards = simCards,
            selectedSimSlot = selectedSimSlot,
            onSelectSimSlot = { slot ->
                onSelectSimSlot?.invoke(slot)
                onLaunchSimulatedSession(action.code)
                selectedActionForSimPrompt = null
            },
            onDismiss = { selectedActionForSimPrompt = null }
        )
    }
}

@Composable
private fun SimWorkflowCard(
    title: String,
    code: String,
    steps: String,
    icon: ImageVector,
    color: Color,
    onLaunch: () -> Unit
) {
    Card(
        onClick = onLaunch,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = code,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = TealPrimary
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = steps,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = TealPrimary,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Test flow",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
