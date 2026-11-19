package com.suguru.expensetracker.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.suguru.expensetracker.domain.model.AccountType
import com.suguru.expensetracker.domain.model.CategoryType
import com.suguru.expensetracker.R

/**
 * Visual metadata for curated icon options.
 */
data class IconOption(
    val id: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

/**
 * Visual metadata for curated color options.
 */
data class ColorOption(
    val hex: String,
    @StringRes val labelRes: Int,
    val color: Color
)

object FinanceVisuals {

    val ACCOUNT_ICONS: List<IconOption> = listOf(
        IconOption("account_balance", R.string.icon_bank, Icons.Default.AccountBalance),
        IconOption("wallet", R.string.icon_wallet, Icons.Default.AccountBalanceWallet),
        IconOption("credit_card", R.string.icon_card, Icons.Default.CreditCard),
        IconOption("savings", R.string.icon_savings, Icons.Default.Savings),
        IconOption("trending_up", R.string.icon_investment, Icons.Default.TrendingUp),
        IconOption("payments", R.string.icon_cash, Icons.Default.Payments),
        IconOption("attach_money", R.string.icon_money, Icons.Default.AttachMoney),
        IconOption("star", R.string.icon_featured, Icons.Default.Star)
    )

    val CATEGORY_ICONS: List<IconOption> = listOf(
        IconOption("fastfood", R.string.icon_food_dining, Icons.Default.Fastfood),
        IconOption("directions_car", R.string.icon_transport, Icons.Default.DirectionsCar),
        IconOption("shopping_bag", R.string.icon_shopping, Icons.Default.CreditCard),
        IconOption("movie", R.string.icon_entertainment, Icons.Default.Movie),
        IconOption("receipt", R.string.icon_bills_utilities, Icons.Default.Receipt),
        IconOption("local_hospital", R.string.icon_health, Icons.Default.LocalHospital),
        IconOption("school", R.string.icon_education, Icons.Default.School),
        IconOption("work", R.string.icon_salary_income, Icons.Default.Work),
        IconOption("card_giftcard", R.string.icon_gifts, Icons.Default.CardGiftcard),
        IconOption("home", R.string.icon_housing, Icons.Default.Home),
        IconOption("fitness_center", R.string.icon_fitness, Icons.Default.FitnessCenter),
        IconOption("flight", R.string.icon_travel, Icons.Default.Flight),
        IconOption("pets", R.string.icon_pets, Icons.Default.Pets),
        IconOption("category", R.string.category_other, Icons.Default.Category)
    )

    val CURATED_COLORS: List<ColorOption> = listOf(
        ColorOption("#10B981", R.string.color_emerald, Color(0xFF10B981)),
        ColorOption("#3B82F6", R.string.color_blue, Color(0xFF3B82F6)),
        ColorOption("#6366F1", R.string.color_indigo, Color(0xFF6366F1)),
        ColorOption("#8B5CF6", R.string.color_purple, Color(0xFF8B5CF6)),
        ColorOption("#F59E0B", R.string.color_amber, Color(0xFFF59E0B)),
        ColorOption("#EF4444", R.string.color_coral, Color(0xFFEF4444)),
        ColorOption("#EC4899", R.string.color_pink, Color(0xFFEC4899)),
        ColorOption("#06B6D4", R.string.color_cyan, Color(0xFF06B6D4)),
        ColorOption("#14B8A6", R.string.color_teal, Color(0xFF14B8A6)),
        ColorOption("#64748B", R.string.color_slate, Color(0xFF64748B))
    )

    fun getAccountIcon(iconName: String): ImageVector {
        return ACCOUNT_ICONS.firstOrNull { it.id == iconName }?.icon
            ?: when (iconName) {
                "account_balance_wallet", "wallet" -> Icons.Default.AccountBalanceWallet
                "credit_card" -> Icons.Default.CreditCard
                "savings" -> Icons.Default.Savings
                "trending_up" -> Icons.Default.TrendingUp
                "payments" -> Icons.Default.Payments
                "attach_money" -> Icons.Default.AttachMoney
                "star", "stars" -> Icons.Default.Star
                else -> Icons.Default.AccountBalance
            }
    }

    fun getCategoryIcon(iconName: String): ImageVector {
        return CATEGORY_ICONS.firstOrNull { it.id == iconName }?.icon
            ?: when (iconName) {
                "fastfood", "restaurant", "local_cafe" -> Icons.Default.Fastfood
                "directions_car", "commute" -> Icons.Default.DirectionsCar
                "shopping_bag", "shopping_cart" -> Icons.Default.CreditCard
                "movie", "theater_comedy" -> Icons.Default.Movie
                "receipt", "receipt_long" -> Icons.Default.Receipt
                "local_hospital", "medical_services" -> Icons.Default.LocalHospital
                "school" -> Icons.Default.School
                "work" -> Icons.Default.Work
                "card_giftcard" -> Icons.Default.CardGiftcard
                "home" -> Icons.Default.Home
                "fitness_center" -> Icons.Default.FitnessCenter
                "flight" -> Icons.Default.Flight
                "pets" -> Icons.Default.Pets
                else -> Icons.Default.Category
            }
    }

    fun parseColorHex(hex: String, defaultColor: Color = Color(0xFF10B981)): Color {
        return try {
            val cleanHex = hex.removePrefix("#").trim()
            val colorLong = when (cleanHex.length) {
                6 -> ("FF$cleanHex").toLong(16)
                8 -> cleanHex.toLong(16)
                else -> return defaultColor
            }
            Color(colorLong)
        } catch (e: Exception) {
            defaultColor
        }
    }

    @StringRes
    fun getAccountTypeLabelRes(type: AccountType): Int {
        return when (type) {
            AccountType.CASH -> R.string.account_type_cash
            AccountType.BANK -> R.string.account_type_account_bank
            AccountType.CARD -> R.string.account_type_credit_card
            AccountType.SAVINGS -> R.string.account_type_savings
            AccountType.INVESTMENT -> R.string.account_type_investment
            AccountType.OTHER -> R.string.account_type_other
        }
    }

    @StringRes
    fun getCategoryTypeLabelRes(type: CategoryType): Int {
        return when (type) {
            CategoryType.EXPENSE -> R.string.type_expense
            CategoryType.INCOME -> R.string.type_income
            CategoryType.BOTH -> R.string.category_type_both
        }
    }
}
