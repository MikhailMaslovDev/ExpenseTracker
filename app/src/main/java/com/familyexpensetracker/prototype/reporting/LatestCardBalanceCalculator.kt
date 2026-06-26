package com.familyexpensetracker.prototype.reporting

import com.familyexpensetracker.prototype.data.NotificationRecordEntity

data class LatestCardBalance(
    val accountHint: String,
    val accountName: String,
    val amount: String,
    val currency: String,
    val updatedAt: Long,
)

class LatestCardBalanceCalculator {
    fun calculate(
        records: List<NotificationRecordEntity>,
        accountNames: Map<String, String> = emptyMap(),
    ): List<LatestCardBalance> =
        records
            .mapNotNull { toCardBalance(it, accountNames) }
            .groupBy(LatestCardBalance::accountHint)
            .mapNotNull { (_, balances) -> balances.maxByOrNull(LatestCardBalance::updatedAt) }
            .sortedBy(LatestCardBalance::accountName)

    private fun toCardBalance(
        record: NotificationRecordEntity,
        accountNames: Map<String, String>,
    ): LatestCardBalance? {
        val accountHint = record.accountHint?.takeIf(String::isNotBlank) ?: return null
        val amount = record.availableBalance?.takeIf(String::isNotBlank) ?: return null
        val currency = record.availableBalanceCurrency?.takeIf(String::isNotBlank) ?: return null
        return LatestCardBalance(
            accountHint = accountHint,
            accountName = accountNames[accountHint] ?: "Card ****$accountHint",
            amount = amount,
            currency = currency,
            updatedAt = record.transactionTimestamp ?: record.postedAt,
        )
    }
}
