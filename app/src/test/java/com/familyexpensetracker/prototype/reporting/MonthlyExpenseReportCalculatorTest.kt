package com.familyexpensetracker.prototype.reporting

import com.familyexpensetracker.prototype.categorization.ExpenseCategories
import com.familyexpensetracker.prototype.data.NotificationRecordEntity
import com.familyexpensetracker.prototype.model.OperationType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyExpenseReportCalculatorTest {
    private val calculator = MonthlyExpenseReportCalculator(ZoneId.of("UTC"))

    @Test
    fun calculatesCurrentMonthExpensesByCategoryAndCurrency() {
        val records = listOf(
            record("2026-06-02T10:00:00Z", "100.50", ExpenseCategories.GROCERIES),
            record("2026-06-03T10:00:00Z", "25.00", ExpenseCategories.GROCERIES),
            record("2026-06-04T10:00:00Z", "75.00", ExpenseCategories.CAR),
            record("2026-06-04T10:00:00Z", "900.00", ExpenseCategories.OTHER, OperationType.INCOME),
            record("2026-05-31T10:00:00Z", "500.00", ExpenseCategories.OTHER),
        )

        val report = calculator.calculate(records, YearMonth.of(2026, 6))

        assertEquals(LocalDate.of(2026, 6, 1), report.from)
        assertEquals(LocalDate.of(2026, 6, 30), report.to)
        assertEquals(BigDecimal("200.50"), report.currencyTotals.single().amount)
        assertEquals(
            listOf(
                CategoryExpenseTotal(ExpenseCategories.GROCERIES, "RSD", BigDecimal("125.50")),
                CategoryExpenseTotal(ExpenseCategories.CAR, "RSD", BigDecimal("75.00")),
            ),
            report.categoryTotals,
        )
        assertEquals(2, report.merchantTotals.size)
    }

    @Test
    fun usesSelectedTransactionDayInsteadOfHiddenSortTimestamp() {
        val record = record("2026-06-01T08:00:00Z", "50.00", ExpenseCategories.GROCERIES)
            .copy(transactionDay = "2026-05-31")

        val report = calculator.calculate(listOf(record), YearMonth.of(2026, 5))

        assertEquals(BigDecimal("50.00"), report.currencyTotals.single().amount)
    }

    @Test
    fun groupsTopMerchantsByNormalizedTransactionName() {
        val records = listOf(
            record(
                timestamp = "2026-06-02T10:00:00Z",
                amount = "100.50",
                category = ExpenseCategories.GROCERIES,
                transactionName = "Gomex",
                merchant = "GOMEX DOO EVROPA NOVI SAD RS",
            ),
            record(
                timestamp = "2026-06-03T10:00:00Z",
                amount = "25.00",
                category = ExpenseCategories.GROCERIES,
                transactionName = "Gomex",
                merchant = "GOMEX DOO BELGRADE RS",
            ),
            record(
                timestamp = "2026-06-04T10:00:00Z",
                amount = "75.00",
                category = ExpenseCategories.CAR,
                transactionName = "NIS",
            ),
        )

        val report = calculator.calculate(records, YearMonth.of(2026, 6))

        assertEquals(
            listOf(
                MerchantExpenseTotal("Gomex", "RSD", BigDecimal("125.50")),
                MerchantExpenseTotal("NIS", "RSD", BigDecimal("75.00")),
            ),
            report.merchantTotals,
        )
    }

    private fun record(
        timestamp: String,
        amount: String,
        category: String,
        operationType: OperationType = OperationType.EXPENSE,
        transactionName: String = category,
        merchant: String? = null,
    ): NotificationRecordEntity {
        val epochMillis = Instant.parse(timestamp).toEpochMilli()
        return NotificationRecordEntity(
            fingerprint = timestamp + amount,
            packageName = "",
            source = "MANUAL",
            title = null,
            rawText = "",
            postedAt = epochMillis,
            receivedAt = epochMillis,
            parseStatus = "MANUAL",
            amount = amount,
            currency = "RSD",
            merchant = merchant,
            transactionName = transactionName,
            operationType = operationType.name,
            accountHint = null,
            transactionDate = null,
            transactionDay = timestamp.substring(0, 10),
            transactionTimestamp = epochMillis,
            availableBalance = null,
            availableBalanceCurrency = null,
            category = category,
            isUserEdited = true,
            isDeleted = false,
            updatedAt = epochMillis,
        )
    }
}
