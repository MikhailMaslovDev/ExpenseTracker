package com.familyexpensetracker.prototype.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_records",
    indices = [Index(value = ["fingerprint"], unique = true)],
)
data class NotificationRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fingerprint: String,
    val packageName: String,
    val source: String,
    val title: String?,
    val rawText: String,
    val postedAt: Long,
    val receivedAt: Long,
    val parseStatus: String,
    val amount: String?,
    val currency: String?,
    val merchant: String?,
    val transactionName: String?,
    val operationType: String,
    val accountHint: String?,
    val transactionDate: String?,
    val transactionDay: String?,
    val transactionTimestamp: Long?,
    val availableBalance: String?,
    val availableBalanceCurrency: String?,
    val category: String?,
    val isUserEdited: Boolean,
    val isDeleted: Boolean,
    val updatedAt: Long,
)
