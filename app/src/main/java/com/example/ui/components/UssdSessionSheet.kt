package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ParsedUssdResponse
import com.example.data.model.StepLogItem
import com.example.data.model.UssdFlowStepRecord
import com.example.data.model.UssdInputType
import com.example.data.model.UssdMenuOption
import com.example.data.model.UssdResponseType
import com.example.data.model.UssdSessionFlow
import com.example.data.model.UssdSessionState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.AmberWarningBg
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessBg
import com.example.ui.theme.IndigoInfo
import com.example.ui.theme.IndigoInfoBg
import com.example.ui.theme.RoseError
import com.example.ui.theme.RoseErrorBg
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UssdSessionSheet(
    sessionState: UssdSessionState,
    onSubmitInput: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (sessionState is UssdSessionState.Idle) return

    val isActiveTransaction = sessionState is UssdSessionState.ActiveSession ||
            sessionState is UssdSessionState.Dialing ||
            sessionState is UssdSessionState.Submitting

    var showCancelConfirmationDialog by remember { mutableStateOf(false) }

    // Intercept hardware/gesture Back navigation during active transactions
    BackHandler(enabled = isActiveTransaction) {
        showCancelConfirmationDialog = true
    }

    // Modal BottomSheet state: lock downward swipe dismissal during active transactions
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { targetValue ->
            if (isActiveTransaction && targetValue == SheetValue.Hidden) {
                // Prevent drag-down dismissal while transaction is in flight
                false
            } else {
                true
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = {
            // Prevent accidental outside backdrop clicks from cancelling active transactions
            if (isActiveTransaction) {
                showCancelConfirmationDialog = true
            } else {
                onDismiss()
            }
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier.testTag("ussd_session_modal")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .animateContentSize()
        ) {
            when (sessionState) {
                is UssdSessionState.Dialing -> {
                    DialingContent(
                        code = sessionState.code,
                        simSlot = sessionState.simSlot,
                        isSimulation = sessionState.isSimulation,
                        onRequestCancel = { showCancelConfirmationDialog = true }
                    )
                }
                is UssdSessionState.ActiveSession -> {
                    ActiveSessionContent(
                        code = sessionState.code,
                        step = sessionState.step,
                        response = sessionState.response,
                        flow = sessionState.flow,
                        history = sessionState.historySteps,
                        isSimulation = sessionState.isSimulation,
                        isAutomating = sessionState.isAutomating,
                        pendingInputs = sessionState.pendingInputs,
                        onSubmit = onSubmitInput,
                        onRequestCancel = { showCancelConfirmationDialog = true }
                    )
                }
                is UssdSessionState.Submitting -> {
                    SubmittingContent(
                        input = sessionState.input,
                        step = sessionState.step,
                        flow = sessionState.flow,
                        isSimulation = sessionState.isSimulation
                    )
                }
                is UssdSessionState.Completed -> {
                    CompletedContent(
                        code = sessionState.code,
                        summary = sessionState.summary,
                        response = sessionState.response,
                        flow = sessionState.flow,
                        history = sessionState.historySteps,
                        isSuccess = sessionState.isSuccess,
                        durationMs = sessionState.durationMs,
                        isSimulation = sessionState.isSimulation,
                        onDone = onDismiss
                    )
                }
                is UssdSessionState.Failed -> {
                    FailedContent(
                        code = sessionState.code,
                        errorReason = sessionState.errorReason,
                        rawText = sessionState.rawText,
                        flow = sessionState.flow,
                        onDismiss = onDismiss
                    )
                }
                UssdSessionState.Idle -> Unit
            }
        }
    }

    // Safety Alert Dialog: Protects against accidental transaction cancellation
    if (showCancelConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showCancelConfirmationDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = RoseError,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Cancel USSD Transaction?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "An active USSD session is communicating with the telecom carrier. Dismissing now will cancel the ongoing transaction.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCancelConfirmationDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = RoseError,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_cancel_transaction_btn")
                ) {
                    Text("End Transaction", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showCancelConfirmationDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("keep_transaction_running_btn")
                ) {
                    Text("Keep Transaction")
                }
            }
        )
    }
}

