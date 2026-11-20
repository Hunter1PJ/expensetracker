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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
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
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import com.suguru.expensetracker.presentation.util.AppLanguageManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.annotation.StringRes
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.ai.AiError
import com.suguru.expensetracker.domain.model.AppSettings
import com.suguru.expensetracker.domain.model.DateFormatPreference
import com.suguru.expensetracker.domain.model.ThemeMode
import com.suguru.expensetracker.domain.model.TimeFormatPreference
import com.suguru.expensetracker.domain.model.WeekStart
import com.suguru.expensetracker.presentation.components.ErrorBanner
import com.suguru.expensetracker.presentation.components.ExpenseTrackerBottomSheet
import com.suguru.expensetracker.presentation.components.ExpenseTrackerCard
import com.suguru.expensetracker.presentation.components.IconAvatar
import com.suguru.expensetracker.presentation.components.LoadingState
import com.suguru.expensetracker.presentation.components.SectionHeader
import com.suguru.expensetracker.ui.theme.ExpenseTrackerRadius
import com.suguru.expensetracker.ui.theme.ExpenseTrackerSpacing
import com.suguru.expensetracker.ui.theme.ExpenseTrackerTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button

private enum class ActiveSettingsSheet {
    THEME,
    CURRENCY,
    WEEK_START,
    DATE_FORMAT,
    TIME_FORMAT
}

