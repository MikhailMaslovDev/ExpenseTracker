package com.familyexpensetracker.prototype.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val isSystem: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
)
