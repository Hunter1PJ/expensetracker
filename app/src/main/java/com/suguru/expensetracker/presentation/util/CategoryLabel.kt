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
private fun Category.builtInLabelResource(): Int? {
    if (!isSystem) return null
    return when (name.trim().lowercase()) {
        "groceries" -> R.string.category_groceries
        "food" -> R.string.category_food
        "transport" -> R.string.category_transport
        "study" -> R.string.category_study
        "shopping" -> R.string.category_shopping
        "health" -> R.string.category_health
        "entertainment" -> R.string.category_entertainment
        "bills" -> R.string.category_bills
        "housing" -> R.string.category_housing
        "personal care" -> R.string.category_personal_care
        "subscriptions" -> R.string.category_subscriptions
        "travel" -> R.string.category_travel
        "gifts & donations" -> R.string.category_gifts_donations
        "salary" -> R.string.category_salary
        "freelance" -> R.string.category_freelance
        "business" -> R.string.category_business
        "gift" -> R.string.category_gift
        "other" -> R.string.category_other
        else -> null
    }
}

@Composable
fun categoryDisplayName(category: Category): String {
    val resource = category.builtInLabelResource()
    return if (resource == null) category.name else stringResource(resource)
}
