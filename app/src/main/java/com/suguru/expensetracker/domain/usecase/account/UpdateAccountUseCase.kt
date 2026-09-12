package com.suguru.expensetracker.domain.usecase.account

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.model.FeatureGateResult
import com.suguru.expensetracker.domain.policy.MonetizationPolicy
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.first

class UpdateAccountUseCase(
    private val accountRepository: AccountRepository,
    private val entitlementRepository: EntitlementRepository? = null,
    private val monetizationPolicy: MonetizationPolicy = MonetizationPolicy()
) {
    suspend operator fun invoke(account: Account) {
        if (account.id <= 0L) {
            throw DomainException.InvalidAccount("Account ID must be greater than 0 for update")
        }
        if (account.name.isBlank()) {
            throw DomainException.InvalidAccount("Account name cannot be blank")
        }
        val existing = accountRepository.getAccountById(account.id)
            ?: throw DomainException.AccountNotFound(account.id)

        // If reactivating an archived account, enforce monetization gate
        if (existing.isArchived && !account.isArchived && entitlementRepository != null) {
            val entitlement = entitlementRepository.entitlement.value
            val activeCount = accountRepository.observeActiveAccounts().first().size
            when (val gate = monetizationPolicy.checkAccountCreation(entitlement, activeCount)) {
                is FeatureGateResult.LimitReached -> {
                    throw DomainException.FeatureLimitReached(gate.feature, gate.currentCount, gate.freeLimit)
                }
                is FeatureGateResult.Allowed -> { /* Allowed */ }
            }
        }

        accountRepository.updateAccount(account)
    }
}
