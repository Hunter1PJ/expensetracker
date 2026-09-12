package com.suguru.expensetracker.data.repository

import com.suguru.expensetracker.data.local.dao.AccountDao
import com.suguru.expensetracker.data.mapper.toDomain
import com.suguru.expensetracker.data.mapper.toEntity
import com.suguru.expensetracker.domain.model.Account
import com.suguru.expensetracker.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomAccountRepository(
    private val accountDao: AccountDao
) : AccountRepository {

    override fun observeActiveAccounts(): Flow<List<Account>> {
        return accountDao.observeActiveAccounts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAllAccounts(): Flow<List<Account>> {
        return accountDao.observeAllAccounts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun observeAccountById(id: Long): Flow<Account?> {
        return accountDao.observeAccountById(id).map { it?.toDomain() }
    }

    override suspend fun getAccountById(id: Long): Account? {
        return accountDao.getAccountById(id)?.toDomain()
    }

    override suspend fun insertAccount(account: Account): Long {
        return accountDao.insertAccount(account.toEntity())
    }

    override suspend fun updateAccount(account: Account) {
        accountDao.updateAccount(account.toEntity())
    }

    override suspend fun archiveAccount(id: Long) {
        accountDao.archiveAccount(id)
    }
}
