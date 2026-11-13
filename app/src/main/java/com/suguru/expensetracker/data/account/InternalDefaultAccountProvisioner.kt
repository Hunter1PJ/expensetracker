package com.suguru.expensetracker.data.account

import androidx.room.withTransaction
import com.suguru.expensetracker.data.local.ExpenseTrackerDatabase
import com.suguru.expensetracker.data.local.entity.AccountEntity
import com.suguru.expensetracker.data.mapper.toDomain
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.time.Instant

const val INTERNAL_DEFAULT_ACCOUNT_KEY = "default"

sealed interface InternalDefaultAccountProvisioningResult {
    data class Ready(val internalAccount: Account) : InternalDefaultAccountProvisioningResult
    data object RequiresCurrencySelection : InternalDefaultAccountProvisioningResult
    data class Failure(val error: Throwable) : InternalDefaultAccountProvisioningResult
}

class InternalDefaultAccountProvisioner(
    private val database: ExpenseTrackerDatabase,
    private val settingsRepository: SettingsRepository
) {
    suspend fun ensureProvisioned(): InternalDefaultAccountProvisioningResult {
        return try {
            val accountDao = database.accountDao()
            val activeAccounts = accountDao.getActiveAccounts()
            val existingInternalAccount = activeAccounts.firstOrNull { it.internalKey == INTERNAL_DEFAULT_ACCOUNT_KEY }
            if (existingInternalAccount != null) {
                return InternalDefaultAccountProvisioningResult.Ready(existingInternalAccount.toDomain())
            }

            val preferredCurrencyCode = settingsRepository.settings.first().preferredCurrencyCode?.trim()
            val resolvedCurrency = when {
                !preferredCurrencyCode.isNullOrBlank() -> preferredCurrencyCode
                activeAccounts.size == 1 -> activeAccounts.first().initialBalanceCurrencyCode
                activeAccounts.size > 1 -> return InternalDefaultAccountProvisioningResult.RequiresCurrencySelection
                else -> "UZS"
            }

            val provisionedAccount = database.withTransaction {
                val dao = database.accountDao()
                val persistedInternal = dao.getInternalAccount(INTERNAL_DEFAULT_ACCOUNT_KEY)
                if (persistedInternal != null) {
                    if (persistedInternal.isArchived) {
                        dao.activateInternalAccount(INTERNAL_DEFAULT_ACCOUNT_KEY)
                    }
                    return@withTransaction dao.getInternalAccount(INTERNAL_DEFAULT_ACCOUNT_KEY)
                }

                val candidate = AccountEntity(
                    name = "Internal Default",
                    type = AccountType.CASH,
                    initialBalanceAmountInMinorUnits = 0L,
                    initialBalanceCurrencyCode = resolvedCurrency,
                    iconName = "account_balance",
                    colorHex = "#10B981",
                    isArchived = false,
                    createdAt = Instant.now(),
                    internalKey = INTERNAL_DEFAULT_ACCOUNT_KEY
                )

                try {
                    dao.insertAccountIfAbsent(candidate)
                    dao.getInternalAccount(INTERNAL_DEFAULT_ACCOUNT_KEY)
                } catch (_: Exception) {
                    dao.getInternalAccount(INTERNAL_DEFAULT_ACCOUNT_KEY)
                }
            }

            val finalAccount = provisionedAccount ?: return InternalDefaultAccountProvisioningResult.Failure(
                IllegalStateException("Internal default account was not available after provisioning.")
            )
            InternalDefaultAccountProvisioningResult.Ready(finalAccount.toDomain())
        } catch (e: Exception) {
            InternalDefaultAccountProvisioningResult.Failure(e)
        }
    }
}
