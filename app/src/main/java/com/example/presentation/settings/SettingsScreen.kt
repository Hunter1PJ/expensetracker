package com.example.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AppSettings
import com.example.domain.model.DateFormatPreference
import com.example.domain.model.ThemeMode
import com.example.domain.model.TimeFormatPreference
import com.example.domain.model.WeekStart
import com.example.presentation.components.ExpenseTrackerCard
import com.example.presentation.components.SectionHeader
import com.example.ui.theme.ExpenseTrackerRadius
import com.example.ui.theme.ExpenseTrackerSpacing
import com.example.ui.theme.ExpenseTrackerTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToAccounts: () -> Unit,
    onNavigateToCategories: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    onNavigateToDataAndStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                horizontal = ExpenseTrackerSpacing.screenHorizontal,
                vertical = ExpenseTrackerSpacing.screenVertical
            )
            .testTag("settings_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xl)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 540.dp),
            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
        ) {
            SectionHeader(
                title = stringResource(R.string.title_settings),
                subtitle = stringResource(R.string.settings_subtitle)
            )
        }

        when (val state = uiState) {
            is SettingsUiState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(vertical = ExpenseTrackerSpacing.xxl)
                        .testTag("settings_loading")
                )
            }
            is SettingsUiState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = ExpenseTrackerSpacing.md)
                )
            }
            is SettingsUiState.Success -> {
                val settings = state.settings

                // Financial Structure Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                ) {
                    SectionHeader(
                        title = stringResource(R.string.section_financial_setup),
                        testTag = "section_financial_setup"
                    )

                    SettingsNavigationCard(
                        title = stringResource(R.string.title_account_management),
                        subtitle = stringResource(R.string.settings_accounts_desc),
                        icon = Icons.Default.AccountBalanceWallet,
                        onClick = onNavigateToAccounts,
                        testTag = "settings_item_accounts"
                    )

                    SettingsNavigationCard(
                        title = stringResource(R.string.title_category_management),
                        subtitle = stringResource(R.string.settings_categories_desc),
                        icon = Icons.Default.Category,
                        onClick = onNavigateToCategories,
                        testTag = "settings_item_categories"
                    )

                    SettingsNavigationCard(
                        title = stringResource(R.string.title_recurring_transactions),
                        subtitle = stringResource(R.string.settings_recurring_desc),
                        icon = Icons.Default.Repeat,
                        onClick = onNavigateToRecurring,
                        testTag = "settings_item_recurring"
                    )

                    SettingsNavigationCard(
                        title = stringResource(R.string.title_data_storage),
                        subtitle = stringResource(R.string.desc_data_storage),
                        icon = Icons.Default.Storage,
                        onClick = onNavigateToDataAndStorage,
                        testTag = "settings_item_data_storage"
                    )
                }

                // Preferences / App Configuration Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                ) {
                    SectionHeader(
                        title = stringResource(R.string.section_preferences),
                        testTag = "section_preferences"
                    )

                    ExpenseTrackerCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpenseTrackerRadius.card,
                        containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                        borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
                        testTag = "settings_preferences_card"
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.lg)
                        ) {
                            // 1. Theme Selection
                            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                                Text(
                                    text = stringResource(R.string.title_theme),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                                ) {
                                    FilterChip(
                                        selected = settings.themeMode == ThemeMode.SYSTEM,
                                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                                        label = { Text(stringResource(R.string.theme_system)) },
                                        modifier = Modifier.testTag("theme_system_chip")
                                    )
                                    FilterChip(
                                        selected = settings.themeMode == ThemeMode.LIGHT,
                                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                                        label = { Text(stringResource(R.string.theme_light)) },
                                        modifier = Modifier.testTag("theme_light_chip")
                                    )
                                    FilterChip(
                                        selected = settings.themeMode == ThemeMode.DARK,
                                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                                        label = { Text(stringResource(R.string.theme_dark)) },
                                        modifier = Modifier.testTag("theme_dark_chip")
                                    )
                                }
                            }

                            // 2. Preferred Currency Selection
                            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                                Text(
                                    text = stringResource(R.string.title_preferred_currency),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                                ) {
                                    FilterChip(
                                        selected = settings.preferredCurrencyCode == null,
                                        onClick = { viewModel.setPreferredCurrency(null) },
                                        label = { Text(stringResource(R.string.currency_automatic)) },
                                        modifier = Modifier.testTag("currency_none_chip")
                                    )
                                    listOf("USD", "EUR", "UZS", "KRW", "JPY").forEach { currency ->
                                        FilterChip(
                                            selected = settings.preferredCurrencyCode == currency,
                                            onClick = { viewModel.setPreferredCurrency(currency) },
                                            label = { Text(currency) },
                                            modifier = Modifier.testTag("currency_${currency.lowercase()}_chip")
                                        )
                                    }
                                }
                            }

                            // 3. Week Start Selection
                            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                                Text(
                                    text = stringResource(R.string.title_week_start),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                                ) {
                                    FilterChip(
                                        selected = settings.weekStart == WeekStart.MONDAY,
                                        onClick = { viewModel.setWeekStart(WeekStart.MONDAY) },
                                        label = { Text(stringResource(R.string.week_start_monday)) },
                                        modifier = Modifier.testTag("week_monday_chip")
                                    )
                                    FilterChip(
                                        selected = settings.weekStart == WeekStart.SUNDAY,
                                        onClick = { viewModel.setWeekStart(WeekStart.SUNDAY) },
                                        label = { Text(stringResource(R.string.week_start_sunday)) },
                                        modifier = Modifier.testTag("week_sunday_chip")
                                    )
                                }
                            }

                            // 4. Date Format Selection
                            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                                Text(
                                    text = stringResource(R.string.title_date_format),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                                ) {
                                    FilterChip(
                                        selected = settings.dateFormat == DateFormatPreference.SYSTEM_DEFAULT,
                                        onClick = { viewModel.setDateFormat(DateFormatPreference.SYSTEM_DEFAULT) },
                                        label = { Text(stringResource(R.string.date_format_system)) },
                                        modifier = Modifier.testTag("date_system_chip")
                                    )
                                    FilterChip(
                                        selected = settings.dateFormat == DateFormatPreference.DD_MM_YYYY,
                                        onClick = { viewModel.setDateFormat(DateFormatPreference.DD_MM_YYYY) },
                                        label = { Text("DD/MM/YYYY") },
                                        modifier = Modifier.testTag("date_dmy_chip")
                                    )
                                    FilterChip(
                                        selected = settings.dateFormat == DateFormatPreference.MM_DD_YYYY,
                                        onClick = { viewModel.setDateFormat(DateFormatPreference.MM_DD_YYYY) },
                                        label = { Text("MM/DD/YYYY") },
                                        modifier = Modifier.testTag("date_mdy_chip")
                                    )
                                    FilterChip(
                                        selected = settings.dateFormat == DateFormatPreference.YYYY_MM_DD,
                                        onClick = { viewModel.setDateFormat(DateFormatPreference.YYYY_MM_DD) },
                                        label = { Text("YYYY/MM/DD") },
                                        modifier = Modifier.testTag("date_ymd_chip")
                                    )
                                }
                            }

                            // 5. Time Format Selection
                            Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                                Text(
                                    text = stringResource(R.string.title_time_format),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                                ) {
                                    FilterChip(
                                        selected = settings.timeFormat == TimeFormatPreference.SYSTEM_DEFAULT,
                                        onClick = { viewModel.setTimeFormat(TimeFormatPreference.SYSTEM_DEFAULT) },
                                        label = { Text(stringResource(R.string.time_format_system)) },
                                        modifier = Modifier.testTag("time_system_chip")
                                    )
                                    FilterChip(
                                        selected = settings.timeFormat == TimeFormatPreference.HOUR_24,
                                        onClick = { viewModel.setTimeFormat(TimeFormatPreference.HOUR_24) },
                                        label = { Text(stringResource(R.string.time_format_24)) },
                                        modifier = Modifier.testTag("time_24_chip")
                                    )
                                    FilterChip(
                                        selected = settings.timeFormat == TimeFormatPreference.HOUR_12,
                                        onClick = { viewModel.setTimeFormat(TimeFormatPreference.HOUR_12) },
                                        label = { Text(stringResource(R.string.time_format_12)) },
                                        modifier = Modifier.testTag("time_12_chip")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // 6. Toggles (Show Currency Code)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.title_show_currency_code),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.desc_show_currency_code),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                }
                                Switch(
                                    checked = settings.showCurrencyCode,
                                    onCheckedChange = { viewModel.setShowCurrencyCode(it) },
                                    modifier = Modifier.testTag("show_currency_code_switch")
                                )
                            }

                            // 7. Toggles (Confirm Before Delete)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.title_confirm_delete),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.desc_confirm_delete),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                }
                                Switch(
                                    checked = settings.confirmBeforeDelete,
                                    onCheckedChange = { viewModel.setConfirmBeforeDelete(it) },
                                    modifier = Modifier.testTag("confirm_delete_switch")
                                )
                            }
                        }
                    }
                }

                // About / Version Info Section
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 540.dp),
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                ) {
                    SectionHeader(
                        title = stringResource(R.string.section_about),
                        testTag = "section_about"
                    )

                    ExpenseTrackerCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ExpenseTrackerRadius.card,
                        containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
                        borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
                        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
                        testTag = "settings_about_card"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                            )
                            Column(
                                verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xxs)
                            ) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = stringResource(R.string.about_app_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${stringResource(R.string.app_version_label)}: ${state.appVersionName} (${state.appVersionCode})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary,
                                    modifier = Modifier.testTag("settings_version_info")
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))
    }
}

@Composable
private fun SettingsNavigationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String
) {
    ExpenseTrackerCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.cardBackground,
        borderColor = ExpenseTrackerTheme.extendedColors.cardBorder,
        contentPadding = PaddingValues(ExpenseTrackerSpacing.lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xxs)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ExpenseTrackerTheme.extendedColors.textSecondary,
                modifier = Modifier.size(ExpenseTrackerTheme.iconSize.md)
            )
        }
    }
}
