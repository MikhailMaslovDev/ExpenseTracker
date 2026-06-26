package com.familyexpensetracker.prototype.data

import java.security.MessageDigest

object NotificationFingerprint {
    fun create(
        packageName: String,
        title: String?,
        rawText: String,
        postedAt: Long,
    ): String = sha256("$packageName|$postedAt|$title|$rawText")

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}