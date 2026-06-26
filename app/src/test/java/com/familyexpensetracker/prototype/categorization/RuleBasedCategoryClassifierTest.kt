package com.familyexpensetracker.prototype.categorization

import com.familyexpensetracker.prototype.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleBasedCategoryClassifierTest {
    private val classifier = RuleBasedCategoryClassifier()

    @Test
    fun suggestsKnownMerchantCategories() {
        assertEquals(ExpenseCategories.GROCERIES, classifier.suggest("MAXI Novi Sad", OperationType.EXPENSE))
        assertEquals(ExpenseCategories.GROCERIES, classifier.suggest("GOMEX DOO EVROPA NOVI SAD RS", OperationType.EXPENSE))
        assertEquals(ExpenseCategories.GROCERIES, classifier.suggest("GOMEX DOO BELGRADE RS", OperationType.EXPENSE))
        assertEquals(ExpenseCategories.RESTAURANTS, classifier.suggest("WOLT BELGRADE", OperationType.EXPENSE))
        assertEquals(ExpenseCategories.MOBILE_AND_INTERNET, classifier.suggest("YETTEL", OperationType.EXPENSE))
    }

    @Test
    fun normalizesRecognizedMerchantToFriendlyName() {
        assertEquals("Gomex", classifier.normalizeTransactionName("GOMEX DOO EVROPA NOVI SAD RS"))
        assertEquals("Gomex", classifier.normalizeTransactionName("SHOP GOMEX BELGRADE"))
        assertEquals("My custom expense", classifier.normalizeTransactionName("My custom expense"))
    }

    @Test
    fun suggestsOperationSpecificCategories() {
        assertEquals(ExpenseCategories.CASH_WITHDRAWAL, classifier.suggest(null, OperationType.CASH_WITHDRAWAL))
        assertEquals(ExpenseCategories.TRANSFERS, classifier.suggest(null, OperationType.TRANSFER))
    }

    @Test
    fun fallsBackToOtherForUnknownMerchant() {
        assertEquals(ExpenseCategories.OTHER, classifier.suggest("UNKNOWN SHOP", OperationType.EXPENSE))
        assertEquals(ExpenseCategories.GROCERIES, classifier.suggest("SHOP GOMEX", OperationType.EXPENSE))
    }
}
