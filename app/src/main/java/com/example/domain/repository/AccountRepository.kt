package com.example.domain.repository

import com.example.domain.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository interface for managing accounts.
 * Purely decoupled from persistence and framework dependencies.
 */
interface AccountRepository {
    fun observeActiveAccounts(): Flow<List<Account>>
    fun observeAllAccounts(): Flow<List<Account>>
    fun observeAccountById(id: Long): Flow<Account?>
    suspend fun getAccountById(id: Long): Account?
    suspend fun insertAccount(account: Account): Long
    suspend fun updateAccount(account: Account)
    suspend fun archiveAccount(id: Long)
}
