package com.example.presentation.common

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.domain.model.AccountType
import com.example.domain.model.CategoryType

/**
 * Visual metadata for curated icon options.
 */
data class IconOption(
    val id: String,
    val label: String,
    val icon: ImageVector
)

/**
 * Visual metadata for curated color options.
 */
data class ColorOption(
    val hex: String,
    val label: String,
    val color: Color
)

object FinanceVisuals {

    val ACCOUNT_ICONS: List<IconOption> = listOf(
        IconOption("account_balance", "Bank", Icons.Default.AccountBalance),
        IconOption("wallet", "Wallet", Icons.Default.AccountBalanceWallet),
        IconOption("credit_card", "Card", Icons.Default.CreditCard),
        IconOption("savings", "Savings", Icons.Default.Savings),
        IconOption("trending_up", "Investment", Icons.Default.TrendingUp),
        IconOption("payments", "Cash", Icons.Default.Payments),
        IconOption("attach_money", "Money", Icons.Default.AttachMoney),
        IconOption("star", "Featured", Icons.Default.Star)
    )

    val CATEGORY_ICONS: List<IconOption> = listOf(
        IconOption("fastfood", "Food & Dining", Icons.Default.Fastfood),
        IconOption("directions_car", "Transport", Icons.Default.DirectionsCar),
        IconOption("shopping_bag", "Shopping", Icons.Default.CreditCard),
        IconOption("movie", "Entertainment", Icons.Default.Movie),
        IconOption("receipt", "Bills & Utilities", Icons.Default.Receipt),
        IconOption("local_hospital", "Health", Icons.Default.LocalHospital),
        IconOption("school", "Education", Icons.Default.School),
        IconOption("work", "Salary & Income", Icons.Default.Work),
        IconOption("card_giftcard", "Gifts", Icons.Default.CardGiftcard),
        IconOption("home", "Housing", Icons.Default.Home),
        IconOption("fitness_center", "Fitness", Icons.Default.FitnessCenter),
        IconOption("flight", "Travel", Icons.Default.Flight),
        IconOption("pets", "Pets", Icons.Default.Pets),
        IconOption("category", "Other", Icons.Default.Category)
    )

    val CURATED_COLORS: List<ColorOption> = listOf(
        ColorOption("#10B981", "Emerald", Color(0xFF10B981)),
        ColorOption("#3B82F6", "Blue", Color(0xFF3B82F6)),
        ColorOption("#6366F1", "Indigo", Color(0xFF6366F1)),
        ColorOption("#8B5CF6", "Purple", Color(0xFF8B5CF6)),
        ColorOption("#F59E0B", "Amber", Color(0xFFF59E0B)),
        ColorOption("#EF4444", "Coral", Color(0xFFEF4444)),
        ColorOption("#EC4899", "Pink", Color(0xFFEC4899)),
        ColorOption("#06B6D4", "Cyan", Color(0xFF06B6D4)),
        ColorOption("#14B8A6", "Teal", Color(0xFF14B8A6)),
        ColorOption("#64748B", "Slate", Color(0xFF64748B))
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

    fun getAccountTypeLabel(type: AccountType): String {
        return when (type) {
            AccountType.CASH -> "Cash"
            AccountType.BANK -> "Bank Account"
            AccountType.CARD -> "Credit Card"
            AccountType.SAVINGS -> "Savings"
            AccountType.INVESTMENT -> "Investment"
            AccountType.OTHER -> "Other"
        }
    }

    fun getCategoryTypeLabel(type: CategoryType): String {
        return when (type) {
            CategoryType.EXPENSE -> "Expense"
            CategoryType.INCOME -> "Income"
            CategoryType.BOTH -> "Both"
        }
    }
}
