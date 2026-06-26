package com.familyexpensetracker.prototype.reporting

import com.familyexpensetracker.prototype.categorization.ExpenseCategories
import com.familyexpensetracker.prototype.data.NotificationRecordEntity
import com.familyexpensetracker.prototype.model.OperationType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class LatestCardBalanceCalculatorTest {
    private val calculator = LatestCardBalanceCalculator()

    @Test
    fun keepsLatestKnownBalanceForEachCard() {
        val records = listOf(
            record("2026-06-01T10:00:00Z", "2255", "6121.40"),
            record("2026-06-02T10:00:00Z", "4044", "1500.00"),
            record("2026-06-03T10:00:00Z", "2255", "5800.00"),
        )

        assertEquals(
            listOf(
                LatestCardBalance(
                    "2255",
                    "Card ****2255",
                    "5800.00",
                    "RSD",
                    Instant.parse("2026-06-03T10:00:00Z").toEpochMilli(),
                ),
                LatestCardBalance(
                    "4044",
                    "Card ****4044",
                    "1500.00",
                    "RSD",
                    Instant.parse("2026-06-02T10:00:00Z").toEpochMilli(),
                ),
            ),
            calculator.calculate(records),
        )
    }

    @Test
    fun usesFriendlyAccountName() {
        val record = record("2026-06-03T10:00:00Z", "2255", "5800.00")

        assertEquals(
            "My Raiffeisen",
            calculator.calculate(listOf(record), mapOf("2255" to "My Raiffeisen")).single().accountName,
        )
    }

    @Test
    fun ignoresRecordsWithoutCardOrBalance() {
        val records = listOf(
            record("2026-06-01T10:00:00Z", null, "6121.40"),
            record("2026-06-02T10:00:00Z", "2255", null),
        )

        assertEquals(emptyList<LatestCardBalance>(), calculator.calculate(records))
    }

    private fun record(
        timestamp: String,
        accountHint: String?,
        availableBalance: String?,
    ): NotificationRecordEntity {
        val epochMillis = Instant.parse(timestamp).toEpochMilli()
        return NotificationRecordEntity(
            fingerprint = timestamp + accountHint,
            packageName = "",
            source = "NOTIFICATION",
            title = null,
            rawText = "",
            postedAt = epochMillis,
            receivedAt = epochMillis,
            parseStatus = "PARSED",
            amount = "10.00",
            currency = "RSD",
            merchant = "Test merchant",
            transactionName = "Test",
            operationType = OperationType.EXPENSE.name,
            accountHint = accountHint,
            transactionDate = null,
            transactionDay = timestamp.substring(0, 10),
            transactionTimestamp = epochMillis,
            availableBalance = availableBalance,
            availableBalanceCurrency = "RSD",
            category = ExpenseCategories.OTHER,
            isUserEdited = false,
            isDeleted = false,
            updatedAt = epochMillis,
        )
    }
}
