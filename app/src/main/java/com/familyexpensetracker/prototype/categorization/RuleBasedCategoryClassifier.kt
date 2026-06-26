package com.familyexpensetracker.prototype.categorization

import com.familyexpensetracker.prototype.model.OperationType

class RuleBasedCategoryClassifier {
    fun normalizeMerchant(merchant: String?): String =
        merchant.orEmpty().trim().uppercase()

    fun normalizeTransactionName(value: String?): String? {
        val trimmed = value.orEmpty().trim()
        if (trimmed.isBlank()) return null
        val uppercase = trimmed.uppercase()
        return NAME_RULES.firstOrNull { (matches, _) ->
            matches.any(uppercase::contains)
        }?.second ?: trimmed
    }

    fun suggest(transactionName: String?, operationType: OperationType): String {
        if (operationType == OperationType.CASH_WITHDRAWAL) return ExpenseCategories.CASH_WITHDRAWAL
        if (operationType == OperationType.TRANSFER) return ExpenseCategories.TRANSFERS

        val normalizedName = normalizeTransactionName(transactionName)
        return NAME_CATEGORIES[normalizedName] ?: ExpenseCategories.OTHER
    }

    fun suggestExpenseCategory(transactionName: String?): String =
        suggest(transactionName, OperationType.EXPENSE)

    companion object {
        val suggestedTransactionNames: List<String> = listOf(
            "Maxi",
            "Idea",
            "Gomex",
            "Wolt",
            "Glovo",
            "NIS",
            "Benu",
            "Yettel",
            "A1",
            "MTS",
        )

        private val NAME_RULES = listOf(
            listOf("MAXI") to "Maxi",
            listOf("IDEA") to "Idea",
            listOf("GOMEX") to "Gomex",
            listOf("WOLT") to "Wolt",
            listOf("GLOVO") to "Glovo",
            listOf("NIS") to "NIS",
            listOf("BENU") to "Benu",
            listOf("YETTEL") to "Yettel",
            listOf("A1") to "A1",
            listOf("MTS") to "MTS",
        )

        private val NAME_CATEGORIES = mapOf(
            "Maxi" to ExpenseCategories.GROCERIES,
            "Idea" to ExpenseCategories.GROCERIES,
            "Gomex" to ExpenseCategories.GROCERIES,
            "Wolt" to ExpenseCategories.RESTAURANTS,
            "Glovo" to ExpenseCategories.RESTAURANTS,
            "NIS" to ExpenseCategories.CAR,
            "Benu" to ExpenseCategories.PHARMACY,
            "Yettel" to ExpenseCategories.MOBILE_AND_INTERNET,
            "A1" to ExpenseCategories.MOBILE_AND_INTERNET,
            "MTS" to ExpenseCategories.MOBILE_AND_INTERNET,
        )
    }
}
