package com.example

import android.Manifest
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.OnboardingSetupGuideDialog
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RoutinesScreen
import com.example.ui.screens.TrustCenterScreen
import com.example.ui.screens.UssdSessionScreen
import com.example.ui.theme.CodeeTheme
import com.example.ui.theme.TealPrimary
import com.example.ui.viewmodel.CodeeViewModel

enum class CodeeTab(val title: String, val icon: ImageVector) {
    CODES("USSD Dial", Icons.Default.Phone),
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
            val themeColor by viewModel.themeColor.collectAsState()
            val displayScale by viewModel.displayScale.collectAsState()

            CodeeTheme(
                themeColor = themeColor,
                displayScale = displayScale
            ) {
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
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(CodeeTab.CODES) }
    var showCreateRoutineDialog by remember { mutableStateOf(false) }

    // First-time onboarding guide state
    val prefs = remember { context.getSharedPreferences("codee_user_prefs", android.content.Context.MODE_PRIVATE) }
    var showFirstTimeOnboarding by remember {
        mutableStateOf(!prefs.getBoolean("has_completed_onboarding", false))
    }

    // Active in-app USSD interactive session state
    var activeSessionCode by remember { mutableStateOf<String?>(null) }
    var activeSessionTitle by remember { mutableStateOf("") }
    var activeSessionSubId by remember { mutableStateOf(-1) }
    var activeSessionSlotIndex by remember { mutableStateOf(0) }

    val savedRoutines by viewModel.savedRoutines.collectAsState()
    val permissionStatus by viewModel.permissionStatus.collectAsState()
    val favoriteCodeIds by viewModel.favoriteCodeIds.collectAsState()
    val currentThemeColor by viewModel.themeColor.collectAsState()
    val currentDisplayScale by viewModel.displayScale.collectAsState()

    // Auto-request permissions on app start if CALL_PHONE is not yet granted
    LaunchedEffect(Unit) {
        if (!permissionStatus.isCallPhoneGranted) {
            onRequestPhonePermissions()
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    CodeeTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        val isPrimaryTab = tab == CodeeTab.CODES
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            icon = {
                                if (isPrimaryTab) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
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
                        permissionStatus = permissionStatus,
                        onRequestPhonePermissions = onRequestPhonePermissions,
                        onDialCode = { code, title, subId, slotIndex ->
                            // Launches internal in-app USSD viewer on selected SIM
                            activeSessionCode = code
                            activeSessionTitle = title
                            activeSessionSubId = subId
                            activeSessionSlotIndex = slotIndex
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
                        onRefreshPermissions = { viewModel.refreshPermissions() },
                        onRequestPhonePermissions = onRequestPhonePermissions,
                        currentThemeColor = currentThemeColor,
                        onSelectThemeColor = { viewModel.setThemeColor(it) },
                        currentDisplayScale = currentDisplayScale,
                        onSelectDisplayScale = { viewModel.setDisplayScale(it) },
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

        // Full-screen in-app live USSD session view
        AnimatedVisibility(
            visible = activeSessionCode != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            activeSessionCode?.let { code ->
                UssdSessionScreen(
                    code = code,
                    title = activeSessionTitle,
                    subscriptionId = activeSessionSubId,
                    simSlotIndex = activeSessionSlotIndex,
                    onClose = {
                        activeSessionCode = null
                        activeSessionTitle = ""
                    },
                    onSessionFinished = { finishedCode, summary, rawText ->
                        viewModel.logCompletedSession(
                            code = finishedCode,
                            title = if (activeSessionTitle.isNotBlank()) activeSessionTitle else summary,
                            rawResponse = rawText
                        )
                    }
                )
            }
        }

        // First-Time Onboarding & Setup Guide Dialog
        if (showFirstTimeOnboarding) {
            OnboardingSetupGuideDialog(
                status = permissionStatus,
                onRequestPhonePermissions = onRequestPhonePermissions,
                onDismiss = {
                    showFirstTimeOnboarding = false
                    prefs.edit().putBoolean("has_completed_onboarding", true).apply()
                }
            )
        }
    }
}
