package com.suguru.expensetracker.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.AppSettings
import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import com.suguru.expensetracker.domain.model.WeekStart
import com.suguru.expensetracker.presentation.components.BackgroundGlowDecoration
import com.suguru.expensetracker.presentation.components.ErrorBanner
import com.suguru.expensetracker.presentation.components.ExpenseTrackerBottomSheet
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.components.IconAvatar
import com.suguru.expensetracker.presentation.components.LoadingState
import com.suguru.expensetracker.presentation.components.SectionHeader
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

private enum class ActiveSettingsSheet {
    THEME,
    CURRENCY,
    WEEK_START,
    DATE_FORMAT,
    TIME_FORMAT
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var activeSheet by remember { mutableStateOf<ActiveSettingsSheet?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        BackgroundGlowDecoration(alpha = 0.08f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = ExpenseTrackerSpacing.screenHorizontal,
                    vertical = ExpenseTrackerSpacing.screenVertical
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xl)
        ) {
            // Screen Header with Eyebrow
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "PREFERENCES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = stringResource(R.string.title_settings),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                )
            }

            when (val state = uiState) {
                is SettingsUiState.Loading -> {
                    LoadingState(
                        message = "Loading preferences...",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ExpenseTrackerSpacing.xxl)
                            .testTag("settings_loading")
                    )
                }

