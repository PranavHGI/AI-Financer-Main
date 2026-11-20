package com.example.aifinancerfree.data.model

import com.google.gson.annotations.SerializedName

data class VoiceCommandRequest(
    @SerializedName("transcript") val transcript: String,
    @SerializedName("client_timestamp") val clientTimestamp: Double? = null,
    @SerializedName("account_preference") val accountPreference: String? = "Cash"
)

data class VoiceActionEntity(
    @SerializedName("action_type") val actionType: String,
    @SerializedName("transaction_id") val transactionId: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("merchant") val merchant: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("account") val account: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("executed") val executed: Boolean = false
)

data class VoiceCommandResponse(
    @SerializedName("action_type") val actionType: String,
    @SerializedName("spoken_text") val spokenText: String,
    @SerializedName("display_text") val displayText: String,
    @SerializedName("entity") val entity: VoiceActionEntity? = null,
    @SerializedName("data") val data: Map<String, Any>? = null
)

sealed class VoiceActionResult {
    data class Success(
        val response: VoiceCommandResponse,
        val localTransactionId: Long? = null,
        val canUndo: Boolean = false
    ) : VoiceActionResult()

    data class Error(
        val message: String,
        val spokenFallback: String = "Sorry, I had trouble processing that command."
    ) : VoiceActionResult()
}
