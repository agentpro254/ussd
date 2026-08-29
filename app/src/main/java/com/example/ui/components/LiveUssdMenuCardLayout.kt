package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UssdMenuOption
import com.example.data.parser.NavigationDetector
import com.example.data.parser.NavigationOptions
import com.example.ui.theme.TealAccent
import com.example.ui.theme.TealContainer
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealPrimaryDark

/**
 * Responsive Card-Based Layout for Live USSD Menu Options with Smart Navigation Detection.
 * Automatically identifies and segregates Back, Next, Main Menu, and Exit actions into a
 * prominent, accessible navigation bar below regular options.
 */
@Composable
fun LiveUssdMenuCardLayout(
    options: List<UssdMenuOption>,
    onSelectOption: (optionId: String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    if (options.isEmpty()) return

    var filterQuery by remember { mutableStateOf("") }

    val filteredOptions = remember(options, filterQuery) {
        if (filterQuery.isBlank()) options
        else options.filter {
            it.label.contains(filterQuery, ignoreCase = true) ||
                    it.id.contains(filterQuery, ignoreCase = true) ||
                    it.description.contains(filterQuery, ignoreCase = true)
        }
    }

    // Smart Navigation Detection: separates regular menu items from navigation controls
    val navOptions = remember(filteredOptions) {
        NavigationDetector.detectNavigationOptions(filteredOptions)
    }

    val primaryOptions = navOptions.regularOptions

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_ussd_menu_card_layout"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Optional quick filter if menu has more than 5 regular options
        if (options.size > 5) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { filterQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("menu_option_search_field"),
                placeholder = {
                    Text(
                        text = "Filter ${options.size} options...",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TealPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
        }

        // Responsive Cards Grid / List Container for Regular Options
        if (primaryOptions.isNotEmpty()) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isWideLayout = maxWidth >= 360.dp
                val areAllShortLabels = primaryOptions.all { it.label.length <= 18 }

                if (isWideLayout && areAllShortLabels && primaryOptions.size >= 4) {
                    // 2-Column Responsive Card Grid
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        primaryOptions.chunked(2).forEach { rowOptions ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowOptions.forEach { option ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        SelectableMenuOptionCard(
                                            option = option,
                                            onSelect = { onSelectOption(option.id) },
                                            enabled = enabled,
                                            isCompact = true
                                        )
                                    }
                                }
                                if (rowOptions.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    // Full-width High-Touch Card Column
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        primaryOptions.forEach { option ->
                            SelectableMenuOptionCard(
                                option = option,
                                onSelect = { onSelectOption(option.id) },
                                enabled = enabled,
                                isCompact = false
                            )
                        }
                    }
                }
            }
        }

        // Prominent Smart Navigation Row (Back, Main Menu, Next, Exit)
        if (navOptions.hasNavigation) {
            Spacer(modifier = Modifier.height(4.dp))
            UssdNavigationRow(
                navOptions = navOptions,
                onSelectOption = onSelectOption,
                enabled = enabled
            )
        }
    }
}

/**
 * Dedicated Navigation Row presenting smart telco navigation actions.
 */
@Composable
fun UssdNavigationRow(
    navOptions: NavigationOptions,
    onSelectOption: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Navigation & Page Controls:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button (◄ Back / 98 or 0)
                if (navOptions.back != null) {
                    NavigationButton(
                        option = navOptions.back,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        accentColor = Color(0xFFFF6D00), // High-visibility Warm Orange
                        testTag = "nav_btn_back",
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectOption(navOptions.back.id) }
                    )
                }

                // Main Menu Button (● Main / 0 or 00)
                if (navOptions.main != null) {
                    NavigationButton(
                        option = navOptions.main,
                        icon = Icons.Default.Home,
                        accentColor = Color(0xFF00B341), // Telecom Green
                        testTag = "nav_btn_main",
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectOption(navOptions.main.id) }
                    )
                }

                // Next Button (Next ► / 99 or # or *)
                if (navOptions.next != null) {
                    NavigationButton(
                        option = navOptions.next,
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        accentColor = Color(0xFF1A73E8), // Primary Blue
                        testTag = "nav_btn_next",
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectOption(navOptions.next.id) }
                    )
                }

                // Exit Button (✕ Exit / 00 or 0)
                if (navOptions.exit != null) {
                    NavigationButton(
                        option = navOptions.exit,
                        icon = Icons.Default.Close,
                        accentColor = Color(0xFFD32F2F), // Warning Red
                        testTag = "nav_btn_exit",
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelectOption(navOptions.exit.id) }
                    )
                }
            }
        }
    }
}

/**
 * Individual Smart Navigation Action Button
 */
@Composable
fun NavigationButton(
    option: UssdMenuOption,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f)),
        modifier = modifier
            .height(52.dp)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = option.label,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "(${option.id})",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/**
 * Individual Selectable Card for a Primary Menu Option
 */
@Composable
fun SelectableMenuOptionCard(
    option: UssdMenuOption,
    onSelect: () -> Unit,
    enabled: Boolean,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onSelect() }
            .testTag("menu_option_card_${option.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp, pressedElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (isCompact) 12.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Key Number Badge (Touch ID)
            Box(
                modifier = Modifier
                    .size(if (isCompact) 32.dp else 36.dp)
                    .background(
                        color = TealContainer,
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.id,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TealPrimaryDark,
                    fontSize = if (option.id.length > 2) 11.sp else 14.sp
                )
            }

            // Option Label and Description
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (isCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (option.description.isNotBlank()) {
                    Text(
                        text = option.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Chevron Indicator
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Muted / Outlined Card for Control Options like 'Back', 'Cancel', 'Next Page'
 */
@Composable
fun ControlOptionCard(
    option: UssdMenuOption,
    onSelect: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isBack = option.isBack || option.id in listOf("0", "00", "98")
    val isNext = option.isNext || option.id in listOf("*", "#", "99")

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onSelect() }
            .testTag("control_option_card_${option.id}"),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = when {
                    isBack -> Icons.AutoMirrored.Filled.ArrowBack
                    isNext -> Icons.AutoMirrored.Filled.ArrowForward
                    else -> Icons.Default.Close
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${option.id}. ${option.label}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

