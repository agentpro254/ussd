package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RoutinesScreen
import com.example.ui.screens.TrustCenterScreen
import com.example.ui.screens.UssdSessionScreen
import com.example.ui.theme.CodeeTheme
import com.example.ui.theme.TealPrimary
import com.example.ui.viewmodel.CodeeViewModel

enum class CodeeTab(val title: String, val icon: ImageVector) {
    CODES("USSD Codes", Icons.Default.Phone),
    HISTORY("SMS History", Icons.Default.History),
    CUSTOM("Custom", Icons.Default.Star),
    TRUST("Safety & Info", Icons.Default.Shield)
}

class MainActivity : ComponentActivity() {

    private val viewModel: CodeeViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        viewModel.refreshPermissions()
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted successfully", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CodeeTheme {
                CodeeAppContent(
                    viewModel = viewModel,
                    onRequestPhonePermissions = ::requestPhonePermissions
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
    }

    private fun requestPhonePermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }
}

@Composable
fun CodeeAppContent(
    viewModel: CodeeViewModel,
    onRequestPhonePermissions: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(CodeeTab.CODES) }
    var showCreateRoutineDialog by remember { mutableStateOf(false) }

    // Active in-app USSD interactive session state
    var activeSessionCode by remember { mutableStateOf<String?>(null) }
    var activeSessionTitle by remember { mutableStateOf("") }
    var activeSessionSubId by remember { mutableStateOf(-1) }
    var activeSessionSlotIndex by remember { mutableStateOf(0) }

    val savedRoutines by viewModel.savedRoutines.collectAsState()
    val historyItems by viewModel.historyItems.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val favoriteCodeIds by viewModel.favoriteCodeIds.collectAsState()
    val dialerMode by viewModel.selectedDialerMode.collectAsState()
    val requireDialConfirmation by viewModel.requireDialConfirmation.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // If an in-app USSD session is active, show the UssdSessionScreen directly!
    if (activeSessionCode != null) {
        UssdSessionScreen(
            code = activeSessionCode!!,
            title = activeSessionTitle,
            subscriptionId = activeSessionSubId,
            simSlotIndex = activeSessionSlotIndex,
            onClose = {
                activeSessionCode = null
                activeSessionTitle = ""
                activeSessionSubId = -1
                activeSessionSlotIndex = 0
            },
            onSessionFinished = { code, title, rawResponse ->
                viewModel.logCompletedSession(code, title, rawResponse)
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    CodeeTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        val isPrimaryTab = tab == CodeeTab.CODES

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                if (isPrimaryTab) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) TealPrimary else TealPrimary.copy(alpha = 0.14f),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.title,
                                                tint = if (isSelected) Color.White else TealPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                } else {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected || isPrimaryTab) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = TealPrimary,
                                selectedTextColor = TealPrimary,
                                indicatorColor = if (isPrimaryTab) Color.Transparent else TealPrimary.copy(alpha = 0.12f)
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            when (selectedTab) {
                CodeeTab.CODES -> {
                    HomeScreen(
                        onDialCode = { code, title, subId, slotIndex ->
                            // Launches internal in-app USSD viewer on selected SIM
                            activeSessionCode = code
                            activeSessionTitle = title
                            activeSessionSubId = subId
                            activeSessionSlotIndex = slotIndex
                        },
                        savedCustomCodes = savedRoutines,
                        favoriteCodeIds = favoriteCodeIds,
                        onToggleFavoriteCode = { viewModel.toggleFavoriteCode(it) },
                        onSaveCustomCode = { title, code, cat, desc ->
                            viewModel.saveRoutine(
                                title = title,
                                code = code,
                                category = cat,
                                description = desc
                            )
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                CodeeTab.HISTORY -> {
                    HistoryScreen(
                        onDialCode = { code, title ->
                            activeSessionCode = code
                            activeSessionTitle = title
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                CodeeTab.CUSTOM -> {
                    RoutinesScreen(
                        routines = savedRoutines,
                        onRunRoutine = { routine ->
                            activeSessionCode = routine.ussdCode
                            activeSessionTitle = routine.title
                            viewModel.updateRoutineLastUsed(routine)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDeleteRoutine = { viewModel.deleteRoutine(it.id) },
                        onCreateNewClick = {
                            showCreateRoutineDialog = true
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                CodeeTab.TRUST -> {
                    TrustCenterScreen(
                        status = permissionStatus,
                        dialerMode = dialerMode,
                        onSelectDialerMode = { viewModel.setDialerMode(it) },
                        requireDialConfirmation = requireDialConfirmation,
                        onToggleRequireConfirmation = { viewModel.setRequireDialConfirmation(it) },
                        onStopAllSessions = { viewModel.stopAllSessions() },
                        onClearCache = { viewModel.clearCache() },
                        onRefreshPermissions = { viewModel.refreshPermissions() },
                        onRequestPhonePermissions = onRequestPhonePermissions,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }

            if (showCreateRoutineDialog) {
                com.example.ui.components.CreateRoutineDialog(
                    onDismiss = { showCreateRoutineDialog = false },
                    onSave = { title, code, category, stepsCsv, iconName, colorHex, desc ->
                        viewModel.saveRoutine(
                            title = title,
                            code = code,
                            category = category,
                            stepsCsv = stepsCsv,
                            iconName = iconName,
                            colorHex = colorHex,
                            description = desc
                        )
                        showCreateRoutineDialog = false
                    }
                )
            }
        }
    }
}
