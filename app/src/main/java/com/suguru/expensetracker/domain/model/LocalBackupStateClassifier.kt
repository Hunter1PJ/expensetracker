package com.suguru.expensetracker.domain.model

/**
 * Pure domain classifier that determines whether an [ExpenseTrackerBackup] represents an
 * effectively empty, freshly provisioned local installation or contains meaningful user data.
 *
 * ## Conservative Classification Policy
 * This classifier protects against accidental financial data loss during cloud account linking:
 * - Returning `true` allows an automatic cloud restore to replace local state without prompting.
 * - Returning `false` treats local state as containing meaningful user data or customizations,
 *   requiring explicit conflict resolution before overwriting local state.
 *
 * Because fresh installations automatically seed baseline rows (one internal `"default"` account,
 * 14 system categories, and default [AppSettings]), an unedited local database is never literally
 * row-empty. Conversely, any deviation from the verified fresh-install baseline—or any missing
 * baseline marker where empty collections would otherwise pass vacuous `all { ... }` checks—is
 * conservatively classified as `false`.
 *
 * ## Model Limitations
 * - Categories do not currently carry an immutable system key (unlike `Account.internalKey`).
 *   Baseline verification therefore checks `isSystem == true`, `isArchived == false`, and an
 *   exact 14-item match against the known seeded `(name, type, iconName, colorHex)` definitions.
 *   If a future app version changes seeded category definitions without updating this baseline,
 *   existing fresh installs will conservatively classify as `false`.
 */
object LocalBackupStateClassifier {

    private const val INTERNAL_DEFAULT_ACCOUNT_KEY = "default"
    private const val DEFAULT_CURRENCY_CODE = "UZS"
    private const val DEFAULT_INTERNAL_ACCOUNT_NAME = "Internal Default"
    private const val DEFAULT_INTERNAL_ACCOUNT_ICON = "account_balance"
    private const val DEFAULT_INTERNAL_ACCOUNT_COLOR = "#10B981"

    private data class SeededCategorySignature(
        val name: String,
        val type: String,
        val iconName: String,
        val colorHex: String
    )

    private val SEEDED_SYSTEM_CATEGORY_SIGNATURES: Set<SeededCategorySignature> = setOf(
        SeededCategorySignature("Groceries", CategoryType.EXPENSE.name, "fastfood", "#10B981"),
        SeededCategorySignature("Food", CategoryType.EXPENSE.name, "fastfood", "#F59E0B"),
        SeededCategorySignature("Transport", CategoryType.EXPENSE.name, "directions_car", "#3B82F6"),
        SeededCategorySignature("Study", CategoryType.EXPENSE.name, "school", "#6366F1"),
        SeededCategorySignature("Shopping", CategoryType.EXPENSE.name, "shopping_bag", "#EC4899"),
        SeededCategorySignature("Health", CategoryType.EXPENSE.name, "local_hospital", "#EF4444"),
        SeededCategorySignature("Entertainment", CategoryType.EXPENSE.name, "movie", "#8B5CF6"),
        SeededCategorySignature("Bills", CategoryType.EXPENSE.name, "receipt", "#64748B"),
        SeededCategorySignature("Other", CategoryType.EXPENSE.name, "category", "#14B8A6"),
        SeededCategorySignature("Salary", CategoryType.INCOME.name, "work", "#10B981"),
        SeededCategorySignature("Freelance", CategoryType.INCOME.name, "work", "#3B82F6"),
        SeededCategorySignature("Business", CategoryType.INCOME.name, "work", "#6366F1"),
        SeededCategorySignature("Gift", CategoryType.INCOME.name, "card_giftcard", "#F59E0B"),
        SeededCategorySignature("Other", CategoryType.INCOME.name, "category", "#14B8A6")
    )

    private val BASELINE_SETTINGS_NULL_CURRENCY: BackupSettings = AppSettings(
        themeMode = ThemeMode.SYSTEM,
        preferredCurrencyCode = null,
        weekStart = WeekStart.MONDAY,
        dateFormat = DateFormatPreference.SYSTEM_DEFAULT,
        timeFormat = TimeFormatPreference.SYSTEM_DEFAULT,
        showCurrencyCode = false,
        confirmBeforeDelete = true
    ).toBackup()

    private val BASELINE_SETTINGS_UZS_CURRENCY: BackupSettings =
        BASELINE_SETTINGS_NULL_CURRENCY.copy(preferredCurrencyCode = DEFAULT_CURRENCY_CODE)

    /**
     * Returns `true` only when [backup] matches an unmodified, freshly provisioned installation
     * baseline across financial records, accounts, categories, and settings.
     *
     * Does not mutate [backup] or any of its collections.
     */
    fun isEffectivelyEmpty(backup: ExpenseTrackerBackup): Boolean {
        if (!hasNoFinancialOrRecurringRecords(backup)) {
            return false
        }
        if (!hasOnlyBaselineInternalAccount(backup.accounts)) {
            return false
        }
        if (!hasOnlyBaselineSystemCategories(backup.categories)) {
            return false
        }
        if (!hasBaselineSettings(backup.settings)) {
            return false
        }
        return true
    }

    private fun hasNoFinancialOrRecurringRecords(backup: ExpenseTrackerBackup): Boolean {
        return backup.transactions.isEmpty() &&
            backup.budgets.isEmpty() &&
            backup.recurringTransactions.isEmpty() &&
            backup.recurringOccurrences.isEmpty()
    }

    private fun hasOnlyBaselineInternalAccount(accounts: List<BackupAccount>): Boolean {
        if (accounts.size != 1) {
            return false
        }
        val account = accounts.single()
        return account.internalKey == INTERNAL_DEFAULT_ACCOUNT_KEY &&
            !account.isArchived &&
            account.initialBalanceAmountInMinorUnits == 0L &&
            account.initialBalanceCurrencyCode == DEFAULT_CURRENCY_CODE &&
            account.name == DEFAULT_INTERNAL_ACCOUNT_NAME &&
            account.type == AccountType.CASH.name &&
            account.iconName == DEFAULT_INTERNAL_ACCOUNT_ICON &&
            account.colorHex == DEFAULT_INTERNAL_ACCOUNT_COLOR
    }

    private fun hasOnlyBaselineSystemCategories(categories: List<BackupCategory>): Boolean {
        if (categories.size != SEEDED_SYSTEM_CATEGORY_SIGNATURES.size) {
            return false
        }
        if (categories.any { !it.isSystem || it.isArchived }) {
            return false
        }
        val actualSignatures = categories.mapTo(HashSet(SEEDED_SYSTEM_CATEGORY_SIGNATURES.size)) {
            SeededCategorySignature(
                name = it.name,
                type = it.type,
                iconName = it.iconName,
                colorHex = it.colorHex
            )
        }
        return actualSignatures == SEEDED_SYSTEM_CATEGORY_SIGNATURES
    }

    private fun hasBaselineSettings(settings: BackupSettings?): Boolean {
        if (settings == null) {
            return false
        }
        return settings == BASELINE_SETTINGS_NULL_CURRENCY ||
            settings == BASELINE_SETTINGS_UZS_CURRENCY
    }
}
