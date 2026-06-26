package com.familyexpensetracker.prototype.parser

import com.familyexpensetracker.prototype.model.OperationType
import com.familyexpensetracker.prototype.model.ParsedBankNotification
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

class RaiffeisenNotificationParser : BankNotificationParser {
    override fun parse(rawText: String): ParsedBankNotification? {
        val normalizedText = rawText.replace('\n', ' ').replace(WHITESPACE, " ").trim()
        val amountMatch = AMOUNT.find(normalizedText) ?: return null
        val amount = normalizeAmount(amountMatch.groupValues[1]) ?: return null
        val balanceMatch = AVAILABLE_BALANCE.find(normalizedText)
        val transactionDate = DATE.find(normalizedText)?.value

        return ParsedBankNotification(
            amount = amount.toPlainString(),
            currency = normalizeCurrency(amountMatch.groupValues[2]),
            merchant = extractMerchant(normalizedText),
            operationType = detectOperationType(normalizedText),
            accountHint = extractCardHint(normalizedText),
            transactionDate = transactionDate,
            transactionTimestamp = transactionDate?.let(::parseTransactionTimestamp),
            availableBalance = balanceMatch
                ?.groupValues
                ?.get(1)
                ?.let(::normalizeAmount)
                ?.toPlainString(),
            availableBalanceCurrency = balanceMatch
                ?.groupValues
                ?.get(2)
                ?.let(::normalizeCurrency),
        )
    }

    private fun normalizeAmount(value: String): BigDecimal? {
        val compact = value.replace(" ", "")
        val lastComma = compact.lastIndexOf(',')
        val lastDot = compact.lastIndexOf('.')
        val decimalSeparator = when {
            lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) ',' else '.'
            lastComma >= 0 -> if (compact.length - lastComma - 1 in 1..2) ',' else null
            lastDot >= 0 -> if (compact.length - lastDot - 1 in 1..2) '.' else null
            else -> null
        }
        val normalized = buildString {
            compact.forEach { char ->
                when {
                    char.isDigit() || char == '-' || char == '+' -> append(char)
                    char == decimalSeparator -> append('.')
                }
            }
        }
        return normalized.toBigDecimalOrNull()
    }

    private fun normalizeCurrency(value: String): String =
        when (value.uppercase()) {
            "DIN" -> "RSD"
            else -> value.uppercase()
        }

    private fun extractMerchant(text: String): String? =
        MERCHANT.find(text)
            ?.groupValues
            ?.get(1)
            ?.trim(' ', '.', ',', ';', ':', '-')
            ?.takeIf { it.isNotBlank() }

    private fun extractCardHint(text: String): String? =
        CARD_FRAGMENT.find(text)
            ?.groupValues
            ?.get(1)
            ?.let { fragment -> FOUR_DIGITS.findAll(fragment).lastOrNull()?.value }

    internal fun parseTransactionTimestamp(value: String): Long? {
        for (formatter in DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            } catch (_: DateTimeParseException) {
                // Try the next supported notification date format.
            }
        }
        return null
    }

    private fun detectOperationType(text: String): OperationType {
        val lowercase = text.lowercase()
        return when {
            REFUND_KEYWORDS.any(lowercase::contains) -> OperationType.REFUND
            CASH_WITHDRAWAL_KEYWORDS.any(lowercase::contains) -> OperationType.CASH_WITHDRAWAL
            TRANSFER_KEYWORDS.any(lowercase::contains) -> OperationType.TRANSFER
            INCOME_KEYWORDS.any(lowercase::contains) -> OperationType.INCOME
            EXPENSE_KEYWORDS.any(lowercase::contains) -> OperationType.EXPENSE
            "koriscenje kartice" in lowercase || "korišćenje kartice" in lowercase -> OperationType.EXPENSE
            else -> OperationType.UNKNOWN
        }
    }

    private companion object {
        val WHITESPACE = Regex("""\s+""")
        val AMOUNT = Regex(
            """(?i)([+-]?\d{1,3}(?:[.\s]\d{3})*(?:,\d{1,2})?|[+-]?\d+(?:[.,]\d{1,2})?)\s*(RSD|DIN|EUR)\b""",
        )
        val AVAILABLE_BALANCE = Regex(
            """(?i)\b(?:raspoloziv[oa]?|available(?:\s+balance)?|stanje)\s*[:=-]?\s*([+-]?\d{1,3}(?:[.\s]\d{3})*(?:,\d{1,2})?|[+-]?\d+(?:[.,]\d{1,2})?)\s*(RSD|DIN|EUR)\b""",
        )
        val CARD_FRAGMENT = Regex(
            """(?i)\b(?:kartic(?:a|e|u|om)?|card)\b(.+?)(?=\s+(?:datum|date|iznos|amount|raspoloziv|available|stanje|kod|at|merchant)\b|$)""",
        )
        val FOUR_DIGITS = Regex("""\d{4}""")
        val DATE = Regex("""\b\d{1,2}[./-]\d{1,2}[./-]\d{2,4}(?:\s+\d{1,2}:\d{2})?\b""")
        val MERCHANT = Regex(
            """(?i)\b(?:merchant|trgovac|prodajno\s+mesto|mesto|place|kod|at)\s*[:=-]?\s*(.+?)(?=\s+(?:iznos|amount|kartic|card|datum|date|raspoloziv|available|stanje)\b|$)""",
        )
        val DATE_TIME_FORMATTERS = listOf(
            DateTimeFormatter.ofPattern("d.M.yyyy H:mm"),
            DateTimeFormatter.ofPattern("d/M/yyyy H:mm"),
            DateTimeFormatter.ofPattern("d-M-yyyy H:mm"),
        )

        val REFUND_KEYWORDS = listOf("refund", "povrac", "povrać", "storno")
        val CASH_WITHDRAWAL_KEYWORDS = listOf("atm", "bankomat", "podizanje", "podignut")
        val TRANSFER_KEYWORDS = listOf("transfer", "prenos", "uplata")
        val INCOME_KEYWORDS = listOf("priliv", "odobren", "income")
        val EXPENSE_KEYWORDS = listOf("placanje", "plaćanje", "kupovina", "purchase", "potrosnja", "potrošnja")
    }
}
