package com.example.engine

sealed interface ValidationResult {
    object Valid : ValidationResult
    data class Blocked(val reason: String) : ValidationResult
    data class PinRequired(val message: String) : ValidationResult
    data class Invalid(val reason: String) : ValidationResult
}

object CodeValidator {

    // Known problematic or protected codes that need special handling
    private val blockedCodes = setOf(
        "#31#", // Caller ID suppression
        "*#06#", // IMEI query - handled by OS dialog only
        "*#*#4636#*#*" // Testing menu
    )

    // Codes that inherently lead directly to a PIN / financial prompt
    private val pinRequiredPrefixes = listOf(
        "*334*1*", // M-PESA Send Money with params
        "*334*2*", // M-PESA Withdraw
        "*334*3*", // M-PESA Buy Airtime
        "*334*4*", // M-PESA Paybill
        "*185*1*", // Airtel Money Send
        "*185*2*"  // Airtel Money Airtime
    )

    fun validateCode(rawCode: String): ValidationResult {
        val clean = rawCode.trim()
        if (clean.isEmpty()) {
            return ValidationResult.Invalid("Please enter a valid USSD code")
        }

        // Check if code is explicitly blocked
        if (blockedCodes.contains(clean)) {
            return ValidationResult.Blocked(
                "The code $clean is a system-restricted string. Please use standard service codes."
            )
        }

        // Check if code directly contains prefilled PIN payload or routes to sensitive PIN
        if (pinRequiredPrefixes.any { clean.startsWith(it) }) {
            return ValidationResult.PinRequired(
                "This USSD operation requires a security PIN for completion. You will be prompted securely."
            )
        }

        // Check standard USSD format (*123#, *123*1#, 123, etc.)
        val isStandardUssd = clean.matches(Regex("""^\*[\d*#]+#$""")) || clean.matches(Regex("""^\d+$"""))
        if (!isStandardUssd) {
            return ValidationResult.Invalid(
                "Invalid USSD format. Expected format like *123# or *544#."
            )
        }

        return ValidationResult.Valid
    }
}
