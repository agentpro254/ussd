package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import android.util.Log
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UssdSessionState
import com.example.data.parser.UssdParser
import com.example.engine.UssdSessionManager
import com.example.service.CodeeAccessibilityService
import com.example.ui.components.UssdResponseDisplay
import com.example.ui.components.parseSimpleUssd
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark

@Composable
fun UssdSessionScreen(
    code: String,
    title: String = "",
    subscriptionId: Int = -1,
    simSlotIndex: Int = 0,
    onClose: () -> Unit,
    onSessionFinished: ((code: String, summary: String, rawText: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var responseText by remember { mutableStateOf("Waiting for carrier response...") }
    var isWaiting by remember { mutableStateOf(true) }
    var userInput by remember { mutableStateOf("") }
    var isComplete by remember { mutableStateOf(false) }

    // Clean up active session on exit and listen for accessibility updates
    DisposableEffect(Unit) {
        CodeeAccessibilityService.setUssdCallback { newResponse ->
            if (newResponse.isNotBlank() && !newResponse.startsWith("Waiting for") && newResponse != "Sending...") {
                responseText = newResponse
                isWaiting = false
                isComplete = false
            }
        }
        onDispose {
            CodeeAccessibilityService.setUssdCallback(null)
            UssdSessionManager.dismissSession(context)
        }
    }

    // Initiate USSD session and collect real sessionState
    LaunchedEffect(code, subscriptionId) {
        Log.d("DIAL_DEBUG", "UssdSessionScreen LaunchedEffect firing for code=$code, subId=$subscriptionId")
        isWaiting = true
        isComplete = false
        responseText = "Waiting for carrier response..."

        UssdSessionManager.startUssdSession(
            context = context,
            rawCode = code,
            simSlot = simSlotIndex,
            userInitiated = true,
            skipTransparentActivityLaunch = true
        )

        UssdSessionManager.sessionState.collect { state ->
            when (state) {
                is UssdSessionState.ActiveSession -> {
                    val prompt = state.response.rawText.ifBlank {
                        if (state.response.body.isNotBlank()) state.response.body else state.response.title
                    }
                    if (prompt.isNotBlank() && prompt != "Waiting for carrier response..." && !prompt.startsWith("Waiting for")) {
                        responseText = prompt
                        isWaiting = false
                        isComplete = false
                    }
                }
                is UssdSessionState.Completed -> {
                    val finalSummary = state.response.rawText.ifBlank {
                        state.summary.ifBlank { state.response.body }
                    }
                    if (finalSummary.isNotBlank() && finalSummary != "Waiting for carrier response..." && !finalSummary.startsWith("Waiting for")) {
                        responseText = finalSummary
                        isWaiting = false
                        isComplete = true
                        onSessionFinished?.invoke(code, if (title.isNotBlank()) title else code, finalSummary)
                    }
                }
                else -> {
                    // Strictly ignore other states - UI only updates on ActiveSession or Completed
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Header Bar
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
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(TealPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = "USSD",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = if (title.isNotBlank()) title else "USSD Session",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = TealPrimary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = code,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimaryDark
                                ),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SimCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "SIM ${simSlotIndex + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = {
                    UssdSessionManager.dismissSession(context)
                    onClose()
                },
                modifier = Modifier.testTag("close_ussd_session_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        val menuOptions = remember(responseText) {
            if (responseText.isNotBlank() && !responseText.startsWith("Waiting for") && responseText != "Sending...") {
                UssdParser.parseToMenuOptions(responseText)
            } else {
                emptyList()
            }
        }
        val headerText = remember(responseText, menuOptions) {
            if (menuOptions.isNotEmpty()) {
                val lines = responseText.lines().map { it.trim() }.filter { it.isNotBlank() }
                lines.firstOrNull { line ->
                    !menuOptions.any { opt -> line.startsWith(opt.number) }
                }
            } else null
        }

        // Main Response View Area
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                if (isWaiting) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = TealPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = responseText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Communicating with carrier network",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (menuOptions.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("ussd_menu_options_list"),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (headerText != null) {
                            item {
                                Text(
                                    text = headerText,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                )
                            }
                        }
                        items(menuOptions, key = { it.number + it.label }) { option ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isWaiting = true
                                        responseText = "Sending..."
                                        CodeeAccessibilityService.sendUssdResponse(option.number)
                                        UssdSessionManager.submitStepResponse(option.number)
                                    }
                                    .testTag("ussd_option_${option.number}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Bold number on the left (e.g., 98, 0, 1)
                                    Text(
                                        text = option.number,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TealPrimary,
                                        modifier = Modifier.widthIn(min = 28.dp)
                                    )

                                    Spacer(modifier = Modifier.width(12.dp))

                                    // Label on the right (e.g., CashBack, Bizna Wallet, Bundle Offers)
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    UssdResponseDisplay(
                        response = responseText,
                        isComplete = isComplete,
                        onSendInput = { input ->
                            isWaiting = true
                            responseText = "Sending..."
                            CodeeAccessibilityService.sendUssdResponse(input)
                            UssdSessionManager.submitStepResponse(input)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Input Field & Action Bar: Keep a small text input below the list for PIN entry and custom responses
        val parsed = remember(responseText) {
            if (responseText == "Waiting for carrier response..." || responseText.isBlank() || responseText.startsWith("Waiting for")) {
                null
            } else {
                parseSimpleUssd(responseText)
            }
        }
        val isPinInput = parsed?.isPinPrompt == true || responseText.lowercase().contains("pin") || responseText.lowercase().contains("password")
        val shouldShowInputField = !isWaiting && !isComplete

        if (shouldShowInputField) {
            var pinVisible by remember(responseText) { mutableStateOf(false) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = userInput,
                    onValueChange = { userInput = it },
                    label = { Text(if (isPinInput) "PIN" else "Reply / PIN") },
                    placeholder = { 
                        Text(if (isPinInput) "Enter PIN..." else "Enter PIN or custom response...") 
                    },
                    singleLine = true,
                    visualTransformation = if (isPinInput && !pinVisible) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (isPinInput) KeyboardType.NumberPassword else KeyboardType.Text,
                        imeAction = ImeAction.Send
                    ),
                    leadingIcon = if (isPinInput) {
                        {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Security PIN",
                                tint = TealPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else null,
                    trailingIcon = if (isPinInput && userInput.isNotEmpty()) {
                        {
                            IconButton(onClick = { pinVisible = !pinVisible }) {
                                Icon(
                                    imageVector = if (pinVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (pinVisible) "Hide PIN" else "Show PIN",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else null,
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (userInput.isNotBlank()) {
                                keyboardController?.hide()
                                val toSend = userInput.trim()
                                userInput = ""
                                isWaiting = true
                                responseText = "Sending..."
                                CodeeAccessibilityService.sendUssdResponse(toSend)
                                UssdSessionManager.submitStepResponse(toSend)
                            }
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ussd_reply_input_field")
                )

                Button(
                    onClick = {
                        if (userInput.isNotBlank()) {
                            keyboardController?.hide()
                            val toSend = userInput.trim()
                            userInput = ""
                            isWaiting = true
                            responseText = "Sending..."
                            CodeeAccessibilityService.sendUssdResponse(toSend)
                            UssdSessionManager.submitStepResponse(toSend)
                        }
                    },
                    enabled = userInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    modifier = Modifier
                        .height(56.dp)
                        .testTag("ussd_send_input_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Done Button (when session is completed)
        if (isComplete) {
            Button(
                onClick = onClose,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("ussd_session_done_button")
            ) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
