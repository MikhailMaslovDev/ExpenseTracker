package com.familyexpensetracker.prototype.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MerchantRuleDao {
    @Query("SELECT * FROM merchant_rules ORDER BY merchantPattern ASC")
    suspend fun getAllForBackup(): List<MerchantRuleEntity>

    @Query("SELECT category FROM merchant_rules WHERE merchantPattern = :merchantPattern LIMIT 1")
    suspend fun findCategory(merchantPattern: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRuleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<MerchantRuleEntity>)

    @Query("UPDATE merchant_rules SET category = :newCategory, updatedAt = :updatedAt WHERE category = :oldCategory")
    suspend fun replaceCategory(oldCategory: String, newCategory: String, updatedAt: Long)

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAll()
}
