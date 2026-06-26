package com.familyexpensetracker.prototype.parser

import com.familyexpensetracker.prototype.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RaiffeisenNotificationParserTest {
    private val parser = RaiffeisenNotificationParser()

    @Test
    fun parsesSerbianCardPurchaseWithCommaDecimals() {
        val result = parser.parse(
            "Kupovina karticom ****1234 kod MAXI BEOGRAD iznos 1.234,56 RSD datum 02.06.2026 09:15",
        )

        assertEquals("1234.56", result?.amount)
        assertEquals("RSD", result?.currency)
        assertEquals("MAXI BEOGRAD", result?.merchant)
        assertEquals(OperationType.EXPENSE, result?.operationType)
        assertEquals("1234", result?.accountHint)
        assertEquals("02.06.2026 09:15", result?.transactionDate)
    }

    @Test
    fun parsesDinAsRsdAndCashWithdrawal() {
        val result = parser.parse("Podizanje na bankomatu kartica x9876 iznos 5 000 DIN")

        assertEquals("5000", result?.amount)
        assertEquals("RSD", result?.currency)
        assertEquals(OperationType.CASH_WITHDRAWAL, result?.operationType)
        assertEquals("9876", result?.accountHint)
    }

    @Test
    fun parsesEuroRefundWithDotDecimals() {
        val result = parser.parse("Refund merchant: HOTEL TEST amount 42.50 EUR card **4321")

        assertEquals("42.50", result?.amount)
        assertEquals("EUR", result?.currency)
        assertEquals("HOTEL TEST", result?.merchant)
        assertEquals(OperationType.REFUND, result?.operationType)
        assertEquals("4321", result?.accountHint)
    }

    @Test
    fun ignoresNotificationWithoutAmount() {
        assertNull(parser.parse("Dobrodosli u aplikaciju Moja mBanka"))
    }

    @Test
    fun parsesRealCardUsageWithLastFourDigitsAndAvailableBalance() {
        val result = parser.parse(
            "Koriscenje kartice 4054****2255 Datum: 31.05.2026 21:49 " +
                "Iznos: 274,98 RSD Raspolozivo: 6.121,40 RSD Mesto: UNIVERS Novi Sad RS",
        )

        assertEquals("274.98", result?.amount)
        assertEquals(OperationType.EXPENSE, result?.operationType)
        assertEquals("2255", result?.accountHint)
        assertEquals("6121.40", result?.availableBalance)
        assertEquals("RSD", result?.availableBalanceCurrency)
        assertNotNull(result?.transactionTimestamp)
    }
}
