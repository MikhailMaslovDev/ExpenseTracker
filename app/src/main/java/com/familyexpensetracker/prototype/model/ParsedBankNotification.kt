package com.familyexpensetracker.prototype.model

data class ParsedBankNotification(
    val amount: String?,
    val currency: String?,
    val merchant: String?,
    val operationType: OperationType,
    val accountHint: String?,
    val transactionDate: String?,
    val transactionTimestamp: Long?,
    val availableBalance: String?,
    val availableBalanceCurrency: String?,
)

enum class OperationType {
    EXPENSE,
    INCOME,
    CASH_WITHDRAWAL,
    REFUND,
    TRANSFER,
    UNKNOWN,
}
