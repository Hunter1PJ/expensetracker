package com.suguru.expensetracker.presentation.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.suguru.expensetracker.R
import com.suguru.expensetracker.domain.model.Category

/**
 * Translates only seeded built-in categories. User-created names are always shown verbatim.
 * Stored category names and IDs remain untouched.
 */
@StringRes
internal fun builtInCategoryLabelResource(name: String, isSystem: Boolean): Int? {
    if (!isSystem) return null
    return builtInCategoryLabelResources[name.trim().lowercase(java.util.Locale.ROOT)]
}

internal val builtInCategoryLabelResources: Map<String, Int> = mapOf(
    "groceries" to R.string.category_groceries,
    "food" to R.string.category_food,
    "transport" to R.string.category_transport,
    "study" to R.string.category_study,
    "shopping" to R.string.category_shopping,
    "health" to R.string.category_health,
    "entertainment" to R.string.category_entertainment,
    "bills" to R.string.category_bills,
    "housing" to R.string.category_housing,
    "personal care" to R.string.category_personal_care,
    "subscriptions" to R.string.category_subscriptions,
    "travel" to R.string.category_travel,
    "gifts & donations" to R.string.category_gifts_donations,
    "salary" to R.string.category_salary,
    "freelance" to R.string.category_freelance,
    "business" to R.string.category_business,
    "gift" to R.string.category_gift,
    "other" to R.string.category_other
)

@Composable
fun categoryDisplayName(category: Category): String =
    categoryDisplayName(category.name, category.isSystem)

@Composable
fun categoryDisplayName(name: String, isSystem: Boolean): String {
    val resource = builtInCategoryLabelResource(name, isSystem)
    return if (resource == null) name else stringResource(resource)
}
