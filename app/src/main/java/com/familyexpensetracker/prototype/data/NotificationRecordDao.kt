package com.familyexpensetracker.prototype.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationRecordDao {
    @Query(
        """
        SELECT * FROM notification_records
        WHERE isDeleted = 0
        ORDER BY COALESCE(transactionDay, '') DESC, COALESCE(transactionTimestamp, postedAt) DESC, receivedAt DESC
        """,
    )
    fun observeAll(): Flow<List<NotificationRecordEntity>>

    @Query("SELECT * FROM notification_records ORDER BY id ASC")
    suspend fun getAllForBackup(): List<NotificationRecordEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: NotificationRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<NotificationRecordEntity>)

    @Query("SELECT * FROM notification_records WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): NotificationRecordEntity?

    @Query(
        """
        UPDATE notification_records
        SET isDeleted = 0,
            receivedAt = :receivedAt,
            updatedAt = :updatedAt
        WHERE fingerprint = :fingerprint AND isDeleted = 1
        """,
    )
    suspend fun restoreDeleted(fingerprint: String, receivedAt: Long, updatedAt: Long): Int

    @Query(
        """
        UPDATE notification_records
        SET amount = :amount,
            currency = :currency,
            transactionName = :transactionName,
            operationType = :operationType,
            accountHint = :accountHint,
            transactionDate = :transactionDate,
            transactionDay = :transactionDay,
            transactionTimestamp = :transactionTimestamp,
            category = :category,
            isUserEdited = 1,
            updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun updateTransaction(
        id: Long,
        amount: String?,
        currency: String?,
        transactionName: String?,
        operationType: String,
        accountHint: String?,
        transactionDate: String?,
        transactionDay: String?,
        transactionTimestamp: Long?,
        category: String?,
        updatedAt: Long,
    )

    @Query("UPDATE notification_records SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long)

    @Query("UPDATE notification_records SET category = :newCategory, updatedAt = :updatedAt WHERE category = :oldCategory")
    suspend fun replaceCategory(oldCategory: String, newCategory: String, updatedAt: Long)

    @Query("DELETE FROM notification_records")
    suspend fun deleteAll()
}
