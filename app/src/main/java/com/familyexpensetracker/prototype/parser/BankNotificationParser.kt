package com.familyexpensetracker.prototype.parser

import com.familyexpensetracker.prototype.model.ParsedBankNotification

interface BankNotificationParser {
    fun parse(rawText: String): ParsedBankNotification?
}

