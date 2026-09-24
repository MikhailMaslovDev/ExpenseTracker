package com.familyexpensetracker.prototype.data

import android.content.Context
import androidx.room.withTransaction
import com.familyexpensetracker.prototype.backup.ExpenseBackupData
import com.familyexpensetracker.prototype.categorization.ExpenseCategories
import com.familyexpensetracker.prototype.categorization.RuleBasedCategoryClassifier
import com.familyexpensetracker.prototype.model.OperationType
import com.familyexpensetracker.prototype.parser.RaiffeisenNotificationParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

enum class NotificationCaptureResult {
    ADDED,
    UPDATED,
    RESTORED,
    DUPLICATE,
}
class NotificationRepository private constructor(
    private val database: AppDatabase,
    private val dao: NotificationRecordDao,
    private val merchantRuleDao: MerchantRuleDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val parser: RaiffeisenNotificationParser = RaiffeisenNotificationParser(),
    private val categoryClassifier: RuleBasedCategoryClassifier = RuleBasedCategoryClassifier(),
) {
    val categories: Flow<List<CategoryEntity>> = categoryDao.observeAll()
    val accounts: Flow<List<AccountEntity>> = accountDao.observeAll()

    val records: Flow<List<NotificationRecordEntity>> = dao.observeAll()
        .map { records ->
            records.map(::withLatestParsing)
                .sortedWith(
                    compareByDescending<NotificationRecordEntity> { it.transactionDay.orEmpty() }
                        .thenByDescending(::sortTimestamp),
                )
        }

    private fun withLatestParsing(record: NotificationRecordEntity): NotificationRecordEntity {
        val transactionName = record.transactionName
            ?: categoryClassifier.normalizeTransactionName(record.merchant)
        val transactionDay = record.transactionDay ?: dayOf(record.transactionTimestamp ?: record.postedAt)
        if (record.isUserEdited) {
            return record.copy(transactionName = transactionName, transactionDay = transactionDay)
        }
        val parsed = parser.parse(record.rawText) ?: return record
        val parsedTransactionName = categoryClassifier.normalizeTransactionName(parsed.merchant)
        return record.copy(
            parseStatus = "PARSED",
            amount = parsed.amount,
            currency = parsed.currency,
            merchant = parsed.merchant,
            transactionName = parsedTransactionName,
            operationType = parsed.operationType.name,
            accountHint = parsed.accountHint,
            transactionDate = parsed.transactionDate,
            transactionDay = parsed.transactionTimestamp?.let(::dayOf) ?: transactionDay,
            transactionTimestamp = parsed.transactionTimestamp,
            availableBalance = parsed.availableBalance,
            availableBalanceCurrency = parsed.availableBalanceCurrency,
            category = if (record.category.isNullOrBlank() || record.category == "Other") {
                categoryClassifier.suggest(parsedTransactionName, parsed.operationType)
            } else {
                record.category
            },
        )
    }

    suspend fun capture(
        packageName: String,
        title: String?,
        rawText: String,
        postedAt: Long,
    ): NotificationCaptureResult {
        val parsed = parser.parse(rawText)
        val transactionName = categoryClassifier.normalizeTransactionName(parsed?.merchant)
        val category = parsed?.let { suggestCategory(it.merchant, transactionName, it.operationType) }
        val now = System.currentTimeMillis()
        val fingerprint = NotificationFingerprint.create(packageName, title, rawText, postedAt)
        if (
            dao.refreshCapturedNotification(
                fingerprint = fingerprint,
                receivedAt = now,
                updatedAt = now,
                parseStatus = if (parsed == null) "UNPARSED" else "PARSED",
                amount = parsed?.amount,
                currency = parsed?.currency,
                merchant = parsed?.merchant,
                transactionName = transactionName,
                operationType = parsed?.operationType?.name ?: OperationType.UNKNOWN.name,
                accountHint = parsed?.accountHint,
                transactionDate = parsed?.transactionDate,
                transactionDay = parsed?.transactionTimestamp?.let(::dayOf) ?: dayOf(postedAt),
                transactionTimestamp = parsed?.transactionTimestamp,
                availableBalance = parsed?.availableBalance,
                availableBalanceCurrency = parsed?.availableBalanceCurrency,
                category = category,
            ) > 0
        ) {
            ensureAccount(parsed?.accountHint, now)
            return NotificationCaptureResult.UPDATED
        }
        if (dao.restoreDeleted(fingerprint, receivedAt = now, updatedAt = now) > 0) {
            ensureAccount(parsed?.accountHint, now)
            return NotificationCaptureResult.RESTORED
        }
        val inserted = dao.insert(
            NotificationRecordEntity(
                fingerprint = fingerprint,
                packageName = packageName,
                source = SOURCE_NOTIFICATION,
                title = title,
                rawText = rawText,
                postedAt = postedAt,
                receivedAt = now,
                parseStatus = if (parsed == null) "UNPARSED" else "PARSED",
                amount = parsed?.amount,
                currency = parsed?.currency,
                merchant = parsed?.merchant,
                transactionName = transactionName,
                operationType = parsed?.operationType?.name ?: OperationType.UNKNOWN.name,
                accountHint = parsed?.accountHint,
                transactionDate = parsed?.transactionDate,
                transactionDay = parsed?.transactionTimestamp?.let(::dayOf) ?: dayOf(postedAt),
                transactionTimestamp = parsed?.transactionTimestamp,
                availableBalance = parsed?.availableBalance,
                availableBalanceCurrency = parsed?.availableBalanceCurrency,
                category = category,
                isUserEdited = false,
                isDeleted = false,
                updatedAt = now,
            ),
        )
        ensureAccount(parsed?.accountHint, now)
        return if (inserted > 0) NotificationCaptureResult.ADDED else NotificationCaptureResult.DUPLICATE
    }

    suspend fun updateTransaction(
        id: Long,
        amount: String,
        currency: String,
        transactionName: String,
        transactionDate: String,
        category: String,
    ) {
        val normalizedDay = normalizeDay(transactionDate)
        val existing = dao.findById(id) ?: return
        val parsed = parser.parse(existing.rawText)
        val normalizedTransactionName = categoryClassifier.normalizeTransactionName(transactionName)
        val now = System.currentTimeMillis()
        dao.updateTransaction(
            id = id,
            amount = amount.trim().ifBlank { null },
            currency = currency.trim().uppercase().ifBlank { null },
            transactionName = normalizedTransactionName,
            operationType = parsed?.operationType?.name ?: existing.operationType,
            accountHint = parsed?.accountHint ?: existing.accountHint,
            transactionDate = normalizedDay?.let(::displayDay),
            transactionDay = normalizedDay,
            transactionTimestamp = existing.transactionTimestamp ?: now,
            category = category,
            updatedAt = now,
        )
        saveMerchantRule(existing.merchant, category, now)
    }

    suspend fun createManualTransaction(
        amount: String,
        currency: String,
        transactionName: String,
        transactionDate: String,
        category: String,
    ) {
        val now = System.currentTimeMillis()
        val normalizedTransactionName = categoryClassifier.normalizeTransactionName(transactionName)
        val normalizedDay = normalizeDay(transactionDate) ?: dayOf(now)
        val transactionTimestamp = now
        val selectedCategory = if (category == "Other") {
            suggestCategory(null, normalizedTransactionName, OperationType.EXPENSE)
        } else {
            category
        }
        dao.insert(
            NotificationRecordEntity(
                fingerprint = "manual:${UUID.randomUUID()}",
                packageName = "",
                source = SOURCE_MANUAL,
                title = "Manual transaction",
                rawText = "",
                postedAt = transactionTimestamp,
                receivedAt = now,
                parseStatus = "MANUAL",
                amount = amount.trim().ifBlank { null },
                currency = currency.trim().uppercase().ifBlank { "RSD" },
                merchant = null,
                transactionName = normalizedTransactionName,
                operationType = OperationType.EXPENSE.name,
                accountHint = null,
                transactionDate = displayDay(normalizedDay),
                transactionDay = normalizedDay,
                transactionTimestamp = transactionTimestamp,
                availableBalance = null,
                availableBalanceCurrency = null,
                category = selectedCategory,
                isUserEdited = true,
                isDeleted = false,
                updatedAt = now,
            ),
        )
    }

    suspend fun deleteTransaction(id: Long) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    suspend fun createCategory(name: String) {
        val normalizedName = name.trim()
        if (normalizedName.isBlank()) return
        categoryDao.insert(
            CategoryEntity(
                name = normalizedName,
                isSystem = false,
                sortOrder = Int.MAX_VALUE,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun renameCategory(oldName: String, newName: String): Boolean {
        val normalizedName = newName.trim()
        if (normalizedName.isBlank() || normalizedName == oldName) return false
        return database.withTransaction {
            val existing = categoryDao.findByName(oldName)
            if (existing == null || existing.isSystem || categoryDao.findByName(normalizedName) != null) {
                return@withTransaction false
            }
            val inserted = categoryDao.insert(existing.copy(name = normalizedName))
            if (inserted == -1L) return@withTransaction false
            val now = System.currentTimeMillis()
            dao.replaceCategory(oldName, normalizedName, now)
            merchantRuleDao.replaceCategory(oldName, normalizedName, now)
            categoryDao.deleteUserCategory(oldName)
            true
        }
    }

    suspend fun deleteCategory(name: String): Boolean =
        database.withTransaction {
            val existing = categoryDao.findByName(name)
            if (existing == null || existing.isSystem) return@withTransaction false
            val now = System.currentTimeMillis()
            dao.replaceCategory(name, ExpenseCategories.OTHER, now)
            merchantRuleDao.replaceCategory(name, ExpenseCategories.OTHER, now)
            categoryDao.deleteUserCategory(name) > 0
        }

    suspend fun renameAccount(accountHint: String, name: String): Boolean {
        val normalizedName = name.trim()
        if (accountHint.isBlank() || normalizedName.isBlank()) return false
        return accountDao.rename(accountHint, normalizedName, System.currentTimeMillis()) > 0
    }

    suspend fun createBackup(): ExpenseBackupData =
        database.withTransaction {
            ExpenseBackupData(
                transactions = dao.getAllForBackup(),
                userCategories = categoryDao.getUserCategoriesForBackup(),
                merchantRules = merchantRuleDao.getAllForBackup(),
                accounts = accountDao.getAllForBackup(),
            )
        }

    suspend fun restoreBackup(data: ExpenseBackupData) {
        database.withTransaction {
            dao.deleteAll()
            merchantRuleDao.deleteAll()
            accountDao.deleteAll()
            categoryDao.deleteAllUserCategories()

            categoryDao.insertAll(data.userCategories)
            merchantRuleDao.insertAll(data.merchantRules)
            accountDao.insertAll(data.accounts)
            dao.insertAll(data.transactions)
        }
    }

    suspend fun clear() = dao.deleteAll()

    private suspend fun suggestCategory(
        merchant: String?,
        transactionName: String?,
        operationType: OperationType,
    ): String {
        val merchantPattern = categoryClassifier.normalizeMerchant(merchant)
        return merchantPattern
            .takeIf(String::isNotBlank)
            ?.let { merchantRuleDao.findCategory(it) }
            ?: categoryClassifier.suggest(transactionName, operationType)
    }

    private fun normalizeDay(value: String): String? =
        runCatching { LocalDate.parse(value.trim(), DISPLAY_DAY_FORMATTER).toString() }.getOrNull()

    private fun displayDay(value: String): String =
        LocalDate.parse(value).format(DISPLAY_DAY_FORMATTER)

    private fun dayOf(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp).atZone(ZONE_ID).toLocalDate().toString()

    private fun sortTimestamp(record: NotificationRecordEntity): Long =
        if (record.source == SOURCE_MANUAL) {
            record.receivedAt
        } else {
            record.transactionTimestamp ?: record.postedAt
        }

    private suspend fun saveMerchantRule(merchant: String?, category: String, updatedAt: Long) {
        categoryClassifier.normalizeMerchant(merchant)
            .takeIf(String::isNotBlank)
            ?.let { merchantPattern ->
                merchantRuleDao.upsert(
                    MerchantRuleEntity(
                        merchantPattern = merchantPattern,
                        category = category,
                        updatedAt = updatedAt,
                    ),
                )
            }
    }

    private suspend fun ensureAccount(accountHint: String?, now: Long) {
        val hint = accountHint?.trim()?.takeIf(String::isNotBlank) ?: return
        accountDao.insert(
            AccountEntity(
                accountHint = hint,
                name = defaultAccountName(hint),
                bankName = BANK_RAIFFEISEN_SERBIA,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun defaultAccountName(accountHint: String): String = "Card ****$accountHint"

    companion object {
        private const val SOURCE_NOTIFICATION = "NOTIFICATION"
        private const val SOURCE_MANUAL = "MANUAL"
        private const val BANK_RAIFFEISEN_SERBIA = "Raiffeisen Serbia"
        private val ZONE_ID: ZoneId = ZoneId.systemDefault()
        private val DISPLAY_DAY_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        @Volatile
        private var instance: NotificationRepository? = null

        fun getInstance(context: Context): NotificationRepository =
            instance ?: synchronized(this) {
                instance ?: AppDatabase.getInstance(context).let { database ->
                    NotificationRepository(
                        database,
                        database.notificationRecordDao(),
                        database.merchantRuleDao(),
                        database.categoryDao(),
                        database.accountDao(),
                    )
                }.also { instance = it }
            }
    }
}
