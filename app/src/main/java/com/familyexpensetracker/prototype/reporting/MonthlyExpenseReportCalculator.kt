package com.familyexpensetracker.prototype.reporting

import com.familyexpensetracker.prototype.categorization.ExpenseCategories
import com.familyexpensetracker.prototype.data.NotificationRecordEntity
import com.familyexpensetracker.prototype.model.OperationType
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class CategoryExpenseTotal(
    val category: String,
    val currency: String,
    val amount: BigDecimal,
)

data class CurrencyExpenseTotal(
    val currency: String,
    val amount: BigDecimal,
)

data class MerchantExpenseTotal(
    val name: String,
    val currency: String,
    val amount: BigDecimal,
)

data class MonthlyExpenseReport(
    val from: LocalDate,
    val to: LocalDate,
    val currencyTotals: List<CurrencyExpenseTotal>,
    val categoryTotals: List<CategoryExpenseTotal>,
    val merchantTotals: List<MerchantExpenseTotal>,
)

class MonthlyExpenseReportCalculator(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val periodFilter = TransactionPeriodFilter(zoneId)

    fun calculate(
        records: List<NotificationRecordEntity>,
        month: YearMonth = YearMonth.now(zoneId),
    ): MonthlyExpenseReport =
        calculate(records, month.atDay(1), month.atEndOfMonth())

    fun calculate(
        records: List<NotificationRecordEntity>,
        from: LocalDate,
        to: LocalDate,
    ): MonthlyExpenseReport {
        val expenses = periodFilter.filterByRange(records, from, to).mapNotNull { record ->
            if (record.operationType != OperationType.EXPENSE.name) return@mapNotNull null
            val amount = record.amount?.toBigDecimalOrNull() ?: return@mapNotNull null
            val currency = record.currency.orEmpty().ifBlank { "RSD" }
            ExpenseRow(
                category = record.category ?: ExpenseCategories.OTHER,
                currency = currency,
                amount = amount,
                merchantName = record.transactionName?.takeIf { it.isNotBlank() }
                    ?: record.merchant?.takeIf { it.isNotBlank() }
                    ?: "Unknown",
            )
        }

        val currencyTotals = expenses
            .groupBy { it.currency }
            .map { (currency, rows) ->
                CurrencyExpenseTotal(currency, rows.sumOf { it.amount })
            }
            .sortedBy { it.currency }

        val categoryTotals = expenses
            .groupBy { it.category to it.currency }
            .map { (key, rows) ->
                CategoryExpenseTotal(key.first, key.second, rows.sumOf { it.amount })
            }
            .sortedWith(compareByDescending<CategoryExpenseTotal> { it.amount }.thenBy { it.category })

        val merchantTotals = expenses
            .groupBy { it.merchantName to it.currency }
            .map { (key, rows) ->
                MerchantExpenseTotal(key.first, key.second, rows.sumOf { it.amount })
            }
            .sortedWith(compareByDescending<MerchantExpenseTotal> { it.amount }.thenBy { it.name })
            .take(TOP_MERCHANT_LIMIT)

        return MonthlyExpenseReport(from, to, currencyTotals, categoryTotals, merchantTotals)
    }

    private data class ExpenseRow(
        val category: String,
        val currency: String,
        val amount: BigDecimal,
        val merchantName: String,
    )

    private companion object {
        const val TOP_MERCHANT_LIMIT = 5
    }
}
