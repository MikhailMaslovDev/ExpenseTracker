package com.familyexpensetracker.prototype.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val accountHint: String,
    val name: String,
    val bankName: String,
    val createdAt: Long,
    val updatedAt: Long,
)
