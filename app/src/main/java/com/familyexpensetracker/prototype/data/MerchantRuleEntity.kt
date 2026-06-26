package com.familyexpensetracker.prototype.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val merchantPattern: String,
    val category: String,
    val updatedAt: Long,
)
