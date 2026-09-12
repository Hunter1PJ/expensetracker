package com.suguru.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.suguru.expensetracker.data.local.entity.RecurringOccurrenceEntity
import com.suguru.expensetracker.domain.model.RecurringOccurrenceStatus
import java.time.LocalDate

@Dao
interface RecurringOccurrenceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOccurrence(occurrence: RecurringOccurrenceEntity): Long

    @Query("SELECT * FROM recurring_occurrences WHERE rule_id = :ruleId AND occurrence_date = :occurrenceDate LIMIT 1")
    suspend fun getOccurrence(ruleId: Long, occurrenceDate: LocalDate): RecurringOccurrenceEntity?

    @Query("SELECT * FROM recurring_occurrences WHERE rule_id = :ruleId")
    suspend fun getOccurrencesForRule(ruleId: Long): List<RecurringOccurrenceEntity>

    @Update
    suspend fun updateOccurrence(occurrence: RecurringOccurrenceEntity)

    @Query("UPDATE recurring_occurrences SET status = :status, generated_transaction_id = NULL WHERE rule_id = :ruleId AND occurrence_date = :occurrenceDate")
    suspend fun markTransactionDeleted(ruleId: Long, occurrenceDate: LocalDate, status: RecurringOccurrenceStatus = RecurringOccurrenceStatus.TRANSACTION_DELETED)
}