                is SettingsUiState.Error -> {
                    ErrorBanner(
                        message = state.message,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp)
                    )
                }

                is SettingsUiState.Success -> {
                    val settings = state.settings

                    // 1. Management Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        SectionHeader(
                            title = stringResource(R.string.section_financial_setup),
                            testTag = "section_financial_setup"
                        )

                        SettingsGroupCard {
                            SettingsRow(
                                title = stringResource(R.string.title_account_management),
                                subtitle = stringResource(R.string.settings_accounts_desc),
                                icon = Icons.Default.AccountBalanceWallet,
                                iconTint = MaterialTheme.colorScheme.primary,
                                onClick = onNavigateToAccounts,
                                testTag = "settings_item_accounts"
                            )
                            SettingsDivider()
                            SettingsRow(
                                title = stringResource(R.string.title_category_management),
                                subtitle = stringResource(R.string.settings_categories_desc),
                                icon = Icons.Default.Category,
                                iconTint = MaterialTheme.colorScheme.secondary,
                                onClick = onNavigateToCategories,
                                testTag = "settings_item_categories"
                            )
                            SettingsDivider()
                            SettingsRow(
                                title = stringResource(R.string.title_recurring_transactions),
                                subtitle = stringResource(R.string.settings_recurring_desc),
                                icon = Icons.Default.Repeat,
                                iconTint = MaterialTheme.colorScheme.tertiary,
                                onClick = onNavigateToRecurring,
                                testTag = "settings_item_recurring"
                            )
                            SettingsDivider()
                            SettingsRow(
                                title = stringResource(R.string.title_data_storage),
                                subtitle = stringResource(R.string.desc_data_storage),
                                icon = Icons.Default.Storage,
                                iconTint = ExpenseTrackerTheme.extendedColors.primaryPurple,
                                onClick = onNavigateToDataAndStorage,
                                testTag = "settings_item_data_storage"
                            )
                        }
                    }

                    // 2. Appearance Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        SectionHeader(title = "Appearance")

                        SettingsGroupCard {
                            SettingsRow(
                                title = stringResource(R.string.title_theme),
                                subtitle = "App appearance theme",
                                icon = Icons.Default.DarkMode,
                                iconTint = MaterialTheme.colorScheme.primary,
                                trailingText = when (settings.themeMode) {
                                    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                    ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                },
                                onClick = { activeSheet = ActiveSettingsSheet.THEME }
                            )
                        }
                    }

                    // 3. Financial Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        SectionHeader(
                            title = "Financial",
                            testTag = "section_preferences"
                        )

                        SettingsGroupCard(testTag = "settings_preferences_card") {
                            SettingsRow(
                                title = stringResource(R.string.title_preferred_currency),
                                subtitle = "Used as a default, not for conversion",
                                icon = Icons.Default.Payments,
                                iconTint = MaterialTheme.colorScheme.primary,
                                trailingText = settings.preferredCurrencyCode ?: stringResource(R.string.currency_automatic),
                                onClick = { activeSheet = ActiveSettingsSheet.CURRENCY }
                            )
                            SettingsDivider()
                            SettingsSwitchRow(
                                title = stringResource(R.string.title_show_currency_code),
                                subtitle = stringResource(R.string.desc_show_currency_code),
                                icon = Icons.Default.Security,
                                iconTint = MaterialTheme.colorScheme.secondary,
                                checked = settings.showCurrencyCode,
                                onCheckedChange = { viewModel.setShowCurrencyCode(it) },
                                switchTestTag = "show_currency_code_switch"
                            )
                        }
                    }

                    // 4. Regional Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        SectionHeader(title = "Regional")

                        SettingsGroupCard {
                            SettingsRow(
                                title = stringResource(R.string.title_week_start),
                                subtitle = "Weekly budget calculation anchor",
                                icon = Icons.Default.CalendarMonth,
                                iconTint = MaterialTheme.colorScheme.primary,
                                trailingText = if (settings.weekStart == WeekStart.MONDAY) {
                                    stringResource(R.string.week_start_monday)
                                } else {
                                    stringResource(R.string.week_start_sunday)
                                },
                                onClick = { activeSheet = ActiveSettingsSheet.WEEK_START }
                            )
                            SettingsDivider()
                            SettingsRow(
                                title = stringResource(R.string.title_date_format),
                                subtitle = "Date format preview",
                                icon = Icons.Default.DateRange,
                                iconTint = MaterialTheme.colorScheme.secondary,
                                trailingText = when (settings.dateFormat) {
                                    DateFormatPreference.SYSTEM_DEFAULT -> stringResource(R.string.date_format_system)
                                    DateFormatPreference.DD_MM_YYYY -> "11/09/2026"
                                    DateFormatPreference.MM_DD_YYYY -> "09/11/2026"
                                    DateFormatPreference.YYYY_MM_DD -> "2026-09-11"
                                },
                                onClick = { activeSheet = ActiveSettingsSheet.DATE_FORMAT }
                            )
                            SettingsDivider()
                            SettingsRow(
                                title = stringResource(R.string.title_time_format),
                                subtitle = "Time format preview",
                                icon = Icons.Default.Schedule,
                                iconTint = MaterialTheme.colorScheme.tertiary,
                                trailingText = when (settings.timeFormat) {
                                    TimeFormatPreference.SYSTEM_DEFAULT -> stringResource(R.string.time_format_system)
                                    TimeFormatPreference.HOUR_24 -> "16:45 (24h)"
                                    TimeFormatPreference.HOUR_12 -> "4:45 PM (12h)"
                                },
                                onClick = { activeSheet = ActiveSettingsSheet.TIME_FORMAT }
                            )
                        }
                    }

                    // 5. Behavior Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        SectionHeader(title = "Behavior")

                        SettingsGroupCard {
                            SettingsSwitchRow(
                                title = stringResource(R.string.title_confirm_delete),
                                subtitle = stringResource(R.string.desc_confirm_delete),
                                icon = Icons.Default.DeleteOutline,
                                iconTint = ExpenseTrackerTheme.extendedColors.financialDanger,
                                checked = settings.confirmBeforeDelete,
                                onCheckedChange = { viewModel.setConfirmBeforeDelete(it) },
                                switchTestTag = "confirm_delete_switch"
                            )
                        }
                    }

                    // 6. About Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        SectionHeader(
                            title = stringResource(R.string.section_about),
                            testTag = "section_about"
                        )

                        ExpenseTrackerCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = ExpenseTrackerRadius.card,
                            containerColor = ExpenseTrackerTheme.extendedColors.surface,
                            borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
                            contentPadding = PaddingValues(ExpenseTrackerSpacing.lg),
                            testTag = "settings_about_card"
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
                            ) {
                                IconAvatar(
                                    icon = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    size = 44.dp
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xxs)
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_name),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "Offline-first • Your financial data stays on this device unless you choose to export it.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${stringResource(R.string.app_version_label)}: ${state.appVersionName} (${state.appVersionCode})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.testTag("settings_version_info")
                                    )
                                }
                            }
                        }
                    }

                    // Selection Bottom Sheets
                    when (activeSheet) {
                        ActiveSettingsSheet.THEME -> {
                            ExpenseTrackerBottomSheet(
                                onDismissRequest = { activeSheet = null }
                            ) {
                                Text(
                                    text = "Choose Theme",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Select your preferred visual appearance",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                                    SettingsOptionCard(
                                        title = stringResource(R.string.theme_system),
                                        subtitle = "Follow system light/dark appearance",
                                        isSelected = settings.themeMode == ThemeMode.SYSTEM,
                                        onClick = {
                                            viewModel.setThemeMode(ThemeMode.SYSTEM)
                                            activeSheet = null
                                        },
                                        testTag = "theme_system_chip"
                                    )
                                    SettingsOptionCard(
                                        title = stringResource(R.string.theme_light),
                                        subtitle = "Clean light interface",
                                        isSelected = settings.themeMode == ThemeMode.LIGHT,
                                        onClick = {
                                            viewModel.setThemeMode(ThemeMode.LIGHT)
                                            activeSheet = null
                                        },
                                        testTag = "theme_light_chip"
                                    )
                                    SettingsOptionCard(
                                        title = stringResource(R.string.theme_dark),
                                        subtitle = "Midnight indigo dark interface",
                                        isSelected = settings.themeMode == ThemeMode.DARK,
                                        onClick = {
                                            viewModel.setThemeMode(ThemeMode.DARK)
                                            activeSheet = null
                                        },
                                        testTag = "theme_dark_chip"
                                    )
                                }
                            }
                        }

                        ActiveSettingsSheet.CURRENCY -> {
                            ExpenseTrackerBottomSheet(
                                onDismissRequest = { activeSheet = null }
                            ) {
                                Text(
                                    text = "Preferred Currency",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Used as initial default for new accounts. Does not convert existing balances.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                                    SettingsOptionCard(
                                        title = stringResource(R.string.currency_automatic),
                                        subtitle = "Automatic (use account or device currency)",
                                        isSelected = settings.preferredCurrencyCode == null,
                                        onClick = {
                                            viewModel.setPreferredCurrency(null)
                                            activeSheet = null
                                        },
                                        testTag = "currency_none_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "UZS — Uzbekistani Som",
                                        subtitle = "Uzbekistan national currency",
                                        isSelected = settings.preferredCurrencyCode == "UZS",
                                        onClick = {
                                            viewModel.setPreferredCurrency("UZS")
                                            activeSheet = null
                                        },
                                        testTag = "currency_uzs_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "USD — US Dollar",
                                        subtitle = "United States currency",
                                        isSelected = settings.preferredCurrencyCode == "USD",
                                        onClick = {
                                            viewModel.setPreferredCurrency("USD")
                                            activeSheet = null
                                        },
                                        testTag = "currency_usd_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "EUR — Euro",
                                        subtitle = "European Union currency",
                                        isSelected = settings.preferredCurrencyCode == "EUR",
                                        onClick = {
                                            viewModel.setPreferredCurrency("EUR")
                                            activeSheet = null
                                        },
                                        testTag = "currency_eur_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "KRW — South Korean Won",
                                        subtitle = "South Korea currency",
                                        isSelected = settings.preferredCurrencyCode == "KRW",
                                        onClick = {
                                            viewModel.setPreferredCurrency("KRW")
                                            activeSheet = null
                                        },
                                        testTag = "currency_krw_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "JPY — Japanese Yen",
                                        subtitle = "Japan currency",
                                        isSelected = settings.preferredCurrencyCode == "JPY",
                                        onClick = {
                                            viewModel.setPreferredCurrency("JPY")
                                            activeSheet = null
                                        },
                                        testTag = "currency_jpy_chip"
                                    )
                                }
                            }
                        }

                        ActiveSettingsSheet.WEEK_START -> {
                            ExpenseTrackerBottomSheet(
                                onDismissRequest = { activeSheet = null }
                            ) {
                                Text(
                                    text = "Week Starts On",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Anchor day used for weekly budgets and charts",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                                    SettingsOptionCard(
                                        title = stringResource(R.string.week_start_monday),
                                        subtitle = "Standard ISO calendar week start",
                                        isSelected = settings.weekStart == WeekStart.MONDAY,
                                        onClick = {
                                            viewModel.setWeekStart(WeekStart.MONDAY)
                                            activeSheet = null
                                        },
                                        testTag = "week_monday_chip"
                                    )
                                    SettingsOptionCard(
                                        title = stringResource(R.string.week_start_sunday),
                                        subtitle = "Traditional calendar week start",
                                        isSelected = settings.weekStart == WeekStart.SUNDAY,
                                        onClick = {
                                            viewModel.setWeekStart(WeekStart.SUNDAY)
                                            activeSheet = null
                                        },
                                        testTag = "week_sunday_chip"
                                    )
                                }
                            }
                        }

                        ActiveSettingsSheet.DATE_FORMAT -> {
                            ExpenseTrackerBottomSheet(
                                onDismissRequest = { activeSheet = null }
                            ) {
                                Text(
                                    text = "Date Format",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Display format for dates in transaction records",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                                    SettingsOptionCard(
                                        title = stringResource(R.string.date_format_system),
                                        subtitle = "Follows Android system locale settings",
                                        isSelected = settings.dateFormat == DateFormatPreference.SYSTEM_DEFAULT,
                                        onClick = {
                                            viewModel.setDateFormat(DateFormatPreference.SYSTEM_DEFAULT)
                                            activeSheet = null
                                        },
                                        testTag = "date_system_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "11/09/2026",
                                        subtitle = "DD/MM/YYYY (Day / Month / Year)",
                                        isSelected = settings.dateFormat == DateFormatPreference.DD_MM_YYYY,
                                        onClick = {
                                            viewModel.setDateFormat(DateFormatPreference.DD_MM_YYYY)
                                            activeSheet = null
                                        },
                                        testTag = "date_dmy_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "09/11/2026",
                                        subtitle = "MM/DD/YYYY (Month / Day / Year)",
                                        isSelected = settings.dateFormat == DateFormatPreference.MM_DD_YYYY,
                                        onClick = {
                                            viewModel.setDateFormat(DateFormatPreference.MM_DD_YYYY)
                                            activeSheet = null
                                        },
                                        testTag = "date_mdy_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "2026-09-11",
                                        subtitle = "YYYY-MM-DD (ISO standard format)",
                                        isSelected = settings.dateFormat == DateFormatPreference.YYYY_MM_DD,
                                        onClick = {
                                            viewModel.setDateFormat(DateFormatPreference.YYYY_MM_DD)
                                            activeSheet = null
                                        },
                                        testTag = "date_ymd_chip"
                                    )
                                }
                            }
                        }

                        ActiveSettingsSheet.TIME_FORMAT -> {
                            ExpenseTrackerBottomSheet(
                                onDismissRequest = { activeSheet = null }
                            ) {
                                Text(
                                    text = "Time Format",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Display format for timestamps in transaction records",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                                    SettingsOptionCard(
                                        title = stringResource(R.string.time_format_system),
                                        subtitle = "Follows Android system 24-hour setting",
                                        isSelected = settings.timeFormat == TimeFormatPreference.SYSTEM_DEFAULT,
                                        onClick = {
                                            viewModel.setTimeFormat(TimeFormatPreference.SYSTEM_DEFAULT)
                                            activeSheet = null
                                        },
                                        testTag = "time_system_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "16:45",
                                        subtitle = "24-Hour clock display",
                                        isSelected = settings.timeFormat == TimeFormatPreference.HOUR_24,
                                        onClick = {
                                            viewModel.setTimeFormat(TimeFormatPreference.HOUR_24)
                                            activeSheet = null
                                        },
                                        testTag = "time_24_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "4:45 PM",
                                        subtitle = "12-Hour clock display with AM/PM",
                                        isSelected = settings.timeFormat == TimeFormatPreference.HOUR_12,
                                        onClick = {
                                            viewModel.setTimeFormat(TimeFormatPreference.HOUR_12)
                                            activeSheet = null
                                        },
                                        testTag = "time_12_chip"
                                    )
                                }
                            }
                        }

                        null -> Unit
                    }
                }
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))
        }
    }
}

