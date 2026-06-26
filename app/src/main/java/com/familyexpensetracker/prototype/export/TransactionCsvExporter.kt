package com.familyexpensetracker.prototype.export

import com.familyexpensetracker.prototype.data.NotificationRecordEntity

class TransactionCsvExporter {
    fun export(
        records: List<NotificationRecordEntity>,
        accountNames: Map<String, String>,
    ): String {
        val rows = buildList {
            add(HEADER)
            records.forEach { record ->
                add(
                    listOf(
                        record.transactionDay.orEmpty(),
                        record.amount.orEmpty(),
                        record.currency.orEmpty(),
                        record.transactionName.orEmpty(),
                        record.merchant.orEmpty(),
                        record.category.orEmpty(),
                        record.operationType,
                        record.source,
                        record.accountHint?.let(accountNames::get).orEmpty(),
                        record.accountHint.orEmpty(),
                        record.availableBalance.orEmpty(),
                        record.availableBalanceCurrency.orEmpty(),
                    ),
                )
            }
        }
        return UTF8_BOM + rows.joinToString("\r\n") { row -> row.joinToString(",") { escape(it) } }
    }

    private fun escape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return if (escaped.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) {
            "\"$escaped\""
        } else {
            escaped
        }
    }

    private companion object {
        const val UTF8_BOM = "\uFEFF"
        val HEADER = listOf(
            "Date",
            "Amount",
            "Currency",
            "Name",
            "Merchant",
            "Category",
            "Operation",
            "Source",
            "Account",
            "Card",
            "Available balance",
            "Balance currency",
        )
    }
}