@Composable
private fun DialingContent(
    code: String,
    simSlot: Int,
    isSimulation: Boolean,
    onRequestCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(TealContainer),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = TealPrimary,
                strokeWidth = 3.dp
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Waiting for Carrier Response...",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = code,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = TealPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "SIM ${simSlot + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = EmeraldSuccessBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = EmeraldSuccess,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Touch Protected",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onRequestCancel,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cancel_dialing_button")
        ) {
            Text("Cancel Request", color = RoseError)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveSessionContent(
    code: String,
    step: Int,
    response: ParsedUssdResponse,
    flow: UssdSessionFlow?,
    history: List<StepLogItem>,
    isSimulation: Boolean,
    isAutomating: Boolean,
    pendingInputs: List<String>,
    onSubmit: (String) -> Unit,
    onRequestCancel: () -> Unit
) {
    var textInput by remember(response.rawText) { mutableStateOf("") }
    var isPinVisible by remember { mutableStateOf(false) }
    var showRawDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Session Header Bar with Protection Badge
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
                    shape = RoundedCornerShape(12.dp),
                    color = TealContainer
                ) {
                    Text(
                        text = "Live USSD",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Text(
                    text = code,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = EmeraldSuccessBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "Protected",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            IconButton(
                onClick = onRequestCancel,
                modifier = Modifier.testTag("dismiss_session_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel session",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Title & Description Card
        Text(
            text = response.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (response.body.isNotBlank() && response.body != response.title) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = response.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content by response type: supports direct response input across all modes
        when (response.type) {
            UssdResponseType.TRANSACTION -> {
                TransactionConfirmationCardLayout(
                    response = response,
                    onDone = onRequestCancel
                )
            }
            UssdResponseType.BALANCE -> {
                BalanceCardLayout(
                    response = response,
                    onDone = onRequestCancel
                )
            }
            UssdResponseType.PIN_REQUEST -> {
                PinRequestCardLayout(
                    response = response,
                    pinValue = textInput,
                    onPinChange = { textInput = it },
                    isPinVisible = isPinVisible,
                    onTogglePinVisibility = { isPinVisible = !isPinVisible },
                    onSubmit = {
                        if (textInput.isNotBlank()) {
                            onSubmit(textInput)
                        }
                    }
                )
            }
            UssdResponseType.MENU -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LiveUssdMenuCardLayout(
                        options = response.options,
                        onSelectOption = { optionId -> onSubmit(optionId) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Direct input field for user response (e.g. entering option number or unlisted input)
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Or type response / option number:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = textInput,
                                    onValueChange = { textInput = it },
                                    placeholder = { Text("e.g. 1, 2, 98, yes...") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("menu_manual_input_field"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Send
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (textInput.isNotBlank()) {
                                                onSubmit(textInput.trim())
                                            }
                                        }
                                    )
                                )

                                Button(
                                    onClick = {
                                        if (textInput.isNotBlank()) {
                                            onSubmit(textInput.trim())
                                        }
                                    },
                                    enabled = textInput.isNotBlank(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = TealPrimary,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .height(52.dp)
                                        .testTag("submit_menu_manual_input_btn")
                                 ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            UssdResponseType.CONFIRMATION -> {
                ConfirmationView(
                    options = response.options,
                    onSelect = onSubmit
                )
            }
            UssdResponseType.INPUT_PROMPT, UssdResponseType.INFO, UssdResponseType.SUCCESS_RESULT, UssdResponseType.ERROR_RESULT -> {
                InputPromptView(
                    inputType = response.inputType,
                    inputHint = response.inputHint,
                    textValue = textInput,
                    onValueChange = { textInput = it },
                    isPinVisible = isPinVisible,
                    onTogglePinVisibility = { isPinVisible = !isPinVisible },
                    onSubmit = {
                        if (textInput.isNotBlank()) {
                            onSubmit(textInput)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Collapsible "Raw USSD Text" viewer
        RawUssdAccordion(
            rawText = response.rawText,
            isExpanded = showRawDetails,
            onToggle = { showRawDetails = !showRawDetails }
        )

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun SessionBreadcrumbBar(flow: UssdSessionFlow) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Route,
            contentDescription = null,
            tint = TealPrimary,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = "Flow:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        flow.breadcrumbTrail.forEachIndexed { index, crumb ->
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (index == flow.breadcrumbTrail.lastIndex) TealContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (index == flow.breadcrumbTrail.lastIndex) TealPrimary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant)
            ) {
                Text(
                    text = crumb,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == flow.breadcrumbTrail.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == flow.breadcrumbTrail.lastIndex) TealPrimaryDark else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            if (index < flow.breadcrumbTrail.lastIndex) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InputPromptView(
    inputType: UssdInputType,
    inputHint: String,
    textValue: String,
    onValueChange: (String) -> Unit,
    isPinVisible: Boolean,
    onTogglePinVisibility: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val isPin = inputType == UssdInputType.PIN
        val isAmount = inputType == UssdInputType.AMOUNT
        val isPhone = inputType == UssdInputType.PHONE_NUMBER

        val keyboardType = when (inputType) {
            UssdInputType.PIN, UssdInputType.AMOUNT, UssdInputType.PHONE_NUMBER, UssdInputType.NUMERIC -> KeyboardType.Number
            else -> KeyboardType.Text
        }

        OutlinedTextField(
            value = textValue,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ussd_step_input_field"),
            placeholder = {
                Text(if (inputHint.isNotBlank()) inputHint else "Enter response here...")
            },
            shape = RoundedCornerShape(14.dp),
            visualTransformation = if (isPin && !isPinVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Send
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (textValue.isNotBlank()) onSubmit() },
                onSend = { if (textValue.isNotBlank()) onSubmit() }
            ),
            leadingIcon = {
                Icon(
                    imageVector = when {
                        isPin -> Icons.Default.Lock
                        isAmount -> Icons.Default.Shield
                        else -> Icons.Default.Code
                    },
                    contentDescription = null,
                    tint = TealPrimary
                )
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPin) {
                        IconButton(onClick = onTogglePinVisibility) {
                            Icon(
                                imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle PIN visibility"
                            )
                        }
                    }
                    if (textValue.isNotEmpty()) {
                        IconButton(onClick = { onValueChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear text",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TealPrimary,
                focusedLabelColor = TealPrimary
            )
        )

        // Quick suggestions for amounts
        if (isAmount) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Quick Amounts:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("50", "100", "200", "500", "1000", "2000").forEach { amount ->
                    Surface(
                        onClick = { onValueChange(amount) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.testTag("quick_amount_$amount")
                    ) {
                        Text(
                            text = "KES $amount",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onSubmit,
            enabled = textValue.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TealPrimary,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("submit_step_btn")
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Send Response",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ConfirmationView(
    options: List<UssdMenuOption>,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val confirmOption = options.firstOrNull { !it.isBack } ?: options.firstOrNull()
        val cancelOption = options.firstOrNull { it.isBack }

        if (confirmOption != null) {
            Button(
                onClick = { onSelect(confirmOption.id) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldSuccess,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_action_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = confirmOption.label.ifBlank { "Confirm & Proceed" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (cancelOption != null) {
            OutlinedButton(
                onClick = { onSelect(cancelOption.id) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("cancel_action_btn")
            ) {
                Text(
                    text = cancelOption.label.ifBlank { "Cancel Transaction" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = RoseError
                )
            }
        }
    }
}

@Composable
private fun SubmittingContent(
    input: String,
    step: Int,
    flow: UssdSessionFlow?,
    isSimulation: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = TealPrimary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Sending Response...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Input sent: \"$input\"",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (flow != null && flow.steps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            SessionBreadcrumbBar(flow = flow)
        }
    }
}

@Composable
private fun CompletedContent(
    code: String,
    summary: String,
    response: ParsedUssdResponse,
    flow: UssdSessionFlow?,
    history: List<StepLogItem>,
    isSuccess: Boolean,
    durationMs: Long,
    isSimulation: Boolean,
    onDone: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (response.type == UssdResponseType.TRANSACTION || response.isTransaction) {
            TransactionConfirmationCardLayout(
                response = response,
                onDone = onDone
            )
        } else if (response.type == UssdResponseType.BALANCE || response.isBalance) {
            BalanceCardLayout(
                response = response,
                onDone = onDone
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (isSuccess) EmeraldSuccessBg else RoseErrorBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (isSuccess) EmeraldSuccess else RoseError,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isSuccess) "Transaction Complete" else "Session Ended",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp
                    )

                    // Sequence Log Trail
                    if (flow != null && flow.steps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Executed Response Sequence:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SessionBreadcrumbBar(flow = flow)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Duration: ${durationMs / 1000}s • ${history.size.coerceAtLeast(1)} Steps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("USSD Response", summary))
                                Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy summary",
                                modifier = Modifier.size(16.dp),
                                tint = TealPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDone,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = TealPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("done_completed_btn")
            ) {
                Text(
                    text = "Close & Return",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun FailedContent(
    code: String,
    errorReason: String,
    rawText: String,
    flow: UssdSessionFlow?,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(RoseErrorBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = RoseError,
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "USSD Execution Failed",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorReason,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (flow != null && flow.steps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            SessionBreadcrumbBar(flow = flow)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Text("Dismiss")
        }
    }
}

@Composable
private fun RawUssdAccordion(
    rawText: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "View Raw System USSD Dialog",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = rawText.ifBlank { "(Empty raw buffer)" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionConfirmationCardLayout(
    response: ParsedUssdResponse,
    onDone: (() -> Unit)? = null,
    onCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSent = !response.rawText.contains("received", ignoreCase = true) &&
            !response.body.contains("received", ignoreCase = true) &&
            !response.title.contains("received", ignoreCase = true)
    val accentColor = if (isSent) CodeeBrandGreen else CodeeBrandBlue
    val title = if (isSent) "Money Sent" else "Money Received"
    val label = if (isSent) "Sent to" else "Received from"
    val displayAmount = response.amount ?: "KES 500.00"
    val mpesaCode = response.transactionId ?: "XYZ12345ABC"
    val person = response.recipient

    val now = remember {
        val sdf = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault())
        sdf.format(Date())
    }

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("transaction_confirmation_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Codee Branded Checkmark Pulse
            CodeeCheckmarkBadge(color = accentColor)

            Spacer(modifier = Modifier.height(14.dp))

            // Main Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Status Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = accentColor.copy(alpha = 0.1f)
            ) {
                Text(
                    text = "✅ Completed",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Codee Brand Gradient Divider
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(accentColor, accentColor.copy(alpha = 0.25f))
                        )
                    )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Large Bold Amount Display
            Text(
                text = displayAmount,
                style = MaterialTheme.typography.headlineLarge,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )

            // Recipient / Sender Label
            if (!person.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF888888),
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = person,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF0F0F0), thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Detail Chips Row (Code + Time)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF6F8FA),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("M-PESA Code", mpesaCode))
                        Toast.makeText(context, "M-PESA code copied: $mpesaCode", Toast.LENGTH_SHORT).show()
                        onCopy?.invoke()
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Code: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF888888)
                        )
                        Text(
                            text = mpesaCode,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF1A1A1A)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF6F8FA),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Time: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF888888)
                        )
                        Text(
                            text = now.substringAfter("· ").trim(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1A1A1A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons: Copy, Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("M-PESA Code", mpesaCode))
                        Toast.makeText(context, "M-PESA code copied: $mpesaCode", Toast.LENGTH_SHORT).show()
                        onCopy?.invoke()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Code", style = MaterialTheme.typography.labelMedium)
                }

                OutlinedButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            val summary = "✅ M-PESA Receipt\nAmount: $displayAmount\nTo: ${response.recipient ?: "Recipient"}\nCode: $mpesaCode\nDate: $now\n\n⚡ Secured by Codee"
                            putExtra(Intent.EXTRA_TEXT, summary)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Receipt"))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (onDone != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onDone,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(48.dp)
                        .testTag("transaction_done_btn")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Done",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Branding Footer
            Text(
                text = "⚡ Secured by Codee",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF999999),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun BalanceCardLayout(
    response: ParsedUssdResponse,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = TealContainer
        ),
        border = BorderStroke(1.5.dp, TealPrimary.copy(alpha = 0.4f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("balance_display_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = TealPrimary.copy(alpha = 0.15f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = TealPrimaryDark,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = response.title.ifBlank { "Account Balance" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimaryDark
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            val displayBalance = response.balance ?: response.body.lines().firstOrNull() ?: "KES 0.00"
            Text(
                text = displayBalance,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = TealPrimaryDark
            )

            if (response.body.isNotBlank() && response.body != displayBalance) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = response.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Account Balance", response.body.ifBlank { displayBalance }))
                        Toast.makeText(context, "Balance details copied", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }

                if (onDone != null) {
                    Button(
                        onClick = onDone,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TealPrimary,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PinRequestCardLayout(
    response: ParsedUssdResponse,
    pinValue: String,
    onPinChange: (String) -> Unit,
    isPinVisible: Boolean,
    onTogglePinVisibility: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = AmberWarningBg
        ),
        border = BorderStroke(1.5.dp, AmberWarning.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("pin_request_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AmberWarning),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "PIN",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "Security PIN Required",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = response.body.ifBlank { "Enter your secret PIN to authorize this request" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = pinValue,
                onValueChange = onPinChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pin_input_field"),
                placeholder = { Text("Enter 4-6 digit PIN") },
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (!isPinVisible) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (pinValue.isNotBlank()) onSubmit() }
                ),
                trailingIcon = {
                    IconButton(onClick = onTogglePinVisibility) {
                        Icon(
                            imageVector = if (isPinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle PIN visibility"
                        )
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSubmit,
                enabled = pinValue.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldSuccess,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("submit_pin_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Authorize & Submit PIN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
