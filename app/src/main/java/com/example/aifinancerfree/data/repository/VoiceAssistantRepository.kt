package com.example.aifinancerfree.data.repository

import android.content.ContentValues
import android.util.Log
import com.example.aifinancerfree.data.local.DatabaseHelper
import com.example.aifinancerfree.data.local.TokenManager
import com.example.aifinancerfree.data.model.VoiceActionResult
import com.example.aifinancerfree.data.model.VoiceCommandRequest
import com.example.aifinancerfree.data.model.VoiceCommandResponse
import com.example.aifinancerfree.data.network.ApiService
import com.example.aifinancerfree.data.voice.VoiceQueryIntent
import com.example.aifinancerfree.data.voice.VoiceTransactionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class UndoAction(
    val localId: Long,
    val remoteId: String?,
    val amount: Double,
    val merchant: String,
    val timestamp: Long
)

class VoiceAssistantRepository(
    private val apiService: ApiService,
    private val dbHelper: DatabaseHelper,
    private val tokenManager: TokenManager? = null,
    private val transactionRepository: TransactionRepository
) {
    companion object {
        private const val TAG = "VoiceAssistantRepo"
        private const val UNDO_TIMEOUT_MS = 10000L // 10 seconds window for undo
    }

    private var lastAction: UndoAction? = null

    private fun getActiveUserId(): String {
        return tokenManager?.getUserId() ?: tokenManager?.getUsername() ?: ""
    }

    suspend fun executeVoiceCommand(
        transcript: String,
        accountPreference: String = "Cash"
    ): VoiceActionResult = withContext(Dispatchers.IO) {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) {
            return@withContext VoiceActionResult.Error(
                message = "Empty voice command",
                spokenFallback = "I didn't catch that. Could you repeat?"
            )
        }

        // 1. Try backend AI voice endpoint
        try {
            val request = VoiceCommandRequest(
                transcript = trimmed,
                clientTimestamp = System.currentTimeMillis() / 1000.0,
                accountPreference = accountPreference
            )
            val response = apiService.processVoiceCommand(request)

            val entity = response.entity
            val amount = entity?.amount
            if (entity != null && entity.executed && amount != null) {
                // Transaction executed on backend; persist to local SQLite as synced
                val localId = insertLocalTransaction(
                    remoteId = entity.transactionId,
                    type = entity.type ?: "expense",
                    amount = amount,
                    category = entity.category ?: "Other",
                    merchant = entity.merchant ?: "General",
                    account = entity.account ?: accountPreference,
                    notes = entity.notes ?: "Voice command",
                    synced = 1
                )

                lastAction = UndoAction(
                    localId = localId,
                    remoteId = entity.transactionId,
                    amount = amount,
                    merchant = entity.merchant ?: "General",
                    timestamp = System.currentTimeMillis()
                )

                return@withContext VoiceActionResult.Success(
                    response = response,
                    localTransactionId = localId,
                    canUndo = true
                )
            } else {
                return@withContext VoiceActionResult.Success(
                    response = response,
                    localTransactionId = null,
                    canUndo = false
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend voice command failed or device is offline. Triggering on-device fallback parser.", e)

            // 2. Check if this is an offline spending / balance / budget inquiry
            val queryIntent = VoiceTransactionParser.parseQueryIntent(trimmed)
            if (queryIntent != null) {
                val queryResponse = executeOfflineQuery(queryIntent)
                return@withContext VoiceActionResult.Success(
                    response = queryResponse,
                    localTransactionId = null,
                    canUndo = false
                )
            }

            // 3. Offline fallback rule-based parser for transaction logging
            val parsedResponse = VoiceTransactionParser.parse(trimmed, accountPreference)
            val entity = parsedResponse?.entity
            val offlineAmount = entity?.amount
            if (parsedResponse != null && entity != null && offlineAmount != null) {
                // Save locally with synced = 0 for background sync
                val localId = insertLocalTransaction(
                    remoteId = null,
                    type = entity.type ?: "expense",
                    amount = offlineAmount,
                    category = entity.category ?: "Other",
                    merchant = entity.merchant ?: "General",
                    account = entity.account ?: accountPreference,
                    notes = "Voice command (offline fallback)",
                    synced = 0
                )

                lastAction = UndoAction(
                    localId = localId,
                    remoteId = null,
                    amount = offlineAmount,
                    merchant = entity.merchant ?: "General",
                    timestamp = System.currentTimeMillis()
                )

                // Try syncing if internet returns
                try {
                    transactionRepository.syncPendingTransactions()
                } catch (_: Exception) {}

                return@withContext VoiceActionResult.Success(
                    response = parsedResponse,
                    localTransactionId = localId,
                    canUndo = true
                )
            }

            return@withContext VoiceActionResult.Error(
                message = e.localizedMessage ?: "Network error and offline parser could not recognize transaction",
                spokenFallback = "I am offline and couldn't process that command. Please try again."
            )
        }
    }

    suspend fun undoLastAction(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val action = lastAction
        if (action == null) {
            return@withContext Pair(false, "No recent voice transaction to undo.")
        }

        val elapsed = System.currentTimeMillis() - action.timestamp
        if (elapsed > UNDO_TIMEOUT_MS) {
            lastAction = null
            return@withContext Pair(false, "Undo window has expired.")
        }

        val db = dbHelper.writableDatabase
        try {
            db.delete(
                DatabaseHelper.TABLE_TRANSACTIONS,
                "${DatabaseHelper.COL_TX_ID} = ?",
                arrayOf(action.localId.toString())
            )

            if (!action.remoteId.isNullOrEmpty()) {
                try {
                    apiService.deleteTransaction(action.remoteId)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to delete remote transaction during undo", e)
                }
            }

            lastAction = null
            val message = "Undid transaction of ₹${action.amount} for ${action.merchant}."
            return@withContext Pair(true, message)
        } catch (e: Exception) {
            Log.e(TAG, "Error while executing undo", e)
            return@withContext Pair(false, "Failed to undo transaction.")
        }
    }

    private fun insertLocalTransaction(
        remoteId: String?,
        type: String,
        amount: Double,
        category: String,
        merchant: String,
        account: String,
        notes: String,
        synced: Int
    ): Long {
        val db = dbHelper.writableDatabase
        val activeUser = getActiveUserId()
        val clientId = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_TX_USER_ID, activeUser)
            put(DatabaseHelper.COL_TX_REMOTE_ID, remoteId)
            put(DatabaseHelper.COL_TX_CLIENT_ID, clientId)
            put(DatabaseHelper.COL_TX_TYPE, type)
            put(DatabaseHelper.COL_TX_AMOUNT, amount)
            put(DatabaseHelper.COL_TX_CATEGORY, category)
            put(DatabaseHelper.COL_TX_MERCHANT, merchant)
            put(DatabaseHelper.COL_TX_ACCOUNT, account)
            put(DatabaseHelper.COL_TX_NOTES, notes)
            put(DatabaseHelper.COL_TX_SOURCE, "voice")
            put(DatabaseHelper.COL_TX_DATE, System.currentTimeMillis())
            put(DatabaseHelper.COL_TX_SYNCED, synced)
        }
        return db.insert(DatabaseHelper.TABLE_TRANSACTIONS, null, values)
    }

    fun executeOfflineQuery(intent: VoiceQueryIntent): VoiceCommandResponse {
        val db = dbHelper.readableDatabase
        val activeUser = getActiveUserId()

        return when (intent) {
            is VoiceQueryIntent.TotalSpend -> {
                val cursor = db.rawQuery(
                    "SELECT SUM(${DatabaseHelper.COL_TX_AMOUNT}), COUNT(*) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_USER_ID} = ? AND ${DatabaseHelper.COL_TX_TYPE} = 'expense' AND ${DatabaseHelper.COL_TX_CATEGORY} != 'Transfer'",
                    arrayOf(activeUser)
                )
                var total = 0.0
                var count = 0
                if (cursor.moveToFirst()) {
                    total = cursor.getDouble(0)
                    count = cursor.getInt(1)
                }
                cursor.close()

                val formatted = formatCurrency(total)
                val spoken = "Offline analysis: You have spent $formatted rupees across $count transactions."
                val display = "Offline Total Spending: ₹$formatted across $count transactions"
                VoiceCommandResponse(
                    actionType = "QUERY_SPENDING",
                    spokenText = spoken,
                    displayText = display,
                    data = mapOf("offline" to true, "total_spent" to total, "count" to count)
                )
            }
            is VoiceQueryIntent.CategorySpend -> {
                val cursor = db.rawQuery(
                    "SELECT SUM(${DatabaseHelper.COL_TX_AMOUNT}), COUNT(*) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_USER_ID} = ? AND ${DatabaseHelper.COL_TX_TYPE} = 'expense' AND ${DatabaseHelper.COL_TX_CATEGORY} = ?",
                    arrayOf(activeUser, intent.category)
                )
                var catTotal = 0.0
                var count = 0
                if (cursor.moveToFirst()) {
                    catTotal = cursor.getDouble(0)
                    count = cursor.getInt(1)
                }
                cursor.close()

                val formatted = formatCurrency(catTotal)
                val spoken = "Offline analysis: You have spent $formatted rupees on ${intent.category} across $count transactions."
                val display = "Offline Spending for ${intent.category}: ₹$formatted ($count transactions)"
                VoiceCommandResponse(
                    actionType = "QUERY_SPENDING",
                    spokenText = spoken,
                    displayText = display,
                    data = mapOf("offline" to true, "category" to intent.category, "total_spent" to catTotal, "count" to count)
                )
            }
            is VoiceQueryIntent.Balance -> {
                val incomeCursor = db.rawQuery(
                    "SELECT SUM(${DatabaseHelper.COL_TX_AMOUNT}) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_USER_ID} = ? AND ${DatabaseHelper.COL_TX_TYPE} = 'income'",
                    arrayOf(activeUser)
                )
                var income = 0.0
                if (incomeCursor.moveToFirst()) {
                    income = incomeCursor.getDouble(0)
                }
                incomeCursor.close()

                val expenseCursor = db.rawQuery(
                    "SELECT SUM(${DatabaseHelper.COL_TX_AMOUNT}) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_USER_ID} = ? AND ${DatabaseHelper.COL_TX_TYPE} = 'expense' AND ${DatabaseHelper.COL_TX_CATEGORY} != 'Transfer'",
                    arrayOf(activeUser)
                )
                var expense = 0.0
                if (expenseCursor.moveToFirst()) {
                    expense = expenseCursor.getDouble(0)
                }
                expenseCursor.close()

                val balance = income - expense
                val formattedBalance = formatCurrency(balance)
                val spoken = "Offline balance: Your safe to spend balance is $formattedBalance rupees."
                val display = "Offline Balance: ₹$formattedBalance (Income: ₹${formatCurrency(income)}, Spent: ₹${formatCurrency(expense)})"
                VoiceCommandResponse(
                    actionType = "QUERY_BALANCE",
                    spokenText = spoken,
                    displayText = display,
                    data = mapOf("offline" to true, "balance" to balance, "income" to income, "spent" to expense)
                )
            }
            is VoiceQueryIntent.BudgetCheck -> {
                if (intent.category != null) {
                    val bCursor = db.rawQuery(
                        "SELECT ${DatabaseHelper.COL_BUDGET_LIMIT} FROM ${DatabaseHelper.TABLE_BUDGETS} WHERE ${DatabaseHelper.COL_BUDGET_USER_ID} = ? AND ${DatabaseHelper.COL_BUDGET_CATEGORY} = ?",
                        arrayOf(activeUser, intent.category)
                    )
                    var limit = 0.0
                    var hasLimit = false
                    if (bCursor.moveToFirst()) {
                        limit = bCursor.getDouble(0)
                        hasLimit = true
                    }
                    bCursor.close()

                    val txCursor = db.rawQuery(
                        "SELECT SUM(${DatabaseHelper.COL_TX_AMOUNT}) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_USER_ID} = ? AND ${DatabaseHelper.COL_TX_TYPE} = 'expense' AND ${DatabaseHelper.COL_TX_CATEGORY} = ?",
                        arrayOf(activeUser, intent.category)
                    )
                    var spent = 0.0
                    if (txCursor.moveToFirst()) {
                        spent = txCursor.getDouble(0)
                    }
                    txCursor.close()

                    val spoken = if (hasLimit) {
                        val remaining = limit - spent
                        if (remaining >= 0) {
                            "Your ${intent.category} budget limit is ${formatCurrency(limit)} rupees. You have spent ${formatCurrency(spent)} rupees, leaving ${formatCurrency(remaining)} rupees."
                        } else {
                            "Alert: You have exceeded your ${intent.category} budget limit of ${formatCurrency(limit)} rupees by ${formatCurrency(-remaining)} rupees."
                        }
                    } else {
                        "You haven't set a budget limit for ${intent.category}. Current spending is ${formatCurrency(spent)} rupees."
                    }

                    val display = "Budget Status for ${intent.category}: Spent ₹${formatCurrency(spent)}" + (if (hasLimit) " of ₹${formatCurrency(limit)} limit" else "")
                    VoiceCommandResponse(
                        actionType = "QUERY_BUDGET",
                        spokenText = spoken,
                        displayText = display,
                        data = mapOf("offline" to true, "category" to intent.category, "spent" to spent, "limit" to limit)
                    )
                } else {
                    val bCursor = db.rawQuery(
                        "SELECT ${DatabaseHelper.COL_BUDGET_CATEGORY}, ${DatabaseHelper.COL_BUDGET_LIMIT} FROM ${DatabaseHelper.TABLE_BUDGETS} WHERE ${DatabaseHelper.COL_BUDGET_USER_ID} = ?",
                        arrayOf(activeUser)
                    )
                    val overruns = mutableListOf<String>()
                    var countBudgets = 0
                    if (bCursor.moveToFirst()) {
                        val catCol = bCursor.getColumnIndexOrThrow(DatabaseHelper.COL_BUDGET_CATEGORY)
                        val limCol = bCursor.getColumnIndexOrThrow(DatabaseHelper.COL_BUDGET_LIMIT)
                        do {
                            countBudgets++
                            val cat = bCursor.getString(catCol)
                            val limit = bCursor.getDouble(limCol)

                            val sCursor = db.rawQuery(
                                "SELECT SUM(${DatabaseHelper.COL_TX_AMOUNT}) FROM ${DatabaseHelper.TABLE_TRANSACTIONS} WHERE ${DatabaseHelper.COL_TX_USER_ID} = ? AND ${DatabaseHelper.COL_TX_TYPE} = 'expense' AND ${DatabaseHelper.COL_TX_CATEGORY} = ?",
                                arrayOf(activeUser, cat)
                            )
                            var spent = 0.0
                            if (sCursor.moveToFirst()) {
                                spent = sCursor.getDouble(0)
                            }
                            sCursor.close()

                            if (spent > limit) {
                                overruns.add("$cat by ${formatCurrency(spent - limit)} rupees")
                            }
                        } while (bCursor.moveToNext())
                    }
                    bCursor.close()

                    val spoken = when {
                        countBudgets == 0 -> "You have no local category budget limits configured."
                        overruns.isNotEmpty() -> "Alert: You have exceeded budget in: ${overruns.joinToString(", ")}."
                        else -> "Great news! All your $countBudgets category budgets are healthy and within limits."
                    }
                    val display = if (overruns.isNotEmpty()) "Over budget in: ${overruns.joinToString(", ")}" else "All $countBudgets configured category budgets are within limits."
                    VoiceCommandResponse(
                        actionType = "QUERY_BUDGET",
                        spokenText = spoken,
                        displayText = display,
                        data = mapOf("offline" to true, "over_budget_count" to overruns.size)
                    )
                }
            }
        }
    }

    private fun formatCurrency(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            amount.toLong().toString()
        } else {
            String.format("%.2f", amount)
        }
    }
}
