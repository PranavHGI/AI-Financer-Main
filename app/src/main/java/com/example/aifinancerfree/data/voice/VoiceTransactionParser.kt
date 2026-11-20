package com.example.aifinancerfree.data.voice

import com.example.aifinancerfree.data.model.VoiceActionEntity
import com.example.aifinancerfree.data.model.VoiceCommandResponse
import java.util.regex.Pattern

sealed class VoiceQueryIntent {
    data class TotalSpend(val timeRange: String = "all") : VoiceQueryIntent()
    data class CategorySpend(val category: String) : VoiceQueryIntent()
    data object Balance : VoiceQueryIntent()
    data class BudgetCheck(val category: String? = null) : VoiceQueryIntent()
}

object VoiceTransactionParser {

    private val KNOWN_MERCHANTS = listOf(
        "Starbucks", "McDonald's", "KFC", "Dominos", "Swiggy", "Zomato",
        "Uber", "Ola", "Shell", "Amazon", "Flipkart", "Myntra",
        "Netflix", "Spotify", "D-Mart", "Walmart", "Apple", "Google"
    )

    private val CATEGORY_KEYWORD_MAP = mapOf(
        "Food & dining" to listOf("coffee", "cafe", "starbucks", "swiggy", "zomato", "restaurant", "lunch", "dinner", "breakfast", "food", "burger", "pizza", "groceries", "supermarket", "snacks", "tea", "mcdonald"),
        "Travel" to listOf("uber", "ola", "cab", "taxi", "metro", "bus", "train", "flight", "petrol", "diesel", "fuel", "toll", "auto"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "clothes", "shoes", "shopping", "mall", "electronics", "dress", "store"),
        "Bills" to listOf("electricity", "water", "wifi", "internet", "recharge", "rent", "gas", "bill", "broadband", "mobile"),
        "Entertainment" to listOf("netflix", "spotify", "movie", "cinema", "theatre", "tickets", "games", "prime", "concert"),
        "Health" to listOf("medicine", "pharmacy", "hospital", "doctor", "clinic", "gym", "medical", "checkup"),
        "Salary" to listOf("salary", "bonus", "stipend", "paycheck"),
        "Freelance" to listOf("freelance", "upwork", "fiverr", "client project", "consulting"),
        "Transfer" to listOf("transfer", "sent to", "upi to")
    )

    /**
     * Identifies if a voice input is a financial inquiry (spending, budget, or balance query).
     */
    fun parseQueryIntent(transcript: String): VoiceQueryIntent? {
        val lower = transcript.trim().lowercase()
        if (lower.isEmpty()) return null

        // 1. Balance queries
        if (lower.contains("balance") || lower.contains("how much money do i have") || 
            lower.contains("my account balance") || lower.contains("safe to spend")) {
            return VoiceQueryIntent.Balance
        }

        // 2. Budget status queries
        if (lower.contains("budget") || lower.contains("limit left") || 
            lower.contains("within budget") || lower.contains("over budget")) {
            val matchedCat = resolveCategoryFromQuery(lower)
            return VoiceQueryIntent.BudgetCheck(matchedCat)
        }

        // 3. Spending analysis queries
        val isSpendingQuery = listOf(
            "how much did i spend",
            "what did i spend",
            "how much have i spent",
            "how much spent",
            "total spend",
            "total spent",
            "total expense",
            "total expenses",
            "my spending",
            "my expenses",
            "spending on",
            "expenses on",
            "spend on"
        ).any { lower.contains(it) }

        if (isSpendingQuery) {
            val matchedCategory = resolveCategoryFromQuery(lower)
            return if (matchedCategory != null) {
                VoiceQueryIntent.CategorySpend(matchedCategory)
            } else {
                VoiceQueryIntent.TotalSpend()
            }
        }

        return null
    }

