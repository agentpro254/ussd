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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.local.SavedUssdRoutine
import com.example.ui.components.CreateRoutineDialog
import com.example.ui.components.PermissionWizardSheet
import com.example.ui.components.UssdSessionSheet
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RoutinesScreen
import com.example.ui.screens.SimulatorScreen
import com.example.ui.screens.TrustCenterScreen
import com.example.ui.theme.CodeeTheme
import com.example.ui.theme.TealPrimary
import com.example.ui.viewmodel.CodeeViewModel
import com.example.ui.viewmodel.DialerMode
import com.example.ui.viewmodel.PermissionStatus

enum class CodeeTab(val title: String, val icon: ImageVector) {
    HOME("Dialpad", Icons.Default.Dialpad),
    SIMULATOR("Simulate", Icons.Default.Bolt),
    ROUTINES("Workflows", Icons.Default.PlayArrow),
    HISTORY("History", Icons.Default.History),
    TRUST("Security", Icons.Default.Shield)
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
            Manifest.permission.READ_PHONE_STATE
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
    var selectedTab by remember { mutableStateOf(CodeeTab.HOME) }
    var showPermissionWizard by remember { mutableStateOf(false) }
    var showCreateRoutineDialog by remember { mutableStateOf(false) }

    val sessionState by viewModel.sessionState.collectAsState()
    val dialpadText by viewModel.dialpadText.collectAsState()
    val savedRoutines by viewModel.savedRoutines.collectAsState()
    val historyItems by viewModel.historyItems.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val availableSims by viewModel.availableSims.collectAsState()
    val selectedSimSlot by viewModel.selectedSimSlot.collectAsState()
    val isDemoMode by viewModel.isDemoMode.collectAsState()
    val favoriteCodeIds by viewModel.favoriteCodeIds.collectAsState()
    val dialerMode by viewModel.selectedDialerMode.collectAsState()
    val requireDialConfirmation by viewModel.requireDialConfirmation.collectAsState()
    val showConfirmDialog by viewModel.showConfirmDialog.collectAsState()
    val confirmCode by viewModel.confirmDialogCode.collectAsState()
    val confirmSimSlot by viewModel.confirmDialogSimSlot.collectAsState()
    val confirmSteps by viewModel.confirmDialogSteps.collectAsState()
    val confirmTitle by viewModel.confirmDialogTitle.collectAsState()
    val confirmIsPinProtected by viewModel.confirmDialogIsPinProtected.collectAsState()

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

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                CodeeTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    val isCenterDialpad = tab == CodeeTab.HOME

                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = {
                            if (isCenterDialpad) {
                                androidx.compose.material3.Surface(
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = if (isSelected) TealPrimary else TealPrimary.copy(alpha = 0.14f),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    androidx.compose.foundation.layout.Box(
                                        contentAlignment = androidx.compose.ui.Alignment.Center,
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
                                fontWeight = if (isSelected || isCenterDialpad) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TealPrimary,
                            selectedTextColor = TealPrimary,
                            indicatorColor = if (isCenterDialpad) Color.Transparent else TealPrimary.copy(alpha = 0.12f)
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        when (selectedTab) {
            CodeeTab.HOME -> {
                HomeScreen(
                    dialpadText = dialpadText,
                    onDialChar = { viewModel.appendDialpadChar(it) },
                    onDialDelete = { viewModel.deleteDialpadChar() },
                    onDialClear = { viewModel.clearDialpad() },
                    onDialSubmit = { viewModel.requestDialCode(title = "Dialpad Call") },
                    onInitiateSession = { code, simSlot, sequence, _ ->
                        viewModel.requestDialCode(
                            code = code,
                            simSlot = simSlot,
                            automatedSteps = sequence,
                            title = "USSD Action"
                        )
                    },
                    simCards = availableSims,
                    selectedSimSlot = selectedSimSlot,
                    onSelectSimSlot = { viewModel.setSimSlot(it) },
                    isDemoMode = isDemoMode,
                    onToggleDemoMode = { viewModel.toggleDemoMode() },
                    dialerMode = dialerMode,
                    onSelectDialerMode = { viewModel.setDialerMode(it) },
                    savedRoutines = savedRoutines,
                    onRunRoutine = { routine ->
                        val autoSteps = if (routine.stepsCsv.isNotBlank()) {
                            routine.stepsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        } else emptyList()
                        viewModel.requestDialCode(
                            code = routine.ussdCode,
                            simSlot = routine.simSlot,
                            automatedSteps = autoSteps,
                            title = routine.title
                        )
                        viewModel.updateRoutineLastUsed(routine)
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onDeleteRoutine = { viewModel.deleteRoutine(it.id) },
                    favoriteCodeIds = favoriteCodeIds,
                    onToggleFavoriteCode = { viewModel.toggleFavoriteCode(it) },
                    permissionStatus = permissionStatus,
                    onOpenPermissionWizard = { showPermissionWizard = true },
                    onCreateRoutineClick = { showCreateRoutineDialog = true },
                    recentHistory = historyItems,
                    onNavigateToHistory = { selectedTab = CodeeTab.HISTORY },
                    onNavigateToSimulator = { selectedTab = CodeeTab.SIMULATOR },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            CodeeTab.SIMULATOR -> {
                SimulatorScreen(
                    onLaunchSimulatedSession = { code ->
                        viewModel.requestDialCode(code = code, title = "Simulator Test")
                    },
                    simCards = availableSims,
                    selectedSimSlot = selectedSimSlot,
                    onSelectSimSlot = { viewModel.setSimSlot(it) },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            CodeeTab.ROUTINES -> {
                RoutinesScreen(
                    routines = savedRoutines,
                    onRunRoutine = { routine ->
                        val autoSteps = if (routine.stepsCsv.isNotBlank()) {
                            routine.stepsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        } else emptyList()
                        viewModel.requestDialCode(
                            code = routine.ussdCode,
                            simSlot = routine.simSlot,
                            automatedSteps = autoSteps,
                            title = routine.title
                        )
                        viewModel.updateRoutineLastUsed(routine)
                    },
                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                    onDeleteRoutine = { viewModel.deleteRoutine(it.id) },
                    onCreateNewClick = { showCreateRoutineDialog = true },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            CodeeTab.HISTORY -> {
                HistoryScreen(
                    historyItems = historyItems,
                    onRerun = { code ->
                        viewModel.requestDialCode(code = code, title = "Re-dial History")
                    },
                    onClearAll = { viewModel.clearAllHistory() },
                    onDeleteItem = { viewModel.deleteHistoryItem(it) },
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

        // Anti-Loop User Confirmation Dialog
        if (showConfirmDialog) {
            com.example.ui.components.ConfirmDialDialog(
                code = confirmCode,
                simCards = availableSims,
                selectedSimSlot = confirmSimSlot,
                flowTitle = confirmTitle,
                isPinProtected = confirmIsPinProtected,
                onConfirm = { viewModel.confirmAndLaunchPendingDial() },
                onCancel = { viewModel.dismissConfirmDialog() }
            )
        }

        // Active Session Sheet ("Good UI" replacement for USSD)
        UssdSessionSheet(
            sessionState = sessionState,
            onSubmitInput = { input -> viewModel.submitStepInput(input) },
            onDismiss = { viewModel.dismissSession() }
        )

        // Permission Wizard Sheet
        if (showPermissionWizard) {
            PermissionWizardSheet(
                status = permissionStatus,
                onDismiss = { showPermissionWizard = false },
                onRequestPhonePermission = onRequestPhonePermissions
            )
        }

        // Create Automated Routine Dialog
        if (showCreateRoutineDialog) {
            CreateRoutineDialog(
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
