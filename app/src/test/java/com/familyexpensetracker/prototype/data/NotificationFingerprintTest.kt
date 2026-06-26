package com.familyexpensetracker.prototype.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NotificationFingerprintTest {
    @Test
    fun samePushAlwaysProducesSameFingerprint() {
        val first = NotificationFingerprint.create(
            packageName = "rs.Raiffeisen.mobile",
            title = "Karticno placanje",
            rawText = "Kupovina karticom iznos 100 RSD",
            postedAt = 123456789L,
        )
        val second = NotificationFingerprint.create(
            packageName = "rs.Raiffeisen.mobile",
            title = "Karticno placanje",
            rawText = "Kupovina karticom iznos 100 RSD",
            postedAt = 123456789L,
        )

        assertEquals(first, second)
    }

    @Test
    fun distinctPostTimeProducesDistinctFingerprint() {
        val first = NotificationFingerprint.create("rs.Raiffeisen.mobile", null, "100 RSD", 100L)
        val second = NotificationFingerprint.create("rs.Raiffeisen.mobile", null, "100 RSD", 101L)

        assertNotEquals(first, second)
    }
}