    fun resolveCategoryFromQuery(text: String): String? {
        for ((cat, keywords) in CATEGORY_KEYWORD_MAP) {
            if (text.contains(cat.lowercase())) return cat
            for (kw in keywords) {
                if (Regex("""\b${Regex.escape(kw)}\b""").containsMatchIn(text)) {
                    return cat
                }
            }
        }
        return null
    }

    fun parse(transcript: String, defaultAccount: String = "Cash"): VoiceCommandResponse? {
        val lower = transcript.trim().lowercase()
        if (lower.isEmpty()) return null

        val amount = extractAmount(lower) ?: return null

        val type = if (isIncome(lower)) "income" else "expense"
        val merchant = extractMerchant(transcript)
        val category = resolveCategory(lower, merchant)
        val account = extractAccount(lower, defaultAccount)

        val entity = VoiceActionEntity(
            actionType = "add_transaction",
            type = type,
            amount = amount,
            merchant = merchant,
            category = category,
            account = account,
            notes = "Voice command (offline)",
            executed = true
        )

        val verb = if (type == "expense") "Spent" else "Received"
        val spokenText = "$verb ₹${formatAmount(amount)} for $merchant under $category."
        val displayText = "Offline Log: $verb ₹${formatAmount(amount)} at $merchant ($category via $account)."

        return VoiceCommandResponse(
            actionType = "add_transaction",
            spokenText = spokenText,
            displayText = displayText,
            entity = entity,
            data = mapOf("offline" to true)
        )
    }

    private fun extractAmount(text: String): Double? {
        val pattern = Pattern.compile("""(?:₹|rs\.?|inr|\$)?\s*([0-9]+(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|\.[0-9]{1,2})\s*(?:rupees|rs\b|bucks\b|inr|dollars)?""", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(text)
        var foundAmount: Double? = null
        while (matcher.find()) {
            val raw = matcher.group(1)?.replace(",", "")
            val parsed = raw?.toDoubleOrNull()
            if (parsed != null && parsed > 0.0) {
                foundAmount = parsed
                break
            }
        }
        return foundAmount
    }

    private fun isIncome(text: String): Boolean {
        val incomeKeywords = listOf("received", "credited", "salary", "earned", "refund", "income", "got", "deposited")
        return incomeKeywords.any { text.contains(it) }
    }

    private fun extractMerchant(text: String): String {
        for (known in KNOWN_MERCHANTS) {
            if (text.contains(known, ignoreCase = true)) {
                return known
            }
        }

        val prepPattern = Pattern.compile("""\b(?:at|for|to|from|in|on)\s+([A-Za-z0-9&'\s]+?)(?:\s+(?:for|using|with|via|through|on|in|under|as)\b|$)""", Pattern.CASE_INSENSITIVE)
        val matcher = prepPattern.matcher(text)
        if (matcher.find()) {
            val candidate = matcher.group(1)?.trim()
            if (!candidate.isNullOrEmpty() && candidate.length in 2..30 && !candidate.matches(Regex("""^[0-9]+$"""))) {
                return candidate.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            }
        }

        return "General"
    }

    private fun resolveCategory(text: String, merchant: String): String {
        val combined = "$text ${merchant.lowercase()}"
        for ((cat, keywords) in CATEGORY_KEYWORD_MAP) {
            for (keyword in keywords) {
                if (combined.contains(keyword)) {
                    return cat
                }
            }
        }
        return "Other"
    }

    private fun extractAccount(text: String, defaultAccount: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("credit card") -> "Credit Card"
            lower.contains("debit card") -> "Debit Card"
            lower.contains("upi") || lower.contains("gpay") || lower.contains("phonepe") || lower.contains("paytm") -> "UPI"
            lower.contains("bank") || lower.contains("netbanking") -> "Bank Account"
            lower.contains("cash") -> "Cash"
            else -> defaultAccount
        }
    }

    private fun formatAmount(amount: Double): String {
        return if (amount % 1.0 == 0.0) {
            amount.toLong().toString()
        } else {
            String.format("%.2f", amount)
        }
    }
}
