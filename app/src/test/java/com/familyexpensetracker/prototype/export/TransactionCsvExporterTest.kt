package com.familyexpensetracker.prototype.export

import com.familyexpensetracker.prototype.data.NotificationRecordEntity
import com.familyexpensetracker.prototype.model.OperationType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionCsvExporterTest {
    private val exporter = TransactionCsvExporter()

    @Test
    fun exportsVisibleFieldsAndFriendlyAccountNameWithoutRawPush() {
        val csv = exporter.export(
            records = listOf(record()),
            accountNames = mapOf("2255" to "My Raiffeisen"),
        )

        assertTrue(csv.startsWith("\uFEFFDate,Amount,Currency"))
        assertTrue(csv.contains("My Raiffeisen,2255,6121.40,RSD"))
        assertFalse(csv.contains("sensitive raw push"))
    }

    @Test
    fun escapesCommasQuotesAndNewLines() {
        val csv = exporter.export(
            records = listOf(
                record().copy(
                    transactionName = "Shop, center",
                    merchant = "A \"quoted\"\nmerchant",
                ),
            ),
            accountNames = emptyMap(),
        )

        assertTrue(csv.contains("\"Shop, center\""))
        assertTrue(csv.contains("\"A \"\"quoted\"\"\nmerchant\""))
    }

    private fun record(): NotificationRecordEntity =
        NotificationRecordEntity(
            fingerprint = "test",
            packageName = "rs.Raiffeisen.mobile",
            source = "NOTIFICATION",
            title = null,
            rawText = "sensitive raw push",
            postedAt = 0,
            receivedAt = 0,
            parseStatus = "PARSED",
            amount = "10.50",
            currency = "RSD",
            merchant = "TEST SHOP",
            transactionName = "Test",
            operationType = OperationType.EXPENSE.name,
            accountHint = "2255",
            transactionDate = "01.06.2026 10:00",
            transactionDay = "2026-06-01",
            transactionTimestamp = 0,
            availableBalance = "6121.40",
            availableBalanceCurrency = "RSD",
            category = "Other",
            isUserEdited = false,
            isDeleted = false,
            updatedAt = 0,
        )
}
