package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.BudgetPeriodType
import java.time.LocalDate

/**
 * Room Entity representing a budget limit for a category or overall spending.
 *
 * [categoryId] = null indicates an overall monthly budget.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["category_id"]),
        Index(value = ["start_date"]),
        Index(value = ["end_date"]),
        Index(value = ["is_active"]),
        Index(value = ["start_date", "end_date"])
    ]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "category_id")
    val categoryId: Long?,
    @ColumnInfo(name = "limit_amount_minor_units")
    val limitAmountInMinorUnits: Long,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "period_type")
    val periodType: BudgetPeriodType,
    @ColumnInfo(name = "start_date")
    val startDate: LocalDate,
    @ColumnInfo(name = "end_date")
    val endDate: LocalDate,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean
)