@Composable
private fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ExpenseTrackerCard(
        modifier = modifier.fillMaxWidth(),
        shape = ExpenseTrackerRadius.card,
        containerColor = ExpenseTrackerTheme.extendedColors.surface,
        borderColor = ExpenseTrackerTheme.extendedColors.borderSubtle,
        contentPadding = PaddingValues(vertical = ExpenseTrackerSpacing.xs),
        testTag = testTag,
        content = content
    )
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = ExpenseTrackerTheme.extendedColors.borderSubtle.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 64.dp, end = ExpenseTrackerSpacing.lg)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    modifier: Modifier = Modifier
) {
    val clickableModifier = if (onClick != null) {
        modifier.clickable(onClick = onClick)
    } else {
        modifier
    }
    val finalModifier = if (testTag != null) clickableModifier.testTag(testTag) else clickableModifier

    Row(
        modifier = finalModifier
            .fillMaxWidth()
            .padding(
                horizontal = ExpenseTrackerSpacing.lg,
                vertical = ExpenseTrackerSpacing.md
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
        ) {
            IconAvatar(
                icon = icon,
                contentDescription = null,
                tint = iconTint,
                size = 38.dp
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
        ) {
            if (trailingText != null) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            if (onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ExpenseTrackerTheme.extendedColors.textMuted,
                    modifier = Modifier.size(ExpenseTrackerTheme.iconSize.sm)
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchTestTag: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(
                horizontal = ExpenseTrackerSpacing.lg,
                vertical = ExpenseTrackerSpacing.md
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)
        ) {
            IconAvatar(
                icon = icon,
                contentDescription = null,
                tint = iconTint,
                size = 38.dp
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = ExpenseTrackerTheme.extendedColors.textSecondary,
                uncheckedTrackColor = ExpenseTrackerTheme.extendedColors.surfaceHighlight,
                uncheckedBorderColor = ExpenseTrackerTheme.extendedColors.borderSubtle
            ),
            modifier = Modifier.testTag(switchTestTag)
        )
    }
}

@Composable
private fun SettingsOptionCard(
    title: String,
    subtitle: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        ExpenseTrackerTheme.extendedColors.borderSubtle
    }
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        ExpenseTrackerTheme.extendedColors.surfaceElevated
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = ExpenseTrackerRadius.card,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = ExpenseTrackerSpacing.lg,
                    vertical = ExpenseTrackerSpacing.md
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                    )
                }
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

