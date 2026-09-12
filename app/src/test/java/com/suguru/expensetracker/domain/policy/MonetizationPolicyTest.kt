package com.suguru.expensetracker.domain.policy

import com.suguru.expensetracker.domain.model.FeatureGateResult
import com.suguru.expensetracker.domain.model.ProEntitlement
import com.suguru.expensetracker.domain.model.ProFeature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonetizationPolicyTest {

    private val policy = MonetizationPolicy()

    @Test
    fun `accounts gate - free user under limit is allowed`() {
        val freeEntitlement = ProEntitlement.Free
        val result = policy.checkAccountCreation(freeEntitlement, currentActiveCount = 1)
        assertTrue(result is FeatureGateResult.Allowed)
    }

    @Test
    fun `accounts gate - free user at limit of 2 is blocked`() {
        val freeEntitlement = ProEntitlement.Free
        val result = policy.checkAccountCreation(freeEntitlement, currentActiveCount = 2)
        assertTrue(result is FeatureGateResult.LimitReached)
        val limitResult = result as FeatureGateResult.LimitReached
        assertEquals(ProFeature.ACCOUNTS, limitResult.feature)
        assertEquals(2, limitResult.currentCount)
        assertEquals(2, limitResult.freeLimit)
    }

    @Test
    fun `accounts gate - free user above limit of 2 (grandfathered) is blocked from creating new`() {
        val freeEntitlement = ProEntitlement.Free
        val result = policy.checkAccountCreation(freeEntitlement, currentActiveCount = 5)
        assertTrue(result is FeatureGateResult.LimitReached)
        val limitResult = result as FeatureGateResult.LimitReached
        assertEquals(5, limitResult.currentCount)
    }

    @Test
    fun `accounts gate - pro user at any count is allowed`() {
        val proEntitlement = ProEntitlement.Pro
        val result = policy.checkAccountCreation(proEntitlement, currentActiveCount = 10)
        assertTrue(result is FeatureGateResult.Allowed)
    }

    @Test
    fun `custom categories gate - free user under limit of 3 is allowed`() {
        val freeEntitlement = ProEntitlement.Free
        val result = policy.checkCustomCategoryCreation(freeEntitlement, currentCustomCount = 2)
        assertTrue(result is FeatureGateResult.Allowed)
    }

    @Test
    fun `custom categories gate - free user at limit of 3 is blocked`() {
        val freeEntitlement = ProEntitlement.Free
        val result = policy.checkCustomCategoryCreation(freeEntitlement, currentCustomCount = 3)
        assertTrue(result is FeatureGateResult.LimitReached)
        val limitResult = result as FeatureGateResult.LimitReached
        assertEquals(ProFeature.CUSTOM_CATEGORIES, limitResult.feature)
        assertEquals(3, limitResult.currentCount)
        assertEquals(3, limitResult.freeLimit)
    }

    @Test
    fun `custom categories gate - pro user at any count is allowed`() {
        val proEntitlement = ProEntitlement.Pro
        val result = policy.checkCustomCategoryCreation(proEntitlement, currentCustomCount = 15)
        assertTrue(result is FeatureGateResult.Allowed)
    }

    @Test
    fun `budgets gate - free user under limit of 2 is allowed`() {
        val freeEntitlement = ProEntitlement.Free
        val result = policy.checkBudgetCreation(freeEntitlement, currentActiveBudgetCount = 1)
        assertTrue(result is FeatureGateResult.Allowed)
    }

    @Test
    fun `budgets gate - free user at limit of 2 is blocked`() {
        val freeEntitlement = ProEntitlement.Free
        val result = policy.checkBudgetCreation(freeEntitlement, currentActiveBudgetCount = 2)
        assertTrue(result is FeatureGateResult.LimitReached)
        val limitResult = result as FeatureGateResult.LimitReached
        assertEquals(ProFeature.BUDGETS, limitResult.feature)
        assertEquals(2, limitResult.currentCount)
        assertEquals(2, limitResult.freeLimit)
    }

    @Test
    fun `budgets gate - pro user at any count is allowed`() {
        val proEntitlement = ProEntitlement.Pro
        val result = policy.checkBudgetCreation(proEntitlement, currentActiveBudgetCount = 8)
        assertTrue(result is FeatureGateResult.Allowed)
    }

    @Test
    fun `recurring rules gate - free user under limit of 2 is allowed`() {
        val freeEntitlement = ProEntitlement.Free
        val result = policy.checkRecurringRuleCreation(freeEntitlement, currentActiveRuleCount = 1)
        assertTrue(result is FeatureGateResult.Allowed)
    }

    @Test
    fun `recurring rules gate - free user at limit of 2 is blocked`() {
        val freeEntitlement = ProEntitlement.Free
        val result = policy.checkRecurringRuleCreation(freeEntitlement, currentActiveRuleCount = 2)
        assertTrue(result is FeatureGateResult.LimitReached)
        val limitResult = result as FeatureGateResult.LimitReached
        assertEquals(ProFeature.RECURRING_RULES, limitResult.feature)
        assertEquals(2, limitResult.currentCount)
        assertEquals(2, limitResult.freeLimit)
    }

    @Test
    fun `recurring rules gate - pro user at any count is allowed`() {
        val proEntitlement = ProEntitlement.Pro
        val result = policy.checkRecurringRuleCreation(proEntitlement, currentActiveRuleCount = 20)
        assertTrue(result is FeatureGateResult.Allowed)
    }
}
