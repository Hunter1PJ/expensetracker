package com.suguru.expensetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBackupStateClassifierTest {

    private fun baselineInternalAccount(
        id: Long = 1L,
        name: String = "Internal Default",
        type: String = "CASH",
        initialBalanceAmountInMinorUnits: Long = 0L,
        initialBalanceCurrencyCode: String = "UZS",
        iconName: String = "account_balance",
        colorHex: String = "#10B981",
        isArchived: Boolean = false,
        createdAt: String = "2026-11-15T08:00:00Z",
        internalKey: String? = "default"
    ): BackupAccount = BackupAccount(
        id = id,
        name = name,
        type = type,
        initialBalanceAmountInMinorUnits = initialBalanceAmountInMinorUnits,
        initialBalanceCurrencyCode = initialBalanceCurrencyCode,
        iconName = iconName,
        colorHex = colorHex,
        isArchived = isArchived,
        createdAt = createdAt,
        internalKey = internalKey
    )

    private fun seededSystemCategories(): List<BackupCategory> = listOf(
        BackupCategory(1L, "Groceries", "EXPENSE", "fastfood", "#10B981", isSystem = true, isArchived = false),
        BackupCategory(2L, "Food", "EXPENSE", "fastfood", "#F59E0B", isSystem = true, isArchived = false),
        BackupCategory(3L, "Transport", "EXPENSE", "directions_car", "#3B82F6", isSystem = true, isArchived = false),
        BackupCategory(4L, "Study", "EXPENSE", "school", "#6366F1", isSystem = true, isArchived = false),
        BackupCategory(5L, "Shopping", "EXPENSE", "shopping_bag", "#EC4899", isSystem = true, isArchived = false),
        BackupCategory(6L, "Health", "EXPENSE", "local_hospital", "#EF4444", isSystem = true, isArchived = false),
        BackupCategory(7L, "Entertainment", "EXPENSE", "movie", "#8B5CF6", isSystem = true, isArchived = false),
        BackupCategory(8L, "Bills", "EXPENSE", "receipt", "#64748B", isSystem = true, isArchived = false),
        BackupCategory(9L, "Other", "EXPENSE", "category", "#14B8A6", isSystem = true, isArchived = false),
        BackupCategory(10L, "Salary", "INCOME", "work", "#10B981", isSystem = true, isArchived = false),
        BackupCategory(11L, "Freelance", "INCOME", "work", "#3B82F6", isSystem = true, isArchived = false),
        BackupCategory(12L, "Business", "INCOME", "work", "#6366F1", isSystem = true, isArchived = false),
        BackupCategory(13L, "Gift", "INCOME", "card_giftcard", "#F59E0B", isSystem = true, isArchived = false),
        BackupCategory(14L, "Other", "INCOME", "category", "#14B8A6", isSystem = true, isArchived = false)
    )

    private fun baselineSettings(
        preferredCurrencyCode: String? = null
    ): BackupSettings = AppSettings(
        themeMode = ThemeMode.SYSTEM,
        preferredCurrencyCode = preferredCurrencyCode,
        weekStart = WeekStart.MONDAY,
        dateFormat = DateFormatPreference.SYSTEM_DEFAULT,
        timeFormat = TimeFormatPreference.SYSTEM_DEFAULT,
        showCurrencyCode = false,
        confirmBeforeDelete = true
    ).toBackup()

    private fun baselineBackup(
        accounts: List<BackupAccount> = listOf(baselineInternalAccount()),
        categories: List<BackupCategory> = seededSystemCategories(),
        transactions: List<BackupTransaction> = emptyList(),
        budgets: List<BackupBudget> = emptyList(),
        recurringTransactions: List<BackupRecurringTransaction> = emptyList(),
        recurringOccurrences: List<BackupRecurringOccurrence> = emptyList(),
        settings: BackupSettings? = baselineSettings(preferredCurrencyCode = null)
    ): ExpenseTrackerBackup = ExpenseTrackerBackup(
        formatVersion = 2,
        appVersion = "1.0.0",
        createdAt = "2026-11-15T08:00:00Z",
        databaseSchemaVersion = 4,
        accounts = accounts,
        categories = categories,
        transactions = transactions,
        budgets = budgets,
        recurringTransactions = recurringTransactions,
        recurringOccurrences = recurringOccurrences,
        settings = settings,
        checksum = "sample-checksum"
    )

    @Test
    fun `1 - fresh install baseline is classified as effectively empty`() {
        val freshNullCurrency = baselineBackup(settings = baselineSettings(preferredCurrencyCode = null))
        assertTrue(LocalBackupStateClassifier.isEffectivelyEmpty(freshNullCurrency))

        // Order and entity IDs do not matter for baseline classification
        val reorderedAndReidentified = freshNullCurrency.copy(
            formatVersion = 1,
            appVersion = "2.1.0",
            createdAt = "2027-01-01T00:00:00Z",
            databaseSchemaVersion = 3,
            checksum = null,
            accounts = listOf(baselineInternalAccount(id = 42L, createdAt = "2027-01-01T00:00:00Z")),
            categories = seededSystemCategories().reversed().mapIndexed { idx, cat -> cat.copy(id = 100L + idx) }
        )
        assertTrue(LocalBackupStateClassifier.isEffectivelyEmpty(reorderedAndReidentified))
    }

    @Test
    fun `2 - one transaction makes backup non-empty`() {
        val tx = BackupTransaction(
            id = 1L,
            type = "EXPENSE",
            amountInMinorUnits = 15_000L,
            currencyCode = "UZS",
            accountId = 1L,
            destinationAccountId = null,
            categoryId = 2L,
            transactionTime = "2026-11-15T08:05:00Z",
            note = "Coffee",
            recurringRuleId = null,
            recurringOccurrenceDate = null,
            createdAt = "2026-11-15T08:05:00Z"
        )

        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(transactions = listOf(tx))))
    }

    @Test
    fun `3 - one budget makes backup non-empty`() {
        val budget = BackupBudget(
            id = 1L,
            categoryId = 1L,
            limitAmountInMinorUnits = 500_000L,
            currencyCode = "UZS",
            periodType = "MONTHLY",
            startDate = "2026-11-01",
            endDate = "2026-11-30",
            isActive = true
        )

        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(budgets = listOf(budget))))
    }

    @Test
    fun `4 - one recurring transaction makes backup non-empty`() {
        val rule = BackupRecurringTransaction(
            id = 1L,
            type = "EXPENSE",
            amountInMinorUnits = 100_000L,
            currencyCode = "UZS",
            accountId = 1L,
            destinationAccountId = null,
            categoryId = 8L,
            frequency = "MONTHLY",
            startDate = "2026-11-01",
            endDate = null,
            nextOccurrence = "2026-12-01",
            note = "Internet",
            isActive = true
        )

        assertFalse(
            LocalBackupStateClassifier.isEffectivelyEmpty(
                baselineBackup(recurringTransactions = listOf(rule))
            )
        )
    }

    @Test
    fun `5 - one recurring occurrence makes backup non-empty`() {
        val occurrence = BackupRecurringOccurrence(
            id = 1L,
            ruleId = 1L,
            occurrenceDate = "2026-11-01",
            generatedTransactionId = null,
            status = "TRANSACTION_DELETED",
            processedAt = "2026-11-01T00:05:00Z"
        )

        assertFalse(
            LocalBackupStateClassifier.isEffectivelyEmpty(
                baselineBackup(recurringOccurrences = listOf(occurrence))
            )
        )
    }

    @Test
    fun `6 - additional user-created account makes backup non-empty`() {
        val userAccount = BackupAccount(
            id = 2L,
            name = "Main Card",
            type = "CARD",
            initialBalanceAmountInMinorUnits = 0L,
            initialBalanceCurrencyCode = "UZS",
            iconName = "credit_card",
            colorHex = "#2563EB",
            isArchived = false,
            createdAt = "2026-11-15T08:10:00Z",
            internalKey = null
        )

        assertFalse(
            LocalBackupStateClassifier.isEffectivelyEmpty(
                baselineBackup(accounts = listOf(baselineInternalAccount(), userAccount))
            )
        )
        assertFalse(
            LocalBackupStateClassifier.isEffectivelyEmpty(
                baselineBackup(accounts = listOf(userAccount))
            )
        )
    }

    @Test
    fun `7 - missing default account makes backup non-empty`() {
        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(accounts = emptyList())))

        val nonDefaultInternal = baselineInternalAccount(internalKey = null)
        assertFalse(
            LocalBackupStateClassifier.isEffectivelyEmpty(
                baselineBackup(accounts = listOf(nonDefaultInternal))
            )
        )

        val otherInternalKey = baselineInternalAccount(internalKey = "secondary")
        assertFalse(
            LocalBackupStateClassifier.isEffectivelyEmpty(
                baselineBackup(accounts = listOf(otherInternalKey))
            )
        )
    }

    @Test
    fun `8 - duplicate default accounts make backup non-empty`() {
        val duplicateDefaults = listOf(
            baselineInternalAccount(id = 1L),
            baselineInternalAccount(id = 2L)
        )

        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(accounts = duplicateDefaults)))
    }

    @Test
    fun `9 - default account with non-zero initial balance makes backup non-empty`() {
        val positiveBalance = baselineInternalAccount(initialBalanceAmountInMinorUnits = 1L)
        val negativeBalance = baselineInternalAccount(initialBalanceAmountInMinorUnits = -100L)

        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(accounts = listOf(positiveBalance))))
        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(accounts = listOf(negativeBalance))))
    }

    @Test
    fun `default account edits or archiving make backup non-empty`() {
        val variations = listOf(
            baselineInternalAccount(isArchived = true),
            baselineInternalAccount(name = "My Cash"),
            baselineInternalAccount(type = "BANK"),
            baselineInternalAccount(iconName = "wallet"),
            baselineInternalAccount(colorHex = "#000000"),
            baselineInternalAccount(initialBalanceCurrencyCode = "USD")
        )

        for (mutatedAccount in variations) {
            assertFalse(
                "Expected mutated default account $mutatedAccount to be non-empty",
                LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(accounts = listOf(mutatedAccount)))
            )
        }
    }

    @Test
    fun `10 - user-created category makes backup non-empty`() {
        val customCategory = BackupCategory(
            id = 15L,
            name = "Pets",
            type = "EXPENSE",
            iconName = "pets",
            colorHex = "#F97316",
            isSystem = false,
            isArchived = false
        )

        assertFalse(
            LocalBackupStateClassifier.isEffectivelyEmpty(
                baselineBackup(categories = seededSystemCategories() + customCategory)
            )
        )

        // Even at count 14, a non-system category must be rejected
        val replacedWithCustom = seededSystemCategories().dropLast(1) + customCategory.copy(
            id = 14L,
            name = "Other",
            type = "INCOME",
            iconName = "category",
            colorHex = "#14B8A6",
            isSystem = false
        )
        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(categories = replacedWithCustom)))
    }

    @Test
    fun `11 - archived system category makes backup non-empty`() {
        val archivedInPlace = seededSystemCategories().mapIndexed { index, category ->
            if (index == 1) category.copy(isArchived = true) else category
        }
        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(categories = archivedInPlace)))

        val archivedPlusReseeded = seededSystemCategories() + seededSystemCategories().first().copy(
            id = 15L,
            isArchived = true
        )
        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(categories = archivedPlusReseeded)))
    }

    @Test
    fun `modified missing or duplicate system categories make backup non-empty`() {
        val baseCategories = seededSystemCategories()

        // Missing one seeded category
        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(categories = baseCategories.dropLast(1))))

        // Duplicate seeded category replacing another (still 14 items, all isSystem = true)
        val duplicateGroceries = baseCategories.dropLast(1) + baseCategories.first().copy(id = 14L)
        assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(categories = duplicateGroceries)))

        // Edited system category fields while keeping isSystem = true and count = 14
        val editedName = baseCategories.mapIndexed { idx, cat -> if (idx == 0) cat.copy(name = "Supermarket") else cat }
        val editedType = baseCategories.mapIndexed { idx, cat -> if (idx == 0) cat.copy(type = "BOTH") else cat }
        val editedIcon = baseCategories.mapIndexed { idx, cat -> if (idx == 0) cat.copy(iconName = "restaurant") else cat }
        val editedColor = baseCategories.mapIndexed { idx, cat -> if (idx == 0) cat.copy(colorHex = "#000000") else cat }

        for (mutatedCategories in listOf(editedName, editedType, editedIcon, editedColor)) {
            assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(categories = mutatedCategories)))
        }
    }

    @Test
    fun `12 - modified settings make backup non-empty`() {
        val defaults = baselineSettings(preferredCurrencyCode = null)
        val modifiedSettings = listOf(
            null,
            defaults.copy(themeMode = "DARK"),
            defaults.copy(themeMode = "LIGHT"),
            defaults.copy(themeMode = "UNKNOWN_MODE"),
            defaults.copy(weekStart = "SUNDAY"),
            defaults.copy(dateFormat = "DD_MM_YYYY"),
            defaults.copy(timeFormat = "HOUR_12"),
            defaults.copy(showCurrencyCode = true),
            defaults.copy(confirmBeforeDelete = false)
        )

        for (settings in modifiedSettings) {
            assertFalse(
                "Expected settings=$settings to classify as non-empty",
                LocalBackupStateClassifier.isEffectivelyEmpty(baselineBackup(settings = settings))
            )
        }
    }

    @Test
    fun `13 - default currency null is accepted`() {
        val backup = baselineBackup(settings = baselineSettings(preferredCurrencyCode = null))
        assertTrue(LocalBackupStateClassifier.isEffectivelyEmpty(backup))
    }

    @Test
    fun `14 - default currency UZS is accepted`() {
        val backup = baselineBackup(settings = baselineSettings(preferredCurrencyCode = "UZS"))
        assertTrue(LocalBackupStateClassifier.isEffectivelyEmpty(backup))
    }

    @Test
    fun `15 - other currency codes make backup non-empty`() {
        for (currency in listOf("USD", "EUR", "RUB", "KRW", "JPY", "", "   ", "uzs", " UZS ")) {
            val backup = baselineBackup(settings = baselineSettings(preferredCurrencyCode = currency))
            assertFalse(
                "Expected preferredCurrencyCode='$currency' to classify as non-empty",
                LocalBackupStateClassifier.isEffectivelyEmpty(backup)
            )
        }
    }

    @Test
    fun `16 - empty collections do not cause vacuous-true acceptance when baseline is absent`() {
        assertFalse(
            LocalBackupStateClassifier.isEffectivelyEmpty(
                baselineBackup(accounts = emptyList(), categories = emptyList())
            )
        )
        assertFalse(
            LocalBackupStateClassifier.isEffectivelyEmpty(
                baselineBackup(accounts = emptyList())
            )
        )
        assertFalse(
            LocalBackupStateClassifier.isEffectivelyEmpty(
                baselineBackup(categories = emptyList())
            )
        )
        assertFalse(
            LocalBackupStateClassifier.isEffectivelyEmpty(
                baselineBackup(accounts = emptyList(), categories = emptyList(), settings = null)
            )
        )
    }

    @Test
    fun `17 - repeated calls return identical deterministic results`() {
        val emptyBaseline = baselineBackup(settings = baselineSettings(preferredCurrencyCode = "UZS"))
        val nonEmpty = baselineBackup(
            accounts = listOf(
                baselineInternalAccount(),
                baselineInternalAccount(id = 2L, name = "Wallet", internalKey = null)
            )
        )

        repeat(10) {
            assertTrue(LocalBackupStateClassifier.isEffectivelyEmpty(emptyBaseline))
            assertFalse(LocalBackupStateClassifier.isEffectivelyEmpty(nonEmpty))
        }
    }

    @Test
    fun `18 - input backup and its collections are never mutated`() {
        val mutableAccounts = mutableListOf(baselineInternalAccount())
        val mutableCategories = seededSystemCategories().reversed().toMutableList()
        val mutableTransactions = mutableListOf<BackupTransaction>()
        val mutableBudgets = mutableListOf<BackupBudget>()
        val mutableRules = mutableListOf<BackupRecurringTransaction>()
        val mutableOccurrences = mutableListOf<BackupRecurringOccurrence>()

        val accountsSnapshot = mutableAccounts.toList()
        val categoriesSnapshot = mutableCategories.toList()
        val backup = baselineBackup(
            accounts = mutableAccounts,
            categories = mutableCategories,
            transactions = mutableTransactions,
            budgets = mutableBudgets,
            recurringTransactions = mutableRules,
            recurringOccurrences = mutableOccurrences,
            settings = baselineSettings(preferredCurrencyCode = null)
        )
        val backupSnapshot = backup.copy()

        val result = LocalBackupStateClassifier.isEffectivelyEmpty(backup)

        assertTrue(result)
        assertEquals(accountsSnapshot, mutableAccounts)
        assertEquals(categoriesSnapshot, mutableCategories)
        assertTrue(mutableTransactions.isEmpty())
        assertTrue(mutableBudgets.isEmpty())
        assertTrue(mutableRules.isEmpty())
        assertTrue(mutableOccurrences.isEmpty())
        assertEquals(backupSnapshot, backup)
    }
}
