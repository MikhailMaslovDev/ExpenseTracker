package com.familyexpensetracker.prototype.reporting

import com.familyexpensetracker.prototype.data.NotificationRecordEntity
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class TransactionPeriodFilter(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun filterByMonth(
        records: List<NotificationRecordEntity>,
        month: YearMonth,
    ): List<NotificationRecordEntity> {
        val from = month.atDay(1)
        return filterByRange(records, from, month.atEndOfMonth())
    }

    fun filterByRange(
        records: List<NotificationRecordEntity>,
        from: LocalDate,
        to: LocalDate,
    ): List<NotificationRecordEntity> =
        records.filter { transactionDay(it) in from..to }

    fun transactionMonth(record: NotificationRecordEntity): YearMonth =
        YearMonth.from(transactionDay(record))

    fun transactionDay(record: NotificationRecordEntity): LocalDate =
        record.transactionDay
            ?.let { day -> runCatching { LocalDate.parse(day) }.getOrNull() }
            ?: Instant.ofEpochMilli(record.transactionTimestamp ?: record.postedAt)
                .atZone(zoneId)
                .toLocalDate()
}
