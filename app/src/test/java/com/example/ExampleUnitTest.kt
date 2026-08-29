package com.example

import com.example.data.model.UssdInputType
import com.example.data.model.UssdResponseType
import com.example.data.parser.UssdParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testMenuParsing() {
        val rawUssd = """
            Welcome to MoMo:
            1. Send Money
            2. Buy Airtime
            3. Pay Utility Bills
            0. Exit
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
        assertEquals(UssdResponseType.INPUT_PROMPT, parsed.type)
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
}
