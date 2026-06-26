package com.familyexpensetracker.prototype.reporting

import com.familyexpensetracker.prototype.data.NotificationRecordEntity

data class TransactionFilterCriteria(
    val query: String = "",
    val category: String? = null,
) {
    val isActive: Boolean
        get() = query.isNotBlank() || category != null
}

class TransactionListFilter {
    fun apply(
        records: List<NotificationRecordEntity>,
        criteria: TransactionFilterCriteria,
    ): List<NotificationRecordEntity> {
        val query = criteria.query.trim()
        return records.filter { record ->
            matchesCategory(record, criteria.category) && matchesQuery(record, query)
        }
    }

    private fun matchesCategory(record: NotificationRecordEntity, category: String?): Boolean =
        category == null || record.category == category

    private fun matchesQuery(record: NotificationRecordEntity, query: String): Boolean {
        if (query.isBlank()) return true
        return listOf(
            record.transactionName,
            record.merchant,
            record.amount,
            record.currency,
            record.accountHint,
        ).any { value -> value?.contains(query, ignoreCase = true) == true }
    }
}
