package com.example.aifinancerfree.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "aifinancer_local.db"
        private const val DATABASE_VERSION = 4

        // Table Names
        const val TABLE_TRANSACTIONS = "transactions"
        const val TABLE_BUDGETS = "budgets"
        const val TABLE_PARSED_SMS = "parsed_sms"

        // Transactions Columns
        const val COL_TX_ID = "id"
        const val COL_TX_USER_ID = "user_id"
        const val COL_TX_REMOTE_ID = "remote_id"
        const val COL_TX_CLIENT_ID = "client_id"
        const val COL_TX_TYPE = "type"
        const val COL_TX_AMOUNT = "amount"
        const val COL_TX_CATEGORY = "category"
        const val COL_TX_MERCHANT = "merchant"
        const val COL_TX_ACCOUNT = "account"
        const val COL_TX_NOTES = "notes"
        const val COL_TX_SOURCE = "source"
        const val COL_TX_DATE = "date"
        const val COL_TX_SYNCED = "synced"

        // Budgets Columns
        const val COL_BUDGET_CATEGORY = "category"
        const val COL_BUDGET_LIMIT = "limit_amount"
        const val COL_BUDGET_USER_ID = "user_id"

        // Parsed SMS Columns
        const val COL_SMS_FINGERPRINT = "fingerprint"
        const val COL_SMS_BODY_HASH = "body_hash"
        const val COL_SMS_USER_ID = "user_id"
        const val COL_SMS_TIMESTAMP = "timestamp"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTxTable = """
            CREATE TABLE $TABLE_TRANSACTIONS (
                $COL_TX_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TX_USER_ID TEXT,
                $COL_TX_REMOTE_ID TEXT,
                $COL_TX_CLIENT_ID TEXT,
                $COL_TX_TYPE TEXT NOT NULL,
                $COL_TX_AMOUNT REAL NOT NULL,
                $COL_TX_CATEGORY TEXT NOT NULL,
                $COL_TX_MERCHANT TEXT NOT NULL,
                $COL_TX_ACCOUNT TEXT NOT NULL,
                $COL_TX_NOTES TEXT,
                $COL_TX_SOURCE TEXT DEFAULT 'manual',
                $COL_TX_DATE INTEGER NOT NULL,
                $COL_TX_SYNCED INTEGER DEFAULT 0
            )
        """.trimIndent()

        val createBudgetTable = """
            CREATE TABLE $TABLE_BUDGETS (
                $COL_BUDGET_CATEGORY TEXT,
                $COL_BUDGET_LIMIT REAL NOT NULL,
                $COL_BUDGET_USER_ID TEXT,
                PRIMARY KEY ($COL_BUDGET_CATEGORY, $COL_BUDGET_USER_ID)
            )
        """.trimIndent()

        val createSmsTable = """
            CREATE TABLE $TABLE_PARSED_SMS (
                $COL_SMS_FINGERPRINT TEXT,
                $COL_SMS_BODY_HASH TEXT,
                $COL_SMS_USER_ID TEXT,
                $COL_SMS_TIMESTAMP INTEGER NOT NULL,
                PRIMARY KEY ($COL_SMS_FINGERPRINT, $COL_SMS_USER_ID)
            )
        """.trimIndent()

        db.execSQL(createTxTable)
        db.execSQL(createBudgetTable)
        db.execSQL(createSmsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            // Non-destructive migration to version 3
            try {
                db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN $COL_TX_CLIENT_ID TEXT")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN $COL_TX_SOURCE TEXT DEFAULT 'manual'")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_PARSED_SMS ADD COLUMN $COL_SMS_BODY_HASH TEXT")
            } catch (_: Exception) {}
        }
        if (oldVersion < 4) {
            // Non-destructive migration to version 4: multi-user isolation without data deletion
            try {
                db.execSQL("ALTER TABLE $TABLE_TRANSACTIONS ADD COLUMN $COL_TX_USER_ID TEXT")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_BUDGETS ADD COLUMN $COL_BUDGET_USER_ID TEXT")
            } catch (_: Exception) {}
            try {
                db.execSQL("ALTER TABLE $TABLE_PARSED_SMS ADD COLUMN $COL_SMS_USER_ID TEXT")
            } catch (_: Exception) {}
        }
    }

    fun resetParsedSmsHistory(userId: String? = null) {
        val db = writableDatabase
        if (!userId.isNullOrEmpty()) {
            db.delete(TABLE_PARSED_SMS, "$COL_SMS_USER_ID = ?", arrayOf(userId))
        } else {
            db.delete(TABLE_PARSED_SMS, null, null)
        }
    }

    /**
     * Checks if a transaction matching the given content already exists in the transactions table.
     * Prevents duplicate entries regardless of client_id or fingerprint discrepancies.
     *
     * @param db Readable or writable SQLiteDatabase
     * @param userId Active user ID
     * @param type Transaction type ('expense' or 'income')
     * @param amount Transaction amount
     * @param merchant Merchant or party name
     * @param dateMs Transaction timestamp in milliseconds
     * @param windowMs Timestamp tolerance in milliseconds (default: 3 minutes)
     * @return true if an identical transaction exists within the tolerance window, false otherwise
     */
    fun hasMatchingTransaction(
        db: SQLiteDatabase,
        userId: String,
        type: String,
        amount: Double,
        merchant: String,
        dateMs: Long,
        windowMs: Long = 180_000L
    ): Boolean {
        val minDate = dateMs - windowMs
        val maxDate = dateMs + windowMs
        val minAmount = amount - 0.01
        val maxAmount = amount + 0.01
        val normMerchant = merchant.trim()

        val query = if (userId.isNotEmpty()) {
            """
            SELECT 1 FROM $TABLE_TRANSACTIONS 
            WHERE $COL_TX_USER_ID = ? 
              AND $COL_TX_TYPE = ? 
              AND $COL_TX_MERCHANT = ? COLLATE NOCASE 
              AND $COL_TX_AMOUNT >= ? AND $COL_TX_AMOUNT <= ? 
              AND $COL_TX_DATE >= ? AND $COL_TX_DATE <= ? 
            LIMIT 1
            """.trimIndent()
        } else {
            """
            SELECT 1 FROM $TABLE_TRANSACTIONS 
            WHERE $COL_TX_TYPE = ? 
              AND $COL_TX_MERCHANT = ? COLLATE NOCASE 
              AND $COL_TX_AMOUNT >= ? AND $COL_TX_AMOUNT <= ? 
              AND $COL_TX_DATE >= ? AND $COL_TX_DATE <= ? 
            LIMIT 1
            """.trimIndent()
        }

        val args = if (userId.isNotEmpty()) {
            arrayOf(
                userId,
                type,
                normMerchant,
                minAmount.toString(),
                maxAmount.toString(),
                minDate.toString(),
                maxDate.toString()
            )
        } else {
            arrayOf(
                type,
                normMerchant,
                minAmount.toString(),
                maxAmount.toString(),
                minDate.toString(),
                maxDate.toString()
            )
        }

        val cursor = db.rawQuery(query, args)
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    /**
     * Safely cleans up existing duplicate transaction records in TABLE_TRANSACTIONS,
     * retaining the earliest inserted record (lowest id) for any identical
     * (user_id, type, amount, merchant, date) groups.
     *
     * @param userId Optional active user filter. If null, cleans up all duplicates.
     * @return Number of duplicate rows deleted.
     */
    fun cleanupDuplicateTransactions(userId: String? = null): Int {
        val db = writableDatabase
        return try {
            val userFilter = if (!userId.isNullOrEmpty()) "AND t1.$COL_TX_USER_ID = ?" else ""
            val userArgs = if (!userId.isNullOrEmpty()) arrayOf(userId) else null

            val deleteSql = """
                DELETE FROM $TABLE_TRANSACTIONS
                WHERE $COL_TX_ID IN (
                    SELECT t1.$COL_TX_ID
                    FROM $TABLE_TRANSACTIONS t1
                    JOIN $TABLE_TRANSACTIONS t2
                      ON (t1.$COL_TX_USER_ID = t2.$COL_TX_USER_ID OR (t1.$COL_TX_USER_ID IS NULL AND t2.$COL_TX_USER_ID IS NULL))
                     AND t1.$COL_TX_TYPE = t2.$COL_TX_TYPE
                     AND t1.$COL_TX_MERCHANT = t2.$COL_TX_MERCHANT COLLATE NOCASE
                     AND ABS(t1.$COL_TX_AMOUNT - t2.$COL_TX_AMOUNT) < 0.01
                     AND ABS(t1.$COL_TX_DATE - t2.$COL_TX_DATE) <= 180000
                     AND t1.$COL_TX_ID > t2.$COL_TX_ID
                    $userFilter
                )
            """.trimIndent()

            if (userArgs != null) {
                db.execSQL(deleteSql, userArgs)
            } else {
                db.execSQL(deleteSql)
            }
            Log.i("DatabaseHelper", "[CLEANUP] Completed duplicate transaction sweep.")
            1
        } catch (e: Exception) {
            Log.e("DatabaseHelper", "[CLEANUP ERROR] Failed cleaning duplicates", e)
            0
        }
    }
}
