package com.familyexpensetracker.prototype.backup

import com.familyexpensetracker.prototype.data.AccountEntity
import com.familyexpensetracker.prototype.data.CategoryEntity
import com.familyexpensetracker.prototype.data.MerchantRuleEntity
import com.familyexpensetracker.prototype.data.NotificationRecordEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class ExpenseBackupData(
    val transactions: List<NotificationRecordEntity>,
    val userCategories: List<CategoryEntity>,
    val merchantRules: List<MerchantRuleEntity>,
    val accounts: List<AccountEntity>,
)

class ExpenseBackupSerializer {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun encode(data: ExpenseBackupData): String =
        json.encodeToString(
            BackupDocument(
                version = CURRENT_VERSION,
                transactions = data.transactions.map(TransactionBackup::fromEntity),
                userCategories = data.userCategories.map(CategoryBackup::fromEntity),
                merchantRules = data.merchantRules.map(MerchantRuleBackup::fromEntity),
                accounts = data.accounts.map(AccountBackup::fromEntity),
            ),
        )

    fun decode(content: String): ExpenseBackupData {
        val document = json.decodeFromString<BackupDocument>(content)
        require(document.version == CURRENT_VERSION) {
            "Unsupported backup version: ${document.version}"
        }
        return ExpenseBackupData(
            transactions = document.transactions.map(TransactionBackup::toEntity),
            userCategories = document.userCategories.map(CategoryBackup::toEntity),
            merchantRules = document.merchantRules.map(MerchantRuleBackup::toEntity),
            accounts = document.accounts.map(AccountBackup::toEntity),
        )
    }

    private companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
private data class BackupDocument(
    val version: Int,
    val transactions: List<TransactionBackup>,
    val userCategories: List<CategoryBackup>,
    val merchantRules: List<MerchantRuleBackup>,
    val accounts: List<AccountBackup>,
)

@Serializable
private data class TransactionBackup(
    val id: Long,
    val fingerprint: String,
    val packageName: String,
    val source: String,
    val title: String?,
    val postedAt: Long,
    val receivedAt: Long,
    val parseStatus: String,
    val amount: String?,
    val currency: String?,
    val merchant: String?,
    val transactionName: String?,
    val operationType: String,
    val accountHint: String?,
    val transactionDate: String?,
    val transactionDay: String?,
    val transactionTimestamp: Long?,
    val availableBalance: String?,
    val availableBalanceCurrency: String?,
    val category: String?,
    val isUserEdited: Boolean,
    val isDeleted: Boolean,
    val updatedAt: Long,
) {
    fun toEntity() = NotificationRecordEntity(
        id = id,
        fingerprint = fingerprint,
        packageName = packageName,
        source = source,
        title = title,
        rawText = "",
        postedAt = postedAt,
        receivedAt = receivedAt,
        parseStatus = parseStatus,
        amount = amount,
        currency = currency,
        merchant = merchant,
        transactionName = transactionName,
        operationType = operationType,
        accountHint = accountHint,
        transactionDate = transactionDate,
        transactionDay = transactionDay,
        transactionTimestamp = transactionTimestamp,
        availableBalance = availableBalance,
        availableBalanceCurrency = availableBalanceCurrency,
        category = category,
        isUserEdited = isUserEdited,
        isDeleted = isDeleted,
        updatedAt = updatedAt,
    )

    companion object {
        fun fromEntity(entity: NotificationRecordEntity) = TransactionBackup(
            id = entity.id,
            fingerprint = entity.fingerprint,
            packageName = entity.packageName,
            source = entity.source,
            title = entity.title,
            postedAt = entity.postedAt,
            receivedAt = entity.receivedAt,
            parseStatus = entity.parseStatus,
            amount = entity.amount,
            currency = entity.currency,
            merchant = entity.merchant,
            transactionName = entity.transactionName,
            operationType = entity.operationType,
            accountHint = entity.accountHint,
            transactionDate = entity.transactionDate,
            transactionDay = entity.transactionDay,
            transactionTimestamp = entity.transactionTimestamp,
            availableBalance = entity.availableBalance,
            availableBalanceCurrency = entity.availableBalanceCurrency,
            category = entity.category,
            isUserEdited = entity.isUserEdited,
            isDeleted = entity.isDeleted,
            updatedAt = entity.updatedAt,
        )
    }
}

@Serializable
private data class CategoryBackup(
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
) {
    fun toEntity() = CategoryEntity(name, isSystem = false, sortOrder, createdAt)

    companion object {
        fun fromEntity(entity: CategoryEntity) =
            CategoryBackup(entity.name, entity.sortOrder, entity.createdAt)
    }
}

@Serializable
private data class MerchantRuleBackup(
    val merchantPattern: String,
    val category: String,
    val updatedAt: Long,
) {
    fun toEntity() = MerchantRuleEntity(merchantPattern, category, updatedAt)

    companion object {
        fun fromEntity(entity: MerchantRuleEntity) =
            MerchantRuleBackup(entity.merchantPattern, entity.category, entity.updatedAt)
    }
}

@Serializable
private data class AccountBackup(
    val accountHint: String,
    val name: String,
    val bankName: String,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toEntity() = AccountEntity(accountHint, name, bankName, createdAt, updatedAt)

    companion object {
        fun fromEntity(entity: AccountEntity) =
            AccountBackup(entity.accountHint, entity.name, entity.bankName, entity.createdAt, entity.updatedAt)
    }
}
