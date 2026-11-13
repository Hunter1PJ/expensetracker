package com.suguru.expensetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.suguru.expensetracker.data.local.entity.PendingSettingsRecoveryEntity

@Dao
interface PendingSettingsRecoveryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingSettingsRecoveryEntity)

    @Query("SELECT * FROM pending_settings_restore WHERE id = 1 LIMIT 1")
    suspend fun getPending(): PendingSettingsRecoveryEntity?

    @Query("DELETE FROM pending_settings_restore WHERE id = 1")
    suspend fun clear()
}
