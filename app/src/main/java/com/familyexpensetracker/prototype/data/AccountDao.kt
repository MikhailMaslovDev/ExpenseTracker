package com.familyexpensetracker.prototype.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY accountHint ASC")
    suspend fun getAllForBackup(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>)

    @Query("UPDATE accounts SET name = :name, updatedAt = :updatedAt WHERE accountHint = :accountHint")
    suspend fun rename(accountHint: String, name: String, updatedAt: Long): Int

    @Query("DELETE FROM accounts")
    suspend fun deleteAll()
}
