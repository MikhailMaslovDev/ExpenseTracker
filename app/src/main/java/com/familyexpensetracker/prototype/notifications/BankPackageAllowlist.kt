package com.familyexpensetracker.prototype.notifications

object BankPackageAllowlist {
    const val RAIFFEISEN_SERBIA = "rs.Raiffeisen.mobile"

    fun contains(packageName: String): Boolean = packageName == RAIFFEISEN_SERBIA
}

