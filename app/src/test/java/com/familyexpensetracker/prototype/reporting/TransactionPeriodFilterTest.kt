package com.familyexpensetracker.prototype.reporting

import com.familyexpensetracker.prototype.categorization.ExpenseCategories
import com.familyexpensetracker.prototype.data.NotificationRecordEntity
import com.familyexpensetracker.prototype.model.OperationType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionPeriodFilterTest {
    private val filter = TransactionPeriodFilter(ZoneId.of("UTC"))

    @Test
    fun filtersRecordsBySelectedMonth() {
        val juneRecord = record("2026-06-02T10:00:00Z")
        val mayRecord = record("2026-05-31T10:00:00Z")

        assertEquals(listOf(juneRecord), filter.filterByMonth(listOf(juneRecord, mayRecord), YearMonth.of(2026, 6)))
    }

    @Test
    fun selectedTransactionDayOverridesHiddenSortTimestamp() {
        val manuallyAddedLater = record("2026-06-02T10:00:00Z").copy(transactionDay = "2026-05-31")

        assertEquals(
            listOf(manuallyAddedLater),
            filter.filterByMonth(listOf(manuallyAddedLater), YearMonth.of(2026, 5)),
        )
    }

    @Test
    fun customRangeIncludesBothBoundaryDates() {
        val before = record("2026-05-31T10:00:00Z")
        val from = record("2026-06-01T10:00:00Z")
        val to = record("2026-06-10T10:00:00Z")
        val after = record("2026-06-11T10:00:00Z")

        assertEquals(
            listOf(from, to),
            filter.filterByRange(
                listOf(before, from, to, after),
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 6, 10),
            ),
        )
    }

    private fun record(timestamp: String): NotificationRecordEntity {
        val epochMillis = Instant.parse(timestamp).toEpochMilli()
        return NotificationRecordEntity(
            fingerprint = timestamp,
            packageName = "",
            source = "MANUAL",
            title = null,
            rawText = "",
            postedAt = epochMillis,
            receivedAt = epochMillis,
            parseStatus = "MANUAL",
            amount = "10.00",
            currency = "RSD",
            merchant = null,
            transactionName = "Test",
            operationType = OperationType.EXPENSE.name,
            accountHint = null,
            transactionDate = null,
            transactionDay = timestamp.substring(0, 10),
            transactionTimestamp = epochMillis,
            availableBalance = null,
            availableBalanceCurrency = null,
            category = ExpenseCategories.OTHER,
            isUserEdited = true,
            isDeleted = false,
            updatedAt = epochMillis,
        )
    }
}
