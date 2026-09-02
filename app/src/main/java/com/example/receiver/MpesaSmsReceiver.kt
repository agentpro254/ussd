package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.example.data.parser.MpesaSmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Real-time SMS Receiver for intercepting and parsing incoming M-PESA confirmations inside the app.
 * Automatically extracts the transaction code, amount, sender / receiver names, and updates state.
 */
class MpesaSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            try {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                val fullBody = StringBuilder()
                var senderAddress = ""
                var timestamp = System.currentTimeMillis()

                for (sms in messages) {
                    fullBody.append(sms.messageBody)
                    senderAddress = sms.originatingAddress ?: "M-PESA"
                    timestamp = sms.timestampMillis
                }

                val bodyText = fullBody.toString()
                if (bodyText.contains("Confirmed", ignoreCase = true) ||
                    senderAddress.contains("MPESA", ignoreCase = true) ||
                    senderAddress.contains("SAFARICOM", ignoreCase = true)
                ) {
                    val parsed = MpesaSmsParser.parse(bodyText, timestamp)
                    if (parsed != null) {
                        Log.d(
                            "MpesaSmsReceiver",
                            "Parsed incoming M-PESA SMS: Code=${parsed.transactionCode}, Type=${parsed.type}, " +
                                    "Amount=${parsed.amount}, Sender=${parsed.senderName}, Receiver=${parsed.receiverName}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("MpesaSmsReceiver", "Error processing received SMS", e)
            }
        }
    }
}