@StringRes
internal fun settingsErrorMessageResource(error: AiError): Int = when (error) {
    is AiError.InvalidApiKey -> R.string.ai_settings_error_invalid_key
    is AiError.MissingApiKey -> R.string.ai_settings_error_missing_key
    is AiError.RequestTimeout -> R.string.ai_settings_error_timeout
    is AiError.NetworkError -> R.string.ai_settings_error_network
    is AiError.RateLimitExceeded -> R.string.ai_settings_error_rate_limit
    is AiError.ServerError -> R.string.ai_settings_error_server_http
    is AiError.MalformedResponse -> R.string.ai_settings_error_malformed
    is AiError.ExecutionError -> R.string.ai_settings_error_secure_storage
    is AiError.NoTransactionsFound -> R.string.ai_settings_error_generic
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToCategories: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    onNavigateToDataAndStorage: () -> Unit,
    onNavigateToExport: () -> Unit = {},
    createCloudBackupViewModel: ((String) -> CloudBackupViewModel)? = null,
    onShowMessage: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    var activeSheet by remember { mutableStateOf<ActiveSettingsSheet?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showLegacyKeyMigrationDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var selectedLanguage by remember { mutableStateOf(AppLanguageManager.selectedLanguageTag(context)) }
    var apiKeyInput by remember { mutableStateOf("") }
    val apiKeySavedMessage = stringResource(R.string.ai_settings_key_saved)
    val legacyKeyMigratedMessage = stringResource(R.string.ai_legacy_key_migrated)
    val apiKeyVerifiedMessage = stringResource(R.string.ai_settings_key_verified)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                SettingsEvent.ApiKeySaved -> snackbarHostState.showSnackbar(apiKeySavedMessage)
                SettingsEvent.ApiKeyVerified -> {
                    showApiKeyDialog = false
                    snackbarHostState.showSnackbar(apiKeyVerifiedMessage)
                }
                SettingsEvent.LegacyKeyMigrated -> snackbarHostState.showSnackbar(legacyKeyMigratedMessage)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {

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
                    text = stringResource(R.string.settings_preferences_heading),
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
                        message = stringResource(R.string.ui_settings_loading),
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

                    // 1. Account / Cloud Backup Section
                    if (createCloudBackupViewModel != null && onShowMessage != null) {
                        CloudBackupSection(
                            createCloudBackupViewModel = createCloudBackupViewModel,
                            onShowMessage = onShowMessage
                        )
                    }

                    // 2. Financial Structures Section
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
                            SettingsDivider()
                            SettingsRow(
                                title = stringResource(R.string.title_financial_export),
                                subtitle = stringResource(R.string.desc_financial_export),
                                icon = Icons.Default.FileDownload,
                                iconTint = MaterialTheme.colorScheme.primary,
                                onClick = onNavigateToExport,
                                testTag = "settings_item_export"
                            )
                        }
                    }

                    // 2. AI Assistant (BYOK) Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        SectionHeader(
                            title = stringResource(R.string.section_ai_assistant),
                            testTag = "section_ai_assistant"
                        )

                        SettingsGroupCard(testTag = "settings_ai_group_card") {
                            if (!state.isSignedIn) {
                                Text(
                                    text = stringResource(R.string.ai_guest_profile_notice),
                                    modifier = Modifier.padding(horizontal = ExpenseTrackerSpacing.lg, vertical = ExpenseTrackerSpacing.sm),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                            }
                            if (state.hasLegacyApiKey) {
                                Column(
                                    modifier = Modifier.padding(horizontal = ExpenseTrackerSpacing.lg, vertical = ExpenseTrackerSpacing.sm),
                                    verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)
                                ) {
                                    Text(
                                        text = stringResource(R.string.ai_legacy_key_notice),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )
                                    TextButton(onClick = { showLegacyKeyMigrationDialog = true }) {
                                        Text(stringResource(R.string.action_migrate_legacy_key))
                                    }
                                }
                            }
                            SettingsSwitchRow(
                                title = stringResource(R.string.title_ai_feature),
                                subtitle = stringResource(R.string.desc_ai_feature),
                                icon = Icons.Default.AutoAwesome,
                                iconTint = ExpenseTrackerTheme.extendedColors.primaryPurple,
                                checked = state.isAiFeatureEnabled,
                                onCheckedChange = { viewModel.setAiFeatureEnabled(it) },
                                switchTestTag = "toggle_ai_feature_switch",
                                enabled = state.isAiProfileAvailable
                            )

                            if (state.isAiFeatureEnabled && state.isAiProfileAvailable) {
                                SettingsDivider()

                                val keySubtitle = if (state.isAiConfigured) {
                                    val statusNote = when (val kvs = state.keyVerificationState) {
                                        is KeyVerificationState.Idle -> stringResource(R.string.key_status_unverified)
                                        is KeyVerificationState.Verifying -> stringResource(R.string.key_status_verifying)
                                        is KeyVerificationState.Valid -> stringResource(R.string.key_status_verified)
                                        is KeyVerificationState.Invalid -> {
                                            val error = kvs.error
                                            val messageResource = settingsErrorMessageResource(error)
                                            if (error is AiError.ServerError) {
                                                stringResource(messageResource, error.statusCode)
                                            } else {
                                                stringResource(messageResource)
                                            }
                                        }
                                        is KeyVerificationState.TemporaryFailure -> {
                                            val error = kvs.error
                                            val messageResource = settingsErrorMessageResource(error)
                                            if (error is AiError.ServerError) {
                                                stringResource(messageResource, error.statusCode)
                                            } else {
                                                stringResource(messageResource)
                                            }
                                        }
                                    }
                                    "${state.maskedApiKey ?: "••••••••"} • $statusNote"
                                } else {
                                    stringResource(R.string.desc_gemini_api_key_not_configured)
                                }

                                SettingsRow(
                                    title = stringResource(R.string.title_gemini_api_key),
                                    subtitle = keySubtitle,
                                    icon = Icons.Default.Key,
                                    iconTint = when {
                                        state.keyVerificationState is KeyVerificationState.Invalid &&
                                            state.keyVerificationState.error is AiError.InvalidApiKey -> ExpenseTrackerTheme.extendedColors.financialDanger
                                        state.isAiConfigured -> ExpenseTrackerTheme.extendedColors.financialPositive
                                        else -> ExpenseTrackerTheme.extendedColors.primaryPurple
                                    },
                                    trailingText = stringResource(if (state.isAiConfigured) R.string.action_manage else R.string.action_setup),
                                    onClick = {
                                        apiKeyInput = ""
                                        showApiKeyDialog = true
                                    },
                                    testTag = "settings_item_gemini_key"
                                )
                            }
                        }
                    }

                    // 3. Appearance Section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 560.dp),
                        verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)
                    ) {
                        SectionHeader(title = stringResource(R.string.ui_settings_appearance))

                        SettingsGroupCard {
                            SettingsRow(
                                title = stringResource(R.string.title_theme),
                                subtitle = stringResource(R.string.ui_settings_theme_desc),
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
                            title = stringResource(R.string.ui_settings_financial),
                            testTag = "section_preferences"
                        )

                        SettingsGroupCard(testTag = "settings_preferences_card") {
                            SettingsRow(
                                title = stringResource(R.string.title_preferred_currency),
                                subtitle = stringResource(R.string.ui_settings_currency_desc),
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
                        SectionHeader(title = stringResource(R.string.ui_settings_regional))

                        SettingsGroupCard {
                            SettingsRow(
                                title = stringResource(R.string.title_week_start),
                                subtitle = stringResource(R.string.ui_settings_week_anchor),
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
                                subtitle = stringResource(R.string.ui_settings_date_preview),
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
                                subtitle = stringResource(R.string.ui_settings_24hour),
                                icon = Icons.Default.Schedule,
                                iconTint = MaterialTheme.colorScheme.tertiary,
                                trailingText = stringResource(R.string.time_display_24_preview),
                                onClick = {
                                    viewModel.setTimeFormat(TimeFormatPreference.HOUR_24)
                                    activeSheet = null
                                }
                            )
                            SettingsDivider()
                            SettingsRow(
                                title = stringResource(R.string.title_language),
                                subtitle = stringResource(R.string.language_description),
                                icon = Icons.Default.Info,
                                iconTint = MaterialTheme.colorScheme.primary,
                                trailingText = when (selectedLanguage) {
                                    "uz" -> stringResource(R.string.language_uzbek)
                                    "ru" -> stringResource(R.string.language_russian)
                                    else -> stringResource(R.string.language_english)
                                },
                                onClick = { showLanguageDialog = true }
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
                        SectionHeader(title = stringResource(R.string.ui_settings_behavior))

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
                                        text = stringResource(R.string.settings_privacy_copy),
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
                                    text = stringResource(R.string.settings_choose_theme),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = stringResource(R.string.settings_theme_explanation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                                    SettingsOptionCard(
                                        title = stringResource(R.string.theme_system),
                                        subtitle = stringResource(R.string.ui_theme_follow_system),
                                        isSelected = settings.themeMode == ThemeMode.SYSTEM,
                                        onClick = {
                                            viewModel.setThemeMode(ThemeMode.SYSTEM)
                                            activeSheet = null
                                        },
                                        testTag = "theme_system_chip"
                                    )
                                    SettingsOptionCard(
                                        title = stringResource(R.string.theme_light),
                                        subtitle = stringResource(R.string.ui_theme_light_desc),
                                        isSelected = settings.themeMode == ThemeMode.LIGHT,
                                        onClick = {
                                            viewModel.setThemeMode(ThemeMode.LIGHT)
                                            activeSheet = null
                                        },
                                        testTag = "theme_light_chip"
                                    )
                                    SettingsOptionCard(
                                        title = stringResource(R.string.theme_dark),
                                        subtitle = stringResource(R.string.ui_theme_dark_desc),
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
                                    text = stringResource(R.string.title_preferred_currency),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = stringResource(R.string.settings_currency_explanation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                                    SettingsOptionCard(
                                        title = stringResource(R.string.currency_automatic),
                                        subtitle = stringResource(R.string.ui_currency_automatic_desc),
                                        isSelected = settings.preferredCurrencyCode == null,
                                        onClick = {
                                            viewModel.setPreferredCurrency(null)
                                            activeSheet = null
                                        },
                                        testTag = "currency_none_chip"
                                    )
                                    SettingsOptionCard(
                                        title = stringResource(R.string.ui_currency_uzs),
                                        subtitle = stringResource(R.string.ui_currency_uzs_desc),
                                        isSelected = settings.preferredCurrencyCode == "UZS",
                                        onClick = {
                                            viewModel.setPreferredCurrency("UZS")
                                            activeSheet = null
                                        },
                                        testTag = "currency_uzs_chip"
                                    )
                                    SettingsOptionCard(
                                        title = stringResource(R.string.ui_currency_usd),
                                        subtitle = stringResource(R.string.ui_currency_usd_desc),
                                        isSelected = settings.preferredCurrencyCode == "USD",
                                        onClick = {
                                            viewModel.setPreferredCurrency("USD")
                                            activeSheet = null
                                        },
                                        testTag = "currency_usd_chip"
                                    )
                                    SettingsOptionCard(
                                        title = stringResource(R.string.ui_currency_eur),
                                        subtitle = stringResource(R.string.ui_currency_eur_desc),
                                        isSelected = settings.preferredCurrencyCode == "EUR",
                                        onClick = {
                                            viewModel.setPreferredCurrency("EUR")
                                            activeSheet = null
                                        },
                                        testTag = "currency_eur_chip"
                                    )
                                    SettingsOptionCard(
                                        title = stringResource(R.string.ui_currency_krw),
                                        subtitle = stringResource(R.string.ui_currency_krw_desc),
                                        isSelected = settings.preferredCurrencyCode == "KRW",
                                        onClick = {
                                            viewModel.setPreferredCurrency("KRW")
                                            activeSheet = null
                                        },
                                        testTag = "currency_krw_chip"
                                    )
                                    SettingsOptionCard(
                                        title = stringResource(R.string.ui_currency_jpy),
                                        subtitle = stringResource(R.string.ui_currency_jpy_desc),
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
                                    text = stringResource(R.string.settings_week_starts),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = stringResource(R.string.settings_week_charts_explanation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                                    SettingsOptionCard(
                                        title = stringResource(R.string.week_start_monday),
                                        subtitle = stringResource(R.string.ui_week_monday_desc),
                                        isSelected = settings.weekStart == WeekStart.MONDAY,
                                        onClick = {
                                            viewModel.setWeekStart(WeekStart.MONDAY)
                                            activeSheet = null
                                        },
                                        testTag = "week_monday_chip"
                                    )
                                    SettingsOptionCard(
                                        title = stringResource(R.string.week_start_sunday),
                                        subtitle = stringResource(R.string.ui_week_sunday_desc),
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
                                    text = stringResource(R.string.title_date_format),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = stringResource(R.string.settings_date_explanation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                                    SettingsOptionCard(
                                        title = stringResource(R.string.date_format_system),
                                        subtitle = stringResource(R.string.ui_locale_desc),
                                        isSelected = settings.dateFormat == DateFormatPreference.SYSTEM_DEFAULT,
                                        onClick = {
                                            viewModel.setDateFormat(DateFormatPreference.SYSTEM_DEFAULT)
                                            activeSheet = null
                                        },
                                        testTag = "date_system_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "11/09/2026",
                                        subtitle = stringResource(R.string.ui_date_ddmmyyyy),
                                        isSelected = settings.dateFormat == DateFormatPreference.DD_MM_YYYY,
                                        onClick = {
                                            viewModel.setDateFormat(DateFormatPreference.DD_MM_YYYY)
                                            activeSheet = null
                                        },
                                        testTag = "date_dmy_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "09/11/2026",
                                        subtitle = stringResource(R.string.ui_date_mmddyyyy),
                                        isSelected = settings.dateFormat == DateFormatPreference.MM_DD_YYYY,
                                        onClick = {
                                            viewModel.setDateFormat(DateFormatPreference.MM_DD_YYYY)
                                            activeSheet = null
                                        },
                                        testTag = "date_mdy_chip"
                                    )
                                    SettingsOptionCard(
                                        title = "2026-09-11",
                                        subtitle = stringResource(R.string.ui_date_iso),
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
                                    text = stringResource(R.string.title_time_format),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = stringResource(R.string.settings_time_explanation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ExpenseTrackerTheme.extendedColors.textSecondary
                                )
                                Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.lg))
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.sm)) {
                                    SettingsOptionCard(
                                        title = stringResource(R.string.time_display_24_preview),
                                        subtitle = stringResource(R.string.time_format_24),
                                        isSelected = true,
                                        onClick = {
                                            viewModel.setTimeFormat(TimeFormatPreference.HOUR_24)
                                            activeSheet = null
                                        },
                                        testTag = "time_24_chip"
                                    )
                                }
                            }
                        }

                        null -> Unit
                    }

                    if (showLanguageDialog) {
                        AlertDialog(
                            onDismissRequest = { showLanguageDialog = false },
                            title = { Text(stringResource(R.string.language_change_title)) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.xs)) {
                                    listOf(
                                        "uz" to R.string.language_uzbek,
                                        "en" to R.string.language_english,
                                        "ru" to R.string.language_russian
                                    ).forEach { (tag, labelRes) ->
                                        TextButton(
                                            onClick = {
                                                AppLanguageManager.setLanguage(context, tag)
                                                selectedLanguage = tag
                                                showLanguageDialog = false
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = stringResource(labelRes),
                                                fontWeight = if (selectedLanguage == tag) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showLanguageDialog = false }) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }
                        )
                    }

                    if (showApiKeyDialog) {
                        AlertDialog(
                            onDismissRequest = {
                                if (state.keyVerificationState is KeyVerificationState.Verifying) {
                                    viewModel.cancelVerification()
                                }
                                showApiKeyDialog = false
                            },
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.dialog_api_key_title),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    IconButton(
                                        onClick = { showApiKeyDialog = false },
                                        modifier = Modifier.testTag("close_api_key_dialog_button")
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.ui_close))
                                    }
                                }
                            },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(ExpenseTrackerSpacing.md)) {
                                    Text(
                                        text = stringResource(R.string.dialog_api_key_desc),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ExpenseTrackerTheme.extendedColors.textSecondary
                                    )

                                    OutlinedTextField(
                                        value = apiKeyInput,
                                        onValueChange = { apiKeyInput = it },
                                        placeholder = {
                                            Text(if (state.isAiConfigured) state.maskedApiKey ?: "••••••••" else stringResource(R.string.hint_api_key))
                                        },
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("gemini_api_key_input")
                                    )

                                    if (state.isAiConfigured) {
                                        TextButton(
                                            onClick = {
                                                viewModel.deleteGeminiApiKey()
                                                showApiKeyDialog = false
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                                contentColor = ExpenseTrackerTheme.extendedColors.financialDanger
                                            ),
                                            modifier = Modifier.testTag("delete_api_key_button")
                                        ) {
                                            Text(stringResource(R.string.action_delete_key))
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (apiKeyInput.isNotBlank()) {
                                            viewModel.saveGeminiApiKey(apiKeyInput)
                                        }
                                        showApiKeyDialog = false
                                    },
                                    enabled = apiKeyInput.isNotBlank(),
                                    modifier = Modifier.testTag("save_api_key_button")
                                ) {
                                    Text(stringResource(R.string.action_save_key))
                                }
                            },
                        )
                    }
                    if (showLegacyKeyMigrationDialog) {
                        AlertDialog(
                            onDismissRequest = { showLegacyKeyMigrationDialog = false },
                            title = { Text(stringResource(R.string.dialog_migrate_legacy_key_title)) },
                            text = { Text(stringResource(R.string.dialog_migrate_legacy_key_message)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showLegacyKeyMigrationDialog = false
                                    viewModel.migrateLegacyGeminiApiKey()
                                }) { Text(stringResource(R.string.action_migrate_legacy_key)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showLegacyKeyMigrationDialog = false }) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }
                        )
                    }

                }
            }

            Spacer(modifier = Modifier.height(ExpenseTrackerSpacing.xxl))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .testTag("settings_snackbar_host")
        )
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
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
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
            enabled = enabled,
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
                    contentDescription = stringResource(R.string.ui_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


