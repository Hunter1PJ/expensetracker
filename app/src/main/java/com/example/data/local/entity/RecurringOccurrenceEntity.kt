package com.example.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.RecurringOccurrenceStatus
import java.time.Instant
import java.time.LocalDate

/**
 * Room Entity tracking processed recurring occurrences for idempotency.
 * Prevents duplicate transaction generation even if generated transactions are deleted.
 */
@Entity(
    tableName = "recurring_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = RecurringTransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["rule_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["rule_id", "occurrence_date"], unique = true),
        Index(value = ["generated_transaction_id"])
    ]
)
data class RecurringOccurrenceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "rule_id")
    val ruleId: Long,
    @ColumnInfo(name = "occurrence_date")
    val occurrenceDate: LocalDate,
    @ColumnInfo(name = "generated_transaction_id")
    val generatedTransactionId: Long?,
    val status: RecurringOccurrenceStatus,
    @ColumnInfo(name = "processed_at")
    val processedAt: Instant = Instant.now()
)
