package com.suguru.expensetracker.domain.util

import com.suguru.expensetracker.domain.model.Category
import com.suguru.expensetracker.domain.model.CategoryType

/**
 * Pure domain utility for sorting categories by transaction usage.
 * Both transaction types keep a curated deterministic fallback order, and `Other` is always last.
 * Custom and archived categories are handled properly.
 */
object CategorySorter {

    /**
     * Curated initial tie-breaker order for expense categories as specified in requirements:
     * 1. Food
     * 2. Groceries
     * 3. Transport
     * 4. Bills
     * 5. Housing
     * 6. Shopping
     * 7. Health
     * 8. Study
     * 9. Entertainment
     * 10. Personal Care
     * 11. Subscriptions
     * 12. Travel
     * 13. Gifts & Donations
     * 14. Other
     */
    val CURATED_EXPENSE_TIE_BREAKER: List<String> = listOf(
        "Food",
        "Groceries",
        "Transport",
        "Bills",
        "Housing",
        "Shopping",
        "Health",
        "Study",
        "Entertainment",
        "Personal Care",
        "Subscriptions",
        "Travel",
        "Gifts & Donations",
        "Other"
    )

    /** Fallback order for unused or equally used income categories. */
    val CURATED_INCOME_TIE_BREAKER: List<String> = listOf(
        "Salary",
        "Business",
        "Freelance",
        "Gift",
        "Other"
    )

    private val EXPENSE_TIE_BREAKER_INDEX = indexByName(CURATED_EXPENSE_TIE_BREAKER)
    private val INCOME_TIE_BREAKER_INDEX = indexByName(CURATED_INCOME_TIE_BREAKER)

    private fun indexByName(names: List<String>): Map<String, Int> =
        names.mapIndexed { index, name -> name.lowercase() to index }.toMap()

    /**
     * Sorts a list of [categories] given a map of [categoryUsageCounts] (categoryId -> transaction count).
     *
     * Rules:
     * 1. "Other" category is ALWAYS last, regardless of usage count.
     * 2. Higher usage count appears first.
     * 3. Equal usage count breaks ties using curated initial order.
     * 4. Categories not in curated list are placed after curated ones (before "Other"), sorted by name.
     */
    fun sortExpenseCategories(
        categories: List<Category>,
        categoryUsageCounts: Map<Long, Int> = emptyMap()
    ): List<Category> = sortByUsage(categories, categoryUsageCounts, EXPENSE_TIE_BREAKER_INDEX)

    /** Sorts income categories by saved Income transaction usage, keeping `Other` last. */
    fun sortIncomeCategories(
        categories: List<Category>,
        categoryUsageCounts: Map<Long, Int> = emptyMap()
    ): List<Category> = sortByUsage(categories, categoryUsageCounts, INCOME_TIE_BREAKER_INDEX)

    private fun sortByUsage(
        categories: List<Category>,
        usageCounts: Map<Long, Int>,
        tieBreakerIndex: Map<String, Int>
    ): List<Category> = categories.sortedWith { a, b ->
        compareByUsage(a, b, usageCounts, tieBreakerIndex)
    }

    private fun compareByUsage(
        a: Category,
        b: Category,
        usageCounts: Map<Long, Int>,
        tieBreakerIndex: Map<String, Int>
    ): Int {
        val aIsOther = isOtherCategory(a)
        val bIsOther = isOtherCategory(b)

        // "Other" is always last
        if (aIsOther && !bIsOther) return 1
        if (!aIsOther && bIsOther) return -1
        if (aIsOther && bIsOther) return a.name.compareTo(b.name, ignoreCase = true)

        val aCount = usageCounts[a.id] ?: 0
        val bCount = usageCounts[b.id] ?: 0

        // Higher usage first
        if (aCount != bCount) {
            return bCount.compareTo(aCount)
        }

        // Tie-breaker: Curated order
        val aCuratedIndex = tieBreakerIndex[a.name.trim().lowercase()] ?: Int.MAX_VALUE
        val bCuratedIndex = tieBreakerIndex[b.name.trim().lowercase()] ?: Int.MAX_VALUE

        if (aCuratedIndex != bCuratedIndex) {
            return aCuratedIndex.compareTo(bCuratedIndex)
        }

        // Secondary fallback: System before custom, then alphabetical
        if (a.isSystem != b.isSystem) {
            return if (a.isSystem) -1 else 1
        }
        return a.name.compareTo(b.name, ignoreCase = true)
    }

    private fun isOtherCategory(category: Category): Boolean {
        return category.name.trim().equals("Other", ignoreCase = true)
    }
}
