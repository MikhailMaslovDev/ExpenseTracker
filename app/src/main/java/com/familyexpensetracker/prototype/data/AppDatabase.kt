package com.familyexpensetracker.prototype.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.familyexpensetracker.prototype.categorization.ExpenseCategories

@Database(
    entities = [
        NotificationRecordEntity::class,
        MerchantRuleEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
    ],
    version = 8,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notificationRecordDao(): NotificationRecordDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense-push-prototype.db",
                ).addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                )
                    .addCallback(
                        object : RoomDatabase.Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                super.onCreate(db)
                                insertDefaultCategories(db)
                            }
                        },
                    )
                    .build()
                    .also { instance = it }
            }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_records ADD COLUMN transactionTimestamp INTEGER")
                db.execSQL("ALTER TABLE notification_records ADD COLUMN availableBalance TEXT")
                db.execSQL("ALTER TABLE notification_records ADD COLUMN availableBalanceCurrency TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_records ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE notification_records ADD COLUMN isUserEdited INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notification_records ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notification_records ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS merchant_rules (
                        merchantPattern TEXT NOT NULL,
                        category TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(merchantPattern)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE notification_records ADD COLUMN source TEXT NOT NULL DEFAULT 'NOTIFICATION'",
                )
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notification_records ADD COLUMN transactionName TEXT")
                db.execSQL("ALTER TABLE notification_records ADD COLUMN transactionDay TEXT")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS categories (
                        name TEXT NOT NULL,
                        isSystem INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        PRIMARY KEY(name)
                    )
                    """.trimIndent(),
                )
                insertDefaultCategories(db)
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS accounts (
                        accountHint TEXT NOT NULL,
                        name TEXT NOT NULL,
                        bankName TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(accountHint)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO accounts(accountHint, name, bankName, createdAt, updatedAt)
                    SELECT DISTINCT
                        accountHint,
                        'Card ****' || accountHint,
                        'Raiffeisen Serbia',
                        0,
                        0
                    FROM notification_records
                    WHERE accountHint IS NOT NULL AND TRIM(accountHint) != ''
                    """.trimIndent(),
                )
            }
        }

        private fun insertDefaultCategories(db: SupportSQLiteDatabase) {
            ExpenseCategories.all.forEachIndexed { index, category ->
                db.execSQL(
                    "INSERT OR IGNORE INTO categories(name, isSystem, sortOrder, createdAt) VALUES(?, 1, ?, 0)",
                    arrayOf<Any>(category, index),
                )
            }
        }
    }
}
