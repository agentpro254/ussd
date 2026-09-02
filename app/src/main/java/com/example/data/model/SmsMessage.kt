package com.example.data.model

data class SmsMessage(
    val id: String,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val isRead: Boolean = false,
    val isMpesa: Boolean = false,
    val isBank: Boolean = false,
    val isGovernment: Boolean = false
)

data class ParsedSms(
    val raw: SmsMessage,
    val type: SmsType,
    val amount: String? = null,
    val recipient: String? = null,
    val sender: String? = null,
    val phoneNumber: String? = null,
    val transactionCode: String? = null,
    val balance: String? = null,
    val fee: String? = null,
    val dateTime: String? = null,
    val isConfirmed: Boolean = false
)

enum class SmsType {
    MPESA_SENT,
    MPESA_RECEIVED,
    MPESA_AIRTIME,
    MPESA_BILL_PAYMENT,
    MPESA_WITHDRAWAL,
    BANK_ALERT,
    GOVERNMENT,
    OTHER
}
