package com.suguru.expensetracker.domain.ai

import com.suguru.expensetracker.domain.model.Category

/**
 * Maps AI-generated category suggestions to actual existing category records.
 *
 * Uses semantic alias matching so that common AI labels like "Food & Dining" or
 * "Transportation" resolve to the user's actual categories ("Food", "Transport").
 *
 * Rules:
 * - Exact name match (case-insensitive) always wins.
 * - Alias/semantic match is used as fallback.
 * - Returns null if no confident mapping is found (user picks manually).
 * - Never creates new categories from AI output.
 */
object AiCategoryNormalizer {

    /**
     * Maps of normalized lowercase alias → canonical category name (lowercase).
     * Each alias should map to exactly one canonical category.
     * Aliases are intentionally narrow to avoid misclassification.
     */
    private val ALIAS_MAP: Map<String, String> = buildMap {
        // Food aliases
        put("food & dining", "food")
        put("food and dining", "food")
        put("dining", "food")
        put("restaurant", "food")
        put("restaurants", "food")
        put("eating out", "food")
        put("takeaway", "food")
        put("takeout", "food")
        put("fast food", "food")
        put("cafe", "food")
        put("coffee", "food")
        put("pizza", "food")
        put("lunch", "food")
        put("dinner", "food")
        put("breakfast", "food")
        put("snack", "food")
        put("snacks", "food")
        put("meal", "food")
        put("meals", "food")

        // Groceries aliases
        put("grocery", "groceries")
        put("supermarket", "groceries")
        put("market", "groceries")
        put("groceries & supplies", "groceries")

        // Transport aliases
        put("transportation", "transport")
        put("taxi", "transport")
        put("uber", "transport")
        put("ride", "transport")
        put("bus", "transport")
        put("metro", "transport")
        put("subway", "transport")
        put("fuel", "transport")
        put("gas", "transport")
        put("petrol", "transport")
        put("parking", "transport")
        put("car", "transport")
        put("auto", "transport")
        put("commute", "transport")

        // Bills aliases
        put("utilities", "bills")
        put("utility", "bills")
        put("electricity", "bills")
        put("water bill", "bills")
        put("gas bill", "bills")
        put("phone bill", "bills")
        put("internet", "bills")
        put("mobile", "bills")

        // Housing aliases
        put("rent", "housing")
        put("mortgage", "housing")
        put("home", "housing")
        put("apartment", "housing")
        put("accommodation", "housing")
        put("property", "housing")
        put("housing & rent", "housing")

        // Health aliases
        put("healthcare", "health")
        put("medical", "health")
        put("medicine", "health")
        put("pharmacy", "health")
        put("doctor", "health")
        put("hospital", "health")
        put("dental", "health")
        put("fitness", "health")
        put("gym", "health")

        // Shopping aliases
        put("clothing", "shopping")
        put("clothes", "shopping")
        put("fashion", "shopping")
        put("electronics", "shopping")
        put("online shopping", "shopping")

        // Entertainment aliases
        put("movies", "entertainment")
        put("cinema", "entertainment")
        put("gaming", "entertainment")
        put("games", "entertainment")
        put("streaming", "entertainment")
        put("music", "entertainment")
        put("concert", "entertainment")
        put("hobby", "entertainment")
        put("hobbies", "entertainment")
        put("sports", "entertainment")
        put("recreation", "entertainment")

        // Study aliases
        put("education", "study")
        put("school", "study")
        put("university", "study")
        put("course", "study")
        put("courses", "study")
        put("tuition", "study")
        put("books", "study")
        put("learning", "study")

        // Personal Care aliases
        put("personal", "personal care")
        put("beauty", "personal care")
        put("haircut", "personal care")
        put("salon", "personal care")
        put("cosmetics", "personal care")
        put("skincare", "personal care")
        put("spa", "personal care")
        put("grooming", "personal care")

        // Subscriptions aliases
        put("subscription", "subscriptions")
        put("membership", "subscriptions")
        put("netflix", "subscriptions")
        put("spotify", "subscriptions")
        put("premium", "subscriptions")
        put("recurring payment", "subscriptions")

        // Travel aliases
        put("vacation", "travel")
        put("trip", "travel")
        put("hotel", "travel")
        put("flight", "travel")
        put("flights", "travel")
        put("tourism", "travel")
        put("holiday", "travel")

        // Gifts & Donations aliases
        put("gifts", "gifts & donations")
        put("gift", "gifts & donations")
        put("donation", "gifts & donations")
        put("donations", "gifts & donations")
        put("charity", "gifts & donations")
        put("present", "gifts & donations")
        put("presents", "gifts & donations")

        // Income aliases
        put("salary & wages", "salary")
        put("wages", "salary")
        put("paycheck", "salary")
        put("pay", "salary")
        put("freelance work", "freelance")
        put("side hustle", "freelance")
        put("contract work", "freelance")
        put("business income", "business")
        put("revenue", "business")
        put("gift money", "gift")
        put("gift received", "gift")
    }

    /**
     * Resolves an AI-suggested category name to an actual [Category] from the user's available
     * categories, or `null` if no confident mapping is possible.
     *
     * @param aiSuggestion The raw category name from AI (e.g., "Food & Dining")
     * @param availableCategories The user's actual active categories
     * @return The matched [Category], or null if unresolved
     */
    fun resolve(aiSuggestion: String?, availableCategories: List<Category>): Category? {
        if (aiSuggestion.isNullOrBlank() || availableCategories.isEmpty()) return null

        val query = aiSuggestion.trim().lowercase()

        // 1. Exact name match (case-insensitive)
        val exactMatch = availableCategories.find {
            it.name.trim().lowercase() == query
        }
        if (exactMatch != null) return exactMatch

        // 2. Alias map lookup
        val canonicalName = ALIAS_MAP[query]
        if (canonicalName != null) {
            val aliasMatch = availableCategories.find {
                it.name.trim().lowercase() == canonicalName
            }
            if (aliasMatch != null) return aliasMatch
        }

        // 3. Partial word matching - check if any word in the AI suggestion matches a category name
        val queryWords = query.split(Regex("[\\s&,]+")).filter { it.isNotBlank() }
        for (word in queryWords) {
            val wordMatch = availableCategories.find {
                it.name.trim().lowercase() == word
            }
            if (wordMatch != null) return wordMatch
        }

        // 4. Check if any alias key contains a word match
        for (word in queryWords) {
            val aliasEntry = ALIAS_MAP.entries.find { (alias, _) ->
                alias == word || alias.split(Regex("[\\s&,]+")).any { it == word }
            }
            if (aliasEntry != null) {
                val resolved = availableCategories.find {
                    it.name.trim().lowercase() == aliasEntry.value
                }
                if (resolved != null) return resolved
            }
        }

        // No confident mapping found
        return null
    }
}
