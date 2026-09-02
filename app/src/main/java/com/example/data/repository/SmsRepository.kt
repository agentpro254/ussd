package com.example.data.repository

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.example.data.model.TransactionItem
import com.example.data.model.TransactionType
import com.example.permissions.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

object SmsReaderRepository {

    private const val TAG = "SmsReaderRepository"

    /**
     * Reads SMS from telephony inbox or returns sample M-PESA messages if permission is not granted.
     */
    suspend fun readLocalSmsTransactions(context: Context): List<TransactionItem> = withContext(Dispatchers.IO) {
        val hasPermission = PermissionManager.isReadSmsGranted(context)
        if (!hasPermission) {
            return@withContext getSampleMpesaTransactions()
        }

        val items = mutableListOf<TransactionItem>()
        try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("_id", "address", "body", "date")
            val selection = "address LIKE ? OR address LIKE ? OR address LIKE ? OR body LIKE ?"
            val selectionArgs = arrayOf("%MPESA%", "%M-PESA%", "%SAFARICOM%", "%Confirmed%")

            val cursor: Cursor? = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "date DESC LIMIT 50"
            )

            cursor?.use {
                val idCol = it.getColumnIndex("_id")
                val addressCol = it.getColumnIndex("address")
                val bodyCol = it.getColumnIndex("body")
                val dateCol = it.getColumnIndex("date")

                while (it.moveToNext()) {
                    val id = if (idCol >= 0) it.getString(idCol) else System.currentTimeMillis().toString()
                    val address = if (addressCol >= 0) it.getString(addressCol) else "M-PESA"
                    val body = if (bodyCol >= 0) it.getString(bodyCol) else ""
                    val date = if (dateCol >= 0) it.getLong(dateCol) else System.currentTimeMillis()

                    val parsed = parseMpesaSms(id, address, body, date)
                    if (parsed != null) {
                        items.add(parsed)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading SMS inbox", e)
        }

        if (items.isEmpty()) {
            return@withContext getSampleMpesaTransactions()
        }

        items
    }

    /**
     * Parses real M-PESA SMS text into structured TransactionItem using the dedicated MpesaSmsParser.
     */
    fun parseMpesaSms(id: String, address: String, body: String, timestamp: Long): TransactionItem? {
        val parsed = com.example.data.parser.MpesaSmsParser.parse(body, timestamp) ?: return null
        return com.example.data.parser.MpesaSmsParser.toTransactionItem(id, parsed)
    }

    private fun getSampleMpesaTransactions(): List<TransactionItem> {
        val now = System.currentTimeMillis()
        return listOf(
            TransactionItem(
                id = "sms_1",
                mpesaCode = "QJK918374",
                type = TransactionType.SENT,
                amount = "KES 1,500.00",
                recipientOrSender = "Alice Wanjiku",
                phoneNumber = "0712345678",
                timestamp = now - 15 * 60 * 1000, // 15 mins ago
                summary = "QJK918374 Confirmed. Ksh1,500.00 sent to Alice Wanjiku 0712345678. New M-PESA balance is Ksh4,850.00.",
                fullBody = "QJK918374 Confirmed. Ksh1,500.00 sent to Alice Wanjiku 0712345678 on 28/08 at 09:41 AM. New M-PESA balance is Ksh4,850.00. Transaction cost: Ksh15.00.",
                isVerifiedBySms = true,
                isVerifiedByUssd = true,
                source = "DUAL"
            ),
            TransactionItem(
                id = "sms_2",
                mpesaCode = "RJ482910KP",
                type = TransactionType.RECEIVED,
                amount = "KES 3,200.00",
                recipientOrSender = "David Omondi",
                phoneNumber = "0722987654",
                timestamp = now - 2 * 3600 * 1000, // 2 hours ago
                summary = "RJ482910KP Confirmed. You have received Ksh3,200.00 from David Omondi 0722987654.",
                fullBody = "RJ482910KP Confirmed. You have received Ksh3,200.00 from David Omondi 0722987654 on 28/08 at 07:15 AM. New M-PESA balance is Ksh6,350.00.",
                isVerifiedBySms = true,
                isVerifiedByUssd = false,
                source = "M-PESA SMS"
            ),
            TransactionItem(
                id = "sms_3",
                mpesaCode = "SL83K1920L",
                type = TransactionType.BILL_PAYMENT,
                amount = "KES 850.00",
                recipientOrSender = "Kenya Power (KPLC Prepaid)",
                phoneNumber = "888880",
                timestamp = now - 24 * 3600 * 1000, // 1 day ago
                summary = "SL83K1920L Confirmed. Ksh850.00 paid to KPLC PREPAID 888880. Tokens: 4829-1048-2910-4829.",
                fullBody = "SL83K1920L Confirmed. Ksh850.00 paid to KPLC PREPAID for account 14283920194 on 27/08. Token: 4829-1048-2910-4829. Units: 42.1 kWh.",
                isVerifiedBySms = true,
                isVerifiedByUssd = true,
                source = "DUAL"
            ),
            TransactionItem(
                id = "sms_4",
                mpesaCode = "TK9938102B",
                type = TransactionType.AIRTIME,
                amount = "KES 200.00",
                recipientOrSender = "Airtime for 0712345678",
                phoneNumber = "0712345678",
                timestamp = now - 2 * 24 * 3600 * 1000, // 2 days ago
                summary = "TK9938102B Confirmed. You bought Ksh200.00 of airtime on 26/08 at 18:20.",
                fullBody = "TK9938102B Confirmed. You bought Ksh200.00 of airtime on 26/08 at 18:20. New M-PESA balance is Ksh5,700.00.",
                isVerifiedBySms = true,
                isVerifiedByUssd = false,
                source = "M-PESA SMS"
            )
        )
    }
}
