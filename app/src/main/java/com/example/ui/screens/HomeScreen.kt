package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.data.local.SavedUssdRoutine
import com.example.data.model.PreloadedUssdRepository
import com.example.data.model.SimCardInfo
import com.example.data.model.UssdCategoryItem
import com.example.data.model.UssdCodeItem
import com.example.ui.components.CategoryCard
import com.example.ui.components.CategoryDetailSheet
import com.example.ui.components.CodeInputPromptDialog
import com.example.ui.components.PermissionStatusBanner
import com.example.ui.components.SavedRoutineCard
import com.example.ui.components.SimSelectionBottomSheet
import com.example.ui.components.UssdCodeTile
import com.example.ui.components.UssdDialpad
import com.example.ui.components.UssdSessionInitiationForm
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessBg
import com.example.ui.theme.IndigoInfo
import com.example.ui.theme.IndigoInfoBg
import com.example.ui.theme.TealPrimary
import com.example.ui.viewmodel.PermissionStatus

enum class InputInterfaceMode(val title: String, val icon: ImageVector) {
    FORM("Session Form", Icons.Default.EditNote),
    DIALPAD("Keypad Dial", Icons.Default.Dialpad)
}

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
    savedRoutines: List<SavedUssdRoutine>,
    onRunRoutine: (SavedUssdRoutine) -> Unit,
    onToggleFavorite: (SavedUssdRoutine) -> Unit,
    onDeleteRoutine: (SavedUssdRoutine) -> Unit,
    favoriteCodeIds: Set<String>,
    onToggleFavoriteCode: (String) -> Unit,
    permissionStatus: PermissionStatus,
    onOpenPermissionWizard: () -> Unit,
    onCreateRoutineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputMode by remember { mutableStateOf(InputInterfaceMode.FORM) }
    var globalSearchQuery by remember { mutableStateOf("") }

    // Dialog & Sheet States
    var activeCategorySheet by remember { mutableStateOf<UssdCategoryItem?>(null) }
    var codeForInputPrompt by remember { mutableStateOf<UssdCodeItem?>(null) }
    var codeForSimChooser by remember { mutableStateOf<UssdCodeItem?>(null) }

    val searchResults = remember(globalSearchQuery) {
        if (globalSearchQuery.isBlank()) emptyList()
        else PreloadedUssdRepository.searchCodes(globalSearchQuery)
    }

    val favoriteCodes = remember(favoriteCodeIds) {
        PreloadedUssdRepository.allCodes.filter { favoriteCodeIds.contains(it.id) || it.isFavorite }
    }

    fun handleCodeLaunch(codeItem: UssdCodeItem) {
        if (codeItem.requiresInput) {
            codeForInputPrompt = codeItem
        } else if (simCards.size > 1) {
            codeForSimChooser = codeItem
        } else {
            onInitiateSession(codeItem.code, selectedSimSlot, emptyList(), isDemoMode)
        }
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
                        Text(
                            text = "#",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Column {
                        Text(
                            text = "codee",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "USSD Directory & Automation",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    onClick = onOpenPermissionWizard,
                    shape = RoundedCornerShape(20.dp),
                    color = if (permissionStatus.isAllMandatoryGranted) EmeraldSuccessBg else IndigoInfoBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (permissionStatus.isAllMandatoryGranted) EmeraldSuccess else IndigoInfo,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (permissionStatus.isAllMandatoryGranted) "100% Offline" else "Setup Required",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (permissionStatus.isAllMandatoryGranted) EmeraldSuccess else IndigoInfo
                        )
                    }
                }
            }
        }

        // Permission Banner
        if (!permissionStatus.isAllMandatoryGranted) {
            item {
                PermissionStatusBanner(
                    status = permissionStatus,
                    onOpenWizard = onOpenPermissionWizard
                )
            }
        }

        // Global Search Bar across all 31+ Preloaded Codes
        item {
            OutlinedTextField(
                value = globalSearchQuery,
                onValueChange = { globalSearchQuery = it },
                placeholder = { Text("Search 31+ USSD codes (e.g. M-PESA, *544#, KPLC)...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TealPrimary
                    )
                },
                trailingIcon = {
                    if (globalSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { globalSearchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealPrimary,
                    focusedLabelColor = TealPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("global_ussd_search_input")
            )
        }

        // 1-Tap Offline M-PESA Quick Actions Grid
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
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
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = EmeraldSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Quick M-PESA & Mobile Actions",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = EmeraldSuccess.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "100% Offline",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4x2 Quick Action Icons Grid
                    val quickActions = listOf(
                        Triple("Send Money", "*334#", Icons.Default.Send),
                        Triple("Withdraw", "*334#", Icons.Default.CallReceived),
                        Triple("Buy Airtime", "*141#", Icons.Default.PhoneAndroid),
                        Triple("Pay Bill", "*334#", Icons.Default.Receipt),
                        Triple("Lipa Na M-PESA", "*334#", Icons.Default.ShoppingCart),
                        Triple("My Account", "*334#", Icons.Default.AccountBalanceWallet),
                        Triple("Check Balance", "*144#", Icons.Default.Payments),
                        Triple("Data Bundles", "*544#", Icons.Default.Wifi)
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (rowIndex in 0 until quickActions.size step 4) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (colIndex in 0 until 4) {
                                    val itemIndex = rowIndex + colIndex
                                    if (itemIndex < quickActions.size) {
                                        val action = quickActions[itemIndex]
                                        Surface(
                                            onClick = {
                                                val found = PreloadedUssdRepository.allCodes.firstOrNull { it.code == action.second }
                                                if (found != null) {
                                                    handleCodeLaunch(found)
                                                } else {
                                                    onInitiateSession(action.second, selectedSimSlot, emptyList(), isDemoMode)
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("quick_action_${action.first.lowercase().replace(" ", "_")}")
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(TealPrimary.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = action.third,
                                                        contentDescription = action.first,
                                                        tint = TealPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = action.first,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quick Paybill / Utility Numbers Bar
                    Text(
                        text = "Quick Utility Paybills (One-Tap Dial):",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val utilities = listOf(
                            Pair("⚡ KPLC Tokens", "888880"),
                            Pair("⚡ KPLC Postpaid", "888888"),
                            Pair("💧 Nairobi Water", "444400"),
                            Pair("🏛️ KRA Taxes", "572572"),
                            Pair("🏥 NHIF", "200222"),
                            Pair("🎓 HELB", "200800")
                        )
                        items(utilities) { util ->
                            Surface(
                                onClick = {
                                    onInitiateSession("*334#", selectedSimSlot, emptyList(), isDemoMode)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = EmeraldSuccessBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = util.first,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSuccess
                                    )
                                    Text(
                                        text = "(${util.second})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // If search has results, display search matching items
        if (globalSearchQuery.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Search Results (${searchResults.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            items(searchResults, key = { it.id }) { codeItem ->
                val isFav = favoriteCodeIds.contains(codeItem.id) || codeItem.isFavorite
                UssdCodeTile(
                    codeItem = codeItem,
                    isFavorite = isFav,
                    onTap = { handleCodeLaunch(codeItem) },
                    onToggleFavorite = { onToggleFavoriteCode(codeItem.id) }
                )
            }

            if (searchResults.isEmpty()) {
                item {
                    Text(
                        text = "No codes found matching \"$globalSearchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
        }

        // Categories Section (Grid of 6 Preloaded Categories from article)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "31+ Codes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3x2 Grid for Categories
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val categories = PreloadedUssdRepository.allCategories
                    for (i in categories.indices step 3) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (j in 0 until 3) {
                                val index = i + j
                                if (index < categories.size) {
                                    val cat = categories[index]
                                    CategoryCard(
                                        category = cat,
                                        onClick = { activeCategorySheet = cat },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Starred Favorites Section
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Starred Codes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(favoriteCodes) { codeItem ->
                        val catColor = try {
                            Color(android.graphics.Color.parseColor(codeItem.colorHex))
                        } catch (e: Exception) {
                            TealPrimary
                        }
                        Card(
                            onClick = { handleCodeLaunch(codeItem) },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .width(150.dp)
                                .testTag("favorite_card_${codeItem.id}")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = codeItem.icon, fontSize = 20.sp)
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = catColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = codeItem.category,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = catColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = codeItem.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = codeItem.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = catColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // Custom USSD Input Header & Mode Switcher
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Custom USSD Dial",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        InputInterfaceMode.values().forEach { mode ->
                            val isSelected = inputMode == mode
                            Surface(
                                onClick = { inputMode = mode },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = mode.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // USSD Form or Dialpad depending on selected mode
        item {
            AnimatedContent(
                targetState = inputMode,
                label = "input_mode_transition"
            ) { mode ->
                when (mode) {
                    InputInterfaceMode.FORM -> {
                        UssdSessionInitiationForm(
                            codeText = dialpadText,
                            onCodeChange = { newCode ->
                                onDialClear()
                                newCode.forEach { onDialChar(it) }
                            },
                            onInitiateSession = onInitiateSession,
                            simCards = simCards,
                            selectedSimSlot = selectedSimSlot,
                            onSelectSimSlot = onSelectSimSlot,
                            isDemoMode = isDemoMode,
                            onToggleDemoMode = onToggleDemoMode
                        )
                    }
                    InputInterfaceMode.DIALPAD -> {
                        UssdDialpad(
                            codeText = dialpadText,
                            onCharClick = onDialChar,
                            onDeleteClick = onDialDelete,
                            onClearClick = onDialClear,
                            onDialClick = onDialSubmit,
                            simCards = simCards,
                            selectedSimSlot = selectedSimSlot,
                            onSelectSimSlot = onSelectSimSlot,
                            isDemoMode = isDemoMode,
                            onToggleDemoMode = onToggleDemoMode
                        )
                    }
                }
            }
        }

        // Saved Favorite Routines Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved Workflows",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Surface(
                    onClick = onCreateRoutineClick,
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "New",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        items(savedRoutines.take(4)) { routine ->
            SavedRoutineCard(
                routine = routine,
                onRun = { onRunRoutine(routine) },
                onToggleFavorite = { onToggleFavorite(routine) },
                onDelete = { onDeleteRoutine(routine) }
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Modal: Category Detail Sheet
    activeCategorySheet?.let { category ->
        CategoryDetailSheet(
            category = category,
            favoriteCodeIds = favoriteCodeIds,
            onSelectCode = { codeItem ->
                activeCategorySheet = null
                handleCodeLaunch(codeItem)
            },
            onToggleFavorite = { codeItem ->
                onToggleFavoriteCode(codeItem.id)
            },
            onDismiss = { activeCategorySheet = null }
        )
    }

    // Modal: Code Input Prompt Dialog (e.g. Voucher PIN or M-PESA PIN)
    codeForInputPrompt?.let { codeItem ->
        CodeInputPromptDialog(
            codeItem = codeItem,
            onConfirm = { resolvedCode ->
                codeForInputPrompt = null
                onInitiateSession(resolvedCode, selectedSimSlot, emptyList(), isDemoMode)
            },
            onDismiss = { codeForInputPrompt = null }
        )
    }

    // Modal: Dual-SIM Chooser Bottom Sheet
    codeForSimChooser?.let { codeItem ->
        SimSelectionBottomSheet(
            codeTitle = codeItem.name,
            ussdCode = codeItem.code,
            simCards = simCards,
            selectedSimSlot = selectedSimSlot,
            onSelectSimSlot = { slot ->
                codeForSimChooser = null
                onSelectSimSlot(slot)
                onInitiateSession(codeItem.code, slot, emptyList(), isDemoMode)
            },
            onDismiss = { codeForSimChooser = null }
        )
    }
}
