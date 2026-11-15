package com.suguru.expensetracker.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupContentFingerprintTest {

    private fun sampleBackup(
        formatVersion: Int = 2,
        appVersion: String = "1.5.0",
        createdAt: String = "2026-11-15T08:00:00Z",
        databaseSchemaVersion: Int = 4,
        checksum: String? = "existing-checksum-value",
        accounts: List<BackupAccount> = listOf(
            BackupAccount(
                id = 1L,
                name = "Internal Default",
                type = "CASH",
                initialBalanceAmountInMinorUnits = 0L,
                initialBalanceCurrencyCode = "USD",
                iconName = "account_balance",
                colorHex = "#10B981",
                isArchived = false,
                createdAt = "2026-01-01T00:00:00Z",
                internalKey = "default"
            ),
            BackupAccount(
                id = 2L,
                name = "Main Checking",
                type = "BANK",
                initialBalanceAmountInMinorUnits = 250_000L,
                initialBalanceCurrencyCode = "USD",
                iconName = "wallet",
                colorHex = "#2563EB",
                isArchived = false,
                createdAt = "2026-01-02T09:30:00Z",
                internalKey = null
            )
        ),
        categories: List<BackupCategory> = listOf(
            BackupCategory(
                id = 10L,
                name = "Groceries",
                type = "EXPENSE",
                iconName = "shopping_cart",
                colorHex = "#22C55E",
                isSystem = true,
                isArchived = false
            ),
            BackupCategory(
                id = 11L,
                name = "Salary",
                type = "INCOME",
                iconName = "payments",
                colorHex = "#0EA5E9",
                isSystem = false,
                isArchived = false
            )
        ),
        transactions: List<BackupTransaction> = listOf(
            BackupTransaction(
                id = 100L,
                type = "EXPENSE",
                amountInMinorUnits = 4_550L,
                currencyCode = "USD",
                accountId = 2L,
                destinationAccountId = null,
                categoryId = 10L,
                transactionTime = "2026-11-10T18:15:00Z",
                note = "Weekly groceries",
                recurringRuleId = 200L,
                recurringOccurrenceDate = "2026-11-10",
                createdAt = "2026-11-10T18:16:00Z"
            ),
            BackupTransaction(
                id = 101L,
                type = "TRANSFER",
                amountInMinorUnits = 10_000L,
                currencyCode = "USD",
                accountId = 2L,
                destinationAccountId = 1L,
                categoryId = null,
                transactionTime = "2026-11-11T09:00:00Z",
                note = null,
                recurringRuleId = null,
                recurringOccurrenceDate = null,
                createdAt = "2026-11-11T09:00:00Z"
            )
        ),
        budgets: List<BackupBudget> = listOf(
            BackupBudget(
                id = 300L,
                categoryId = 10L,
                limitAmountInMinorUnits = 60_000L,
                currencyCode = "USD",
                periodType = "MONTHLY",
                startDate = "2026-11-01",
                endDate = "2026-11-30",
                isActive = true
            ),
            BackupBudget(
                id = 301L,
                categoryId = null,
                limitAmountInMinorUnits = 150_000L,
                currencyCode = "USD",
                periodType = "MONTHLY",
                startDate = "2026-11-01",
                endDate = "2026-11-30",
                isActive = false
            )
        ),
        recurringTransactions: List<BackupRecurringTransaction> = listOf(
            BackupRecurringTransaction(
                id = 200L,
                type = "EXPENSE",
                amountInMinorUnits = 4_550L,
                currencyCode = "USD",
                accountId = 2L,
                destinationAccountId = null,
                categoryId = 10L,
                frequency = "WEEKLY",
                startDate = "2026-11-03",
                endDate = "2027-11-03",
                nextOccurrence = "2026-11-17",
                note = "Weekly groceries",
                isActive = true
            ),
            BackupRecurringTransaction(
                id = 201L,
                type = "INCOME",
                amountInMinorUnits = 300_000L,
                currencyCode = "USD",
                accountId = 2L,
                destinationAccountId = null,
                categoryId = 11L,
                frequency = "MONTHLY",
                startDate = "2026-01-01",
                endDate = null,
                nextOccurrence = "2026-12-01",
                note = null,
                isActive = true
            )
        ),
        recurringOccurrences: List<BackupRecurringOccurrence> = listOf(
            BackupRecurringOccurrence(
                id = 400L,
                ruleId = 200L,
                occurrenceDate = "2026-11-10",
                generatedTransactionId = 100L,
                status = "GENERATED",
                processedAt = "2026-11-10T18:16:00Z"
            ),
            BackupRecurringOccurrence(
                id = 401L,
                ruleId = 201L,
                occurrenceDate = "2026-11-01",
                generatedTransactionId = null,
                status = "SKIPPED",
                processedAt = "2026-11-01T00:05:00Z"
            )
        ),
        settings: BackupSettings? = BackupSettings(
            themeMode = "SYSTEM",
            preferredCurrencyCode = "USD",
            weekStart = "MONDAY",
            dateFormat = "YYYY_MM_DD",
            timeFormat = "HOUR_24",
            showCurrencyCode = true,
            confirmBeforeDelete = true
        )
    ): ExpenseTrackerBackup = ExpenseTrackerBackup(
        formatVersion = formatVersion,
        appVersion = appVersion,
        createdAt = createdAt,
        databaseSchemaVersion = databaseSchemaVersion,
        accounts = accounts,
        categories = categories,
        transactions = transactions,
        budgets = budgets,
        recurringTransactions = recurringTransactions,
        recurringOccurrences = recurringOccurrences,
        settings = settings,
        checksum = checksum
    )

    // region Stability

    @Test
    fun `identical backups produce identical sha256 hex fingerprints`() {
        val first = sampleBackup()
        val second = sampleBackup()

        val fp1 = BackupContentFingerprint.compute(first)
        val fp2 = BackupContentFingerprint.compute(second)

        assertEquals(fp1, fp2)
        assertTrue(
            "Expected 64-char lowercase SHA-256 hex fingerprint, got: $fp1",
            Regex("^[0-9a-f]{64}$").matches(fp1)
        )
    }

    @Test
    fun `different entity list ordering produces identical fingerprint`() {
        val ordered = sampleBackup()
        val reversed = ordered.copy(
            accounts = ordered.accounts.reversed(),
            categories = ordered.categories.reversed(),
            transactions = ordered.transactions.reversed(),
            budgets = ordered.budgets.reversed(),
            recurringTransactions = ordered.recurringTransactions.reversed(),
            recurringOccurrences = ordered.recurringOccurrences.reversed()
        )

        assertEquals(
            BackupContentFingerprint.compute(ordered),
            BackupContentFingerprint.compute(reversed)
        )
    }

    @Test
    fun `different createdAt metadata produces identical fingerprint`() {
        val base = sampleBackup(createdAt = "2026-01-01T00:00:00Z")
        val later = base.copy(createdAt = "2026-12-31T23:59:59.999Z")

        assertEquals(
            BackupContentFingerprint.compute(base),
            BackupContentFingerprint.compute(later)
        )
    }

    @Test
    fun `different appVersion metadata produces identical fingerprint`() {
        val base = sampleBackup(appVersion = "1.5.0")
        val upgraded = base.copy(appVersion = "2.0.0-beta1")

        assertEquals(
            BackupContentFingerprint.compute(base),
            BackupContentFingerprint.compute(upgraded)
        )
    }

    @Test
    fun `different formatVersion and databaseSchemaVersion produce identical fingerprint`() {
        val v1 = sampleBackup(formatVersion = 1, databaseSchemaVersion = 2)
        val v2 = v1.copy(formatVersion = 2, databaseSchemaVersion = 4)
        val future = v1.copy(formatVersion = 9, databaseSchemaVersion = 12)

        val fpV1 = BackupContentFingerprint.compute(v1)
        assertEquals(fpV1, BackupContentFingerprint.compute(v2))
        assertEquals(fpV1, BackupContentFingerprint.compute(future))
    }

    @Test
    fun `different existing checksum values do not change content fingerprint`() {
        val withoutChecksum = sampleBackup(checksum = null)
        val withValidChecksum = withoutChecksum.copy(checksum = "abcdef0123456789")
        val withOtherChecksum = withoutChecksum.copy(checksum = "different-checksum")

        val expected = BackupContentFingerprint.compute(withoutChecksum)
        assertEquals(expected, BackupContentFingerprint.compute(withValidChecksum))
        assertEquals(expected, BackupContentFingerprint.compute(withOtherChecksum))
    }

    @Test
    fun `null settings is stable and distinct from non-null settings`() {
        val withSettings = sampleBackup()
        val withoutSettingsA = withSettings.copy(settings = null)
        val withoutSettingsB = withSettings.copy(settings = null, createdAt = "2027-01-01T00:00:00Z")

        assertEquals(
            BackupContentFingerprint.compute(withoutSettingsA),
            BackupContentFingerprint.compute(withoutSettingsB)
        )
        assertNotEquals(
            BackupContentFingerprint.compute(withSettings),
            BackupContentFingerprint.compute(withoutSettingsA)
        )
    }

    // endregion

    // region Sensitivity

    @Test
    fun `changing any account field changes fingerprint`() {
        val base = sampleBackup()
        val baseFp = BackupContentFingerprint.compute(base)
        val target = base.accounts.first()

        val mutations = listOf(
            "id" to target.copy(id = 999L),
            "name" to target.copy(name = "Renamed Default"),
            "type" to target.copy(type = "SAVINGS"),
            "initialBalanceAmountInMinorUnits" to target.copy(initialBalanceAmountInMinorUnits = 1L),
            "initialBalanceCurrencyCode" to target.copy(initialBalanceCurrencyCode = "EUR"),
            "iconName" to target.copy(iconName = "savings"),
            "colorHex" to target.copy(colorHex = "#FFFFFF"),
            "isArchived" to target.copy(isArchived = true),
            "createdAt" to target.copy(createdAt = "2026-01-01T00:00:01Z"),
            "internalKey" to target.copy(internalKey = null)
        )

        for ((field, mutatedAccount) in mutations) {
            val mutated = base.copy(accounts = listOf(mutatedAccount) + base.accounts.drop(1))
            assertNotEquals("Expected account.$field mutation to change fingerprint", baseFp, BackupContentFingerprint.compute(mutated))
        }
    }

    @Test
    fun `changing any category field changes fingerprint`() {
        val base = sampleBackup()
        val baseFp = BackupContentFingerprint.compute(base)
        val target = base.categories.first()

        val mutations = listOf(
            "id" to target.copy(id = 999L),
            "name" to target.copy(name = "Supermarket"),
            "type" to target.copy(type = "INCOME"),
            "iconName" to target.copy(iconName = "fastfood"),
            "colorHex" to target.copy(colorHex = "#000000"),
            "isSystem" to target.copy(isSystem = false),
            "isArchived" to target.copy(isArchived = true)
        )

        for ((field, mutatedCategory) in mutations) {
            val mutated = base.copy(categories = listOf(mutatedCategory) + base.categories.drop(1))
            assertNotEquals("Expected category.$field mutation to change fingerprint", baseFp, BackupContentFingerprint.compute(mutated))
        }
    }

    @Test
    fun `changing any transaction field changes fingerprint`() {
        val base = sampleBackup()
        val baseFp = BackupContentFingerprint.compute(base)
        val target = base.transactions.first()

        val mutations = listOf(
            "id" to target.copy(id = 999L),
            "type" to target.copy(type = "INCOME"),
            "amountInMinorUnits" to target.copy(amountInMinorUnits = 4_551L),
            "currencyCode" to target.copy(currencyCode = "EUR"),
            "accountId" to target.copy(accountId = 1L),
            "destinationAccountId" to target.copy(destinationAccountId = 1L),
            "categoryId" to target.copy(categoryId = 11L),
            "transactionTime" to target.copy(transactionTime = "2026-11-10T18:15:01Z"),
            "note" to target.copy(note = "Updated note"),
            "recurringRuleId" to target.copy(recurringRuleId = null),
            "recurringOccurrenceDate" to target.copy(recurringOccurrenceDate = "2026-11-11"),
            "createdAt" to target.copy(createdAt = "2026-11-10T18:16:01Z")
        )

        for ((field, mutatedTx) in mutations) {
            val mutated = base.copy(transactions = listOf(mutatedTx) + base.transactions.drop(1))
            assertNotEquals("Expected transaction.$field mutation to change fingerprint", baseFp, BackupContentFingerprint.compute(mutated))
        }
    }

    @Test
    fun `changing any budget field changes fingerprint`() {
        val base = sampleBackup()
        val baseFp = BackupContentFingerprint.compute(base)
        val target = base.budgets.first()

        val mutations = listOf(
            "id" to target.copy(id = 999L),
            "categoryId" to target.copy(categoryId = null),
            "limitAmountInMinorUnits" to target.copy(limitAmountInMinorUnits = 60_001L),
            "currencyCode" to target.copy(currencyCode = "GBP"),
            "periodType" to target.copy(periodType = "WEEKLY"),
            "startDate" to target.copy(startDate = "2026-11-02"),
            "endDate" to target.copy(endDate = "2026-11-29"),
            "isActive" to target.copy(isActive = false)
        )

        for ((field, mutatedBudget) in mutations) {
            val mutated = base.copy(budgets = listOf(mutatedBudget) + base.budgets.drop(1))
            assertNotEquals("Expected budget.$field mutation to change fingerprint", baseFp, BackupContentFingerprint.compute(mutated))
        }
    }

    @Test
    fun `changing any recurring transaction field changes fingerprint`() {
        val base = sampleBackup()
        val baseFp = BackupContentFingerprint.compute(base)
        val target = base.recurringTransactions.first()

        val mutations = listOf(
            "id" to target.copy(id = 999L),
            "type" to target.copy(type = "TRANSFER"),
            "amountInMinorUnits" to target.copy(amountInMinorUnits = 4_551L),
            "currencyCode" to target.copy(currencyCode = "EUR"),
            "accountId" to target.copy(accountId = 1L),
            "destinationAccountId" to target.copy(destinationAccountId = 1L),
            "categoryId" to target.copy(categoryId = null),
            "frequency" to target.copy(frequency = "MONTHLY"),
            "startDate" to target.copy(startDate = "2026-11-04"),
            "endDate" to target.copy(endDate = null),
            "nextOccurrence" to target.copy(nextOccurrence = "2026-11-18"),
            "note" to target.copy(note = "Changed note"),
            "isActive" to target.copy(isActive = false)
        )

        for ((field, mutatedRule) in mutations) {
            val mutated = base.copy(recurringTransactions = listOf(mutatedRule) + base.recurringTransactions.drop(1))
            assertNotEquals("Expected recurringTransaction.$field mutation to change fingerprint", baseFp, BackupContentFingerprint.compute(mutated))
        }
    }

    @Test
    fun `changing any recurring occurrence field changes fingerprint`() {
        val base = sampleBackup()
        val baseFp = BackupContentFingerprint.compute(base)
        val target = base.recurringOccurrences.first()

        val mutations = listOf(
            "id" to target.copy(id = 999L),
            "ruleId" to target.copy(ruleId = 201L),
            "occurrenceDate" to target.copy(occurrenceDate = "2026-11-11"),
            "generatedTransactionId" to target.copy(generatedTransactionId = null),
            "status" to target.copy(status = "SKIPPED"),
            "processedAt" to target.copy(processedAt = "2026-11-10T18:16:01Z")
        )

        for ((field, mutatedOccurrence) in mutations) {
            val mutated = base.copy(recurringOccurrences = listOf(mutatedOccurrence) + base.recurringOccurrences.drop(1))
            assertNotEquals("Expected recurringOccurrence.$field mutation to change fingerprint", baseFp, BackupContentFingerprint.compute(mutated))
        }
    }

    @Test
    fun `changing any backup setting field changes fingerprint`() {
        val base = sampleBackup()
        val baseFp = BackupContentFingerprint.compute(base)
        val settings = requireNotNull(base.settings)

        val mutations = listOf(
            "themeMode" to settings.copy(themeMode = "DARK"),
            "preferredCurrencyCode" to settings.copy(preferredCurrencyCode = "EUR"),
            "preferredCurrencyCodeNull" to settings.copy(preferredCurrencyCode = null),
            "weekStart" to settings.copy(weekStart = "SUNDAY"),
            "dateFormat" to settings.copy(dateFormat = "DD_MM_YYYY"),
            "timeFormat" to settings.copy(timeFormat = "HOUR_12"),
            "showCurrencyCode" to settings.copy(showCurrencyCode = false),
            "confirmBeforeDelete" to settings.copy(confirmBeforeDelete = false)
        )

        for ((field, mutatedSettings) in mutations) {
            val mutated = base.copy(settings = mutatedSettings)
            assertNotEquals("Expected settings.$field mutation to change fingerprint", baseFp, BackupContentFingerprint.compute(mutated))
        }
    }

    // endregion

    // region Security, Non-Mutation, and Canonical Isolation

    @Test
    fun `fingerprint computation does not mutate supplied backup or its collections`() {
        val mutableAccounts = sampleBackup().accounts.reversed().toMutableList()
        val mutableCategories = sampleBackup().categories.reversed().toMutableList()
        val mutableTransactions = sampleBackup().transactions.reversed().toMutableList()
        val mutableBudgets = sampleBackup().budgets.reversed().toMutableList()
        val mutableRules = sampleBackup().recurringTransactions.reversed().toMutableList()
        val mutableOccurrences = sampleBackup().recurringOccurrences.reversed().toMutableList()

        val initialAccountsSnapshot = mutableAccounts.toList()
        val initialCategoriesSnapshot = mutableCategories.toList()
        val initialTransactionsSnapshot = mutableTransactions.toList()
        val initialBudgetsSnapshot = mutableBudgets.toList()
        val initialRulesSnapshot = mutableRules.toList()
        val initialOccurrencesSnapshot = mutableOccurrences.toList()

        val backup = sampleBackup(
            accounts = mutableAccounts,
            categories = mutableCategories,
            transactions = mutableTransactions,
            budgets = mutableBudgets,
            recurringTransactions = mutableRules,
            recurringOccurrences = mutableOccurrences
        )
        val backupCopyBefore = backup.copy()

        BackupContentFingerprint.compute(backup)

        assertEquals(initialAccountsSnapshot, mutableAccounts)
        assertEquals(initialCategoriesSnapshot, mutableCategories)
        assertEquals(initialTransactionsSnapshot, mutableTransactions)
        assertEquals(initialBudgetsSnapshot, mutableBudgets)
        assertEquals(initialRulesSnapshot, mutableRules)
        assertEquals(initialOccurrencesSnapshot, mutableOccurrences)
        assertEquals(backupCopyBefore, backup)
    }

    @Test
    fun `canonical json excludes envelope metadata and contains no secret fields`() {
        val backup = sampleBackup(
            formatVersion = 2,
            appVersion = "SENTINEL_APP_VERSION_9.9.9",
            createdAt = "2099-01-01T12:34:56Z",
            databaseSchemaVersion = 42,
            checksum = "SENTINEL_CHECKSUM_VALUE"
        )

        val canonicalJson = BackupContentFingerprint.canonicalJson(backup)

        assertFalse(canonicalJson.contains("formatVersion"))
        assertFalse(canonicalJson.contains("appVersion"))
        assertFalse(canonicalJson.contains("SENTINEL_APP_VERSION_9.9.9"))
        assertFalse(canonicalJson.contains("2099-01-01T12:34:56Z"))
        assertFalse(canonicalJson.contains("databaseSchemaVersion"))
        assertFalse(canonicalJson.contains("checksum"))
        assertFalse(canonicalJson.contains("SENTINEL_CHECKSUM_VALUE"))
        assertFalse(canonicalJson.contains("apiKey", ignoreCase = true))
        assertFalse(canonicalJson.contains("byok", ignoreCase = true))
        assertFalse(canonicalJson.contains("gemini", ignoreCase = true))
        assertFalse(canonicalJson.contains("lastBackup", ignoreCase = true))

        assertTrue(canonicalJson.contains("\"accounts\""))
        assertTrue(canonicalJson.contains("\"categories\""))
        assertTrue(canonicalJson.contains("\"transactions\""))
        assertTrue(canonicalJson.contains("\"budgets\""))
        assertTrue(canonicalJson.contains("\"recurringTransactions\""))
        assertTrue(canonicalJson.contains("\"recurringOccurrences\""))
        assertTrue(canonicalJson.contains("\"settings\""))
    }

    // endregion
}
