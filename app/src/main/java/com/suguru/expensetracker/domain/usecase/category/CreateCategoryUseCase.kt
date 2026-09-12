package com.suguru.expensetracker.domain.usecase.category

import com.suguru.expensetracker.domain.error.DomainException
import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.FeatureGateResult
import com.suguru.expensetracker.domain.policy.MonetizationPolicy
import com.suguru.expensetracker.domain.repository.CategoryRepository
import com.suguru.expensetracker.domain.repository.EntitlementRepository
import kotlinx.coroutines.flow.first

class CreateCategoryUseCase(
    private val categoryRepository: CategoryRepository,
    private val entitlementRepository: EntitlementRepository? = null,
    private val monetizationPolicy: MonetizationPolicy = MonetizationPolicy()
) {
    suspend operator fun invoke(category: Category): Long {
        if (category.name.isBlank()) {
            throw DomainException.InvalidCategory("Category name cannot be blank")
        }
        if (category.id != 0L) {
            throw DomainException.InvalidCategory("New category must have an ID of 0L")
        }

        if (!category.isSystem && entitlementRepository != null) {
            val entitlement = entitlementRepository.entitlement.value
            val customCount = categoryRepository.observeActiveCategories().first().count { !it.isSystem }
            when (val gate = monetizationPolicy.checkCustomCategoryCreation(entitlement, customCount)) {
                is FeatureGateResult.LimitReached -> {
                    throw DomainException.FeatureLimitReached(gate.feature, gate.currentCount, gate.freeLimit)
                }
                is FeatureGateResult.Allowed -> { /* Allowed */ }
            }
        }

        return categoryRepository.insertCategory(category)
    }
}
