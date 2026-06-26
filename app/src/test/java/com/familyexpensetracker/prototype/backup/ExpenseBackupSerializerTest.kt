package com.familyexpensetracker.prototype.backup

import com.familyexpensetracker.prototype.data.AccountEntity
import com.familyexpensetracker.prototype.data.CategoryEntity
import com.familyexpensetracker.prototype.data.MerchantRuleEntity
import com.familyexpensetracker.prototype.data.NotificationRecordEntity
import com.familyexpensetracker.prototype.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpenseBackupSerializerTest {
    private val serializer = ExpenseBackupSerializer()

    @Test
    fun roundTripsLocalDataWithoutRawPushText() {
        val data = ExpenseBackupData(
            transactions = listOf(transaction()),
            userCategories = listOf(CategoryEntity("Coffee", false, Int.MAX_VALUE, 10)),
            merchantRules = listOf(MerchantRuleEntity("TEST SHOP", "Coffee", 20)),
            accounts = listOf(AccountEntity("2255", "My card", "Raiffeisen Serbia", 30, 40)),
        )

        val encoded = serializer.encode(data)
        val decoded = serializer.decode(encoded)

        assertFalse(encoded.contains("sensitive raw push"))
        assertEquals("", decoded.transactions.single().rawText)
        assertEquals(data.userCategories, decoded.userCategories)
        assertEquals(data.merchantRules, decoded.merchantRules)
        assertEquals(data.accounts, decoded.accounts)
        assertEquals(
            data.transactions.single().copy(rawText = ""),
            decoded.transactions.single(),
        )
    }

    @Test
    fun rejectsUnsupportedVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            serializer.decode("""{"version":99,"transactions":[],"userCategories":[],"merchantRules":[],"accounts":[]}""")
        }
    }

    private fun transaction() = NotificationRecordEntity(
        id = 7,
        fingerprint = "fingerprint",
        packageName = "rs.Raiffeisen.mobile",
        source = "NOTIFICATION",
        title = "Purchase",
        rawText = "sensitive raw push",
        postedAt = 1,
        receivedAt = 2,
        parseStatus = "PARSED",
        amount = "10.50",
        currency = "RSD",
        merchant = "TEST SHOP",
        transactionName = "Test",
        operationType = OperationType.EXPENSE.name,
        accountHint = "2255",
        transactionDate = "01.06.2026 10:00",
        transactionDay = "2026-06-01",
        transactionTimestamp = 3,
        availableBalance = "1000.00",
        availableBalanceCurrency = "RSD",
        category = "Coffee",
        isUserEdited = true,
        isDeleted = false,
        updatedAt = 4,
    )
}
