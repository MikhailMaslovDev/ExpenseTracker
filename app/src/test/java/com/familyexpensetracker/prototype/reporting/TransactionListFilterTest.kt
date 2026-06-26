package com.familyexpensetracker.prototype.reporting

import com.familyexpensetracker.prototype.data.NotificationRecordEntity
import com.familyexpensetracker.prototype.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionListFilterTest {
    private val filter = TransactionListFilter()

    @Test
    fun searchesAcrossNameMerchantAmountCurrencyAndCard() {
        val gomex = record(
            name = "Gomex",
            merchant = "GOMEX DOO EVROPA NOVI SAD RS",
            amount = "125.50",
            currency = "RSD",
            card = "2255",
            category = "Groceries",
        )
        val benu = record(
            name = "Benu",
            merchant = "BENU APOTEKA",
            amount = "30.00",
            currency = "EUR",
            card = "4044",
            category = "Pharmacy",
        )

        assertEquals(listOf(gomex), filter.apply(listOf(gomex, benu), TransactionFilterCriteria("gomex")))
        assertEquals(listOf(gomex), filter.apply(listOf(gomex, benu), TransactionFilterCriteria("125.5")))
        assertEquals(listOf(benu), filter.apply(listOf(gomex, benu), TransactionFilterCriteria("eur")))
        assertEquals(listOf(benu), filter.apply(listOf(gomex, benu), TransactionFilterCriteria("4044")))
    }

    @Test
    fun combinesQueryAndExactCategory() {
        val groceryGomex = record("Gomex", "GOMEX", category = "Groceries")
        val pharmacyGomex = record("Gomex", "GOMEX SPECIAL", category = "Pharmacy")

        assertEquals(
            listOf(pharmacyGomex),
            filter.apply(
                listOf(groceryGomex, pharmacyGomex),
                TransactionFilterCriteria(query = "gomex", category = "Pharmacy"),
            ),
        )
    }

    private fun record(
        name: String,
        merchant: String,
        amount: String = "10.00",
        currency: String = "RSD",
        card: String = "2255",
        category: String,
    ): NotificationRecordEntity =
        NotificationRecordEntity(
            fingerprint = "$name:$merchant:$category",
            packageName = "",
            source = "NOTIFICATION",
            title = null,
            rawText = "",
            postedAt = 0,
            receivedAt = 0,
            parseStatus = "PARSED",
            amount = amount,
            currency = currency,
            merchant = merchant,
            transactionName = name,
            operationType = OperationType.EXPENSE.name,
            accountHint = card,
            transactionDate = null,
            transactionDay = "2026-06-01",
            transactionTimestamp = 0,
            availableBalance = null,
            availableBalanceCurrency = null,
            category = category,
            isUserEdited = false,
            isDeleted = false,
            updatedAt = 0,
        )
}
