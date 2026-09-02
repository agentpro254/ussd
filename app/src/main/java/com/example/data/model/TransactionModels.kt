package com.example.data.model

enum class TransactionType {
    SENT,
    RECEIVED,
    AIRTIME,
    BILL_PAYMENT,
    WITHDRAWAL,
    BALANCE,
    OTHER
}

data class TransactionItem(
    val id: String,
    val mpesaCode: String,
    val type: TransactionType,
    val amount: String,
    val recipientOrSender: String,
    val phoneNumber: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val summary: String,
    val fullBody: String = "",
    val isVerifiedBySms: Boolean = false,
    val isVerifiedByUssd: Boolean = false,
    val source: String = "M-PESA" // "USSD", "SMS", "DUAL"
) {
    val displayTitle: String
        get() = when (type) {
            TransactionType.SENT -> "Money Sent"
            TransactionType.RECEIVED -> "Money Received"
            TransactionType.AIRTIME -> "Airtime Purchase"
            TransactionType.BILL_PAYMENT -> "Bill Payment"
            TransactionType.WITHDRAWAL -> "Cash Withdrawal"
            TransactionType.BALANCE -> "Balance Check"
            TransactionType.OTHER -> "Transaction"
        }
}

data class SmartFlowResult(
    val amount: String,
    val recipient: String? = null,
    val sender: String? = null,
    val phoneNumber: String? = null,
    val mpesaCode: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val type: TransactionType = TransactionType.SENT,
    val summary: String = "",
    val rawResponse: String = ""
)

data class TransactionDetails(
    val type: TransactionType,
    val amount: String,
    val recipient: String? = null,
    val sender: String? = null,
    val phoneNumber: String? = null,
    val transactionCode: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
