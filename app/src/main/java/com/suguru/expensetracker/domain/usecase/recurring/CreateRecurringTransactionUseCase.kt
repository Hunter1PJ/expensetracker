package com.suguru.expensetracker.domain.usecase.recurring

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.FeatureGateResult
import com.suguru.expensetracker.domain.model.RecurringTransaction
import com.suguru.expensetracker.domain.policy.MonetizationPolicy
import com.suguru.expensetracker.domain.repository.AccountRepository
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.EntitlementRepository
import com.suguru.expensetracker.domain.repository.RecurringTransactionRepository
import kotlinx.coroutines.flow.first

class CreateRecurringTransactionUseCase(
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val entitlementRepository: EntitlementRepository? = null,
    private val monetizationPolicy: MonetizationPolicy = MonetizationPolicy()
) {
    private val validator = RecurringTransactionValidator(accountRepository, categoryRepository)

    suspend operator fun invoke(rule: RecurringTransaction): Long {
        if (rule.id != 0L) {
            throw DomainException.InvalidTransactionId(rule.id)
        }

        validator.validate(rule)

        if (entitlementRepository != null) {
            val entitlement = entitlementRepository.entitlement.value
            val activeRuleCount = recurringTransactionRepository.observeAllRecurringTransactions().first().count { it.isActive }
            when (val gate = monetizationPolicy.checkRecurringRuleCreation(entitlement, activeRuleCount)) {
                is FeatureGateResult.LimitReached -> {
                    throw DomainException.FeatureLimitReached(gate.feature, gate.currentCount, gate.freeLimit)
                }
                is FeatureGateResult.Allowed -> { /* Allowed */ }
            }
        }

        val preparedRule = rule.copy(
            nextOccurrence = rule.startDate,
            isActive = true
        )

        return recurringTransactionRepository.insertRecurringTransaction(preparedRule)
    }
}
