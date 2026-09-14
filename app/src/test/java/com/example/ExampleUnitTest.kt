package com.example

import com.example.data.model.TransactionType
import com.example.data.model.UssdInputType
import com.example.data.model.UssdResponseType
import com.example.data.parser.TransactionParser
import com.example.data.parser.UssdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testSentMoneyTransactionParsing() {
        val raw = "QK93JH788X Confirmed. KES 500.00 sent to John Doe 0712345678 on 28/8/26 at 2:30 PM. New M-PESA balance is KES 4,500.00."
        val parsed = TransactionParser.parseTransaction(raw)
        assertNotNull(parsed)
        assertEquals(TransactionType.SENT, parsed!!.type)
        assertEquals("KES 500.00", parsed.amount)
        assertEquals("John Doe", parsed.recipient)
        assertEquals("0712345678", parsed.phoneNumber)
        assertEquals("QK93JH788X", parsed.transactionCode)
    }

    @Test
    fun testReceivedMoneyTransactionParsing() {
        val raw = "ABC98765XYZ Confirmed. You have received KES 1,000.00 from Mary Smith 0723456789 on 28/8/26 at 12:15 PM."
        val parsed = TransactionParser.parseTransaction(raw)
        assertNotNull(parsed)
        assertEquals(TransactionType.RECEIVED, parsed!!.type)
        assertEquals("KES 1,000.00", parsed.amount)
        assertEquals("Mary Smith", parsed.sender)
        assertEquals("0723456789", parsed.phoneNumber)
        assertEquals("ABC98765XYZ", parsed.transactionCode)
    }

    @Test
    fun testRawReceivedKshParsing() {
        val raw = "You have received Ksh 1,000.00 from MARY WANJIKU 0723456789..."
        val parsed = TransactionParser.parseTransaction(raw)
        assertNotNull(parsed)
        assertEquals(TransactionType.RECEIVED, parsed!!.type)
        assertEquals("KES 1,000.00", parsed.amount)
        assertEquals("Mary Wanjiku", parsed.sender)
        assertEquals("0723456789", parsed.phoneNumber)
    }

    @Test
    fun testMenuParsing() {
        val rawUssd = """
            Welcome to MoMo:
            1. Send Money
            2. Buy Airtime
            3. Pay Utility Bills
            0. Back
        """.trimIndent()

        val parsed = UssdParser.parse(rawUssd)
        assertEquals(UssdResponseType.MENU, parsed.type)
        assertEquals(4, parsed.options.size)
        assertEquals("1", parsed.options[0].id)
        assertEquals("Send Money", parsed.options[0].label)
        assertEquals("0", parsed.options[3].id)
        assertTrue(parsed.options[3].isBack)
    }

    @Test
    fun testPinPromptParsing() {
        val rawUssd = "Enter your 4-digit secret PIN to authorize transfer:"
        val parsed = UssdParser.parse(rawUssd)
        assertEquals(UssdResponseType.PIN_REQUEST, parsed.type)
        assertEquals(UssdInputType.PIN, parsed.inputType)
    }

    @Test
    fun testAmountPromptParsing() {
        val rawUssd = "Enter amount in USD to send to 0772123456:"
        val parsed = UssdParser.parse(rawUssd)
        assertEquals(UssdResponseType.INPUT_PROMPT, parsed.type)
        assertEquals(UssdInputType.AMOUNT, parsed.inputType)
    }

    @Test
    fun testSuccessBalanceParsing() {
        val rawUssd = "Your account balance is $245.80. Thank you for using our service."
        val parsed = UssdParser.parse(rawUssd)
        assertEquals(UssdResponseType.SUCCESS_RESULT, parsed.type)
        assertTrue(parsed.isTerminal)
        assertTrue(parsed.isSuccess)
    }

    @Test
    fun testErrorParsing() {
        val rawUssd = "Connection problem or invalid MMI code. Please try again later."
        val parsed = UssdParser.parse(rawUssd)
        assertEquals(UssdResponseType.ERROR_RESULT, parsed.type)
        assertTrue(parsed.isTerminal)
        assertFalse(parsed.isSuccess)
    }

    @Test
    fun testConfirmationParsing() {
        val rawUssd = "Confirm transfer of $50 to John Doe (0712345678)? 1: Yes, 2: Cancel"
        val parsed = UssdParser.parse(rawUssd)
        assertEquals(UssdResponseType.CONFIRMATION, parsed.type)
        assertEquals(2, parsed.options.size)
    }

    @Test
    fun testNavigationDetector() {
        val rawUssd = """
            CON M-PESA Menu
            1. Send Money
            2. Withdraw Cash
            3. Buy Airtime
            98. Back
            99. Next
            0. Main Menu
        """.trimIndent()

        val parsed = UssdParser.parse(rawUssd)
        val nav = com.example.data.parser.NavigationDetector.detectNavigationOptions(parsed.options)

        assertEquals(3, nav.regularOptions.size)
        assertEquals("Send Money", nav.regularOptions[0].label)
        assertEquals("Withdraw Cash", nav.regularOptions[1].label)
        assertEquals("Buy Airtime", nav.regularOptions[2].label)

        assertTrue(nav.hasNavigation)
        assertEquals("98", nav.back?.id)
        assertEquals("99", nav.next?.id)
        assertEquals("0", nav.main?.id)
    }

    @Test
    fun testRealMpesaFormat1SentMoney() {
        val parser = com.example.engine.SmsParser()
        val text = """
            Dear VAIDAH OTIWU,
            you have sent Ksh.
            100.0 to HEZRON O
            HELLEN for 40069635
            on 08/27/2026 at
            11:04:05. MPESA Ref.
            UHR6M4WZ3I.
        """.trimIndent()
        val parsed = parser.parseSms(text, "MPESA")
        assertEquals(com.example.data.model.SmsType.MPESA_SENT, parsed.type)
        assertEquals("Ksh 100.0", parsed.amount)
        assertEquals("HEZRON O\nHELLEN", parsed.recipient)
        assertEquals("40069635", parsed.phoneNumber)
        assertEquals("UHR6M4WZ3I", parsed.transactionCode)
    }

    @Test
    fun testRealMpesaFormat2ReceivedMoney() {
        val parser = com.example.engine.SmsParser()
        val text = """
            UI2NG57BB3
            Confirmed.You have received Ksh10.00 from Hellen Odinga
            0720***813 on 2/9/26 at 3:50 PM
        """.trimIndent()
        val parsed = parser.parseSms(text, "MPESA")
        assertEquals(com.example.data.model.SmsType.MPESA_RECEIVED, parsed.type)
        assertEquals("Ksh 10.00", parsed.amount)
        assertEquals("Hellen Odinga", parsed.sender)
        assertEquals("0720***813", parsed.phoneNumber)
        assertEquals("UI2NG57BB3", parsed.transactionCode)
    }

    @Test
    fun testRealMpesaFormat3PaidTo() {
        val parser = com.example.engine.SmsParser()
        val text = """
            UI26M5NNUX
            Confirmed.
            Ksh60.00 paid to
            MOSES NJUGUNA.
            on 2/9/26 at 3:03
            PM.
        """.trimIndent()
        val parsed = parser.parseSms(text, "MPESA")
        assertEquals(com.example.data.model.SmsType.MPESA_PAID, parsed.type)
        assertEquals("Ksh 60.00", parsed.amount)
        assertEquals("MOSES NJUGUNA", parsed.recipient)
        assertEquals("UI26M5NNUX", parsed.transactionCode)
    }

    @Test
    fun testRealMpesaFormat4ReceivedMoney() {
        val parser = com.example.engine.SmsParser()
        val text = """
            UI18C50ZXT
            Confirmed.You have received Ksh50.00 from CELESTINE UNGUKU
            0141***004 on 1/9/26 at 8:10 PM
        """.trimIndent()
        val parsed = parser.parseSms(text, "MPESA")
        assertEquals(com.example.data.model.SmsType.MPESA_RECEIVED, parsed.type)
        assertEquals("Ksh 50.00", parsed.amount)
        assertEquals("CELESTINE UNGUKU", parsed.sender)
        assertEquals("0141***004", parsed.phoneNumber)
        assertEquals("UI18C50ZXT", parsed.transactionCode)
    }

    @Test
    fun testRealCarrierUssdModalFromScreenshot() {
        val rawUssd = """
            0 ) Smarta 1000
            1 ) 5GB @Ksh 250, 7 Days
            2 ) 7days@Ksh80, 220Mins AnyNET
            3 ) Amazing Data
            4 ) Hourly Bundle
            5 ) Tubonge + ALLNET
            6 ) SMARTA
            7 ) Kopa
            * next
        """.trimIndent()

        val parsed = UssdParser.parse(rawUssd)
        assertEquals(UssdResponseType.MENU, parsed.type)
        assertEquals(9, parsed.options.size)
        assertEquals("0", parsed.options[0].id)
        assertEquals("Smarta 1000", parsed.options[0].label)
        assertEquals("1", parsed.options[1].id)
        assertEquals("5GB @Ksh 250, 7 Days", parsed.options[1].label)
        assertEquals("*", parsed.options[8].id)
        assertEquals("next", parsed.options[8].label)
    }

    @Test
    fun testStructuredMenuOptionParsing() {
        val rawUssd = """
            0 ) Smarta 1000
            1 ) 5GB @Ksh 250, 7 Days
            2 ) 7days@Ksh80, 220Mins AnyNET
            * next
        """.trimIndent()

        val structured = UssdParser.parseMenuOptions(rawUssd)
        assertEquals(4, structured.size)
        assertEquals("0", structured[0].number)
        assertEquals("Smarta 1000", structured[0].label)
        assertEquals("1", structured[1].number)
        assertEquals("5GB @Ksh 250, 7 Days", structured[1].label)
        assertEquals("*", structured[3].number)
        assertEquals("next", structured[3].label)
    }
}
