package com.example.data.model

data class UssdStepItem(
    val stepNumber: Int,
    val label: String,
    val inputHint: String = "",
    val isFinal: Boolean = false
)

data class UssdCodeItem(
    val id: String,
    val name: String,
    val code: String,
    val description: String,
    val icon: String,
    val category: String,
    val colorHex: String,
    val requiresInput: Boolean = false,
    val inputHint: String = "",
    val inputPlaceholder: String = "",
    val isFavorite: Boolean = false,
    val steps: List<UssdStepItem> = emptyList()
) {
    /**
     * Resolves variable placeholders like `*141*voucher#` with real user input.
     */
    fun resolveExecutableCode(userInput: String = ""): String {
        return when {
            code.contains("voucher", ignoreCase = true) -> {
                code.replace("voucher", userInput.trim(), ignoreCase = true)
            }
            code.contains("PIN", ignoreCase = false) -> {
                code.replace("PIN", userInput.trim())
            }
            else -> code
        }
    }
}

data class UssdCategoryItem(
    val id: String,
    val name: String,
    val icon: String,
    val colorHex: String,
    val description: String,
    val codes: List<UssdCodeItem>
) {
    val count: Int
        get() = codes.size
}

object PreloadedUssdRepository {

    val safaricomCategory = UssdCategoryItem(
        id = "cat_safaricom",
        name = "Safaricom",
        icon = "📱",
        colorHex = "#10B981", // Green
        description = "Safaricom Kenya M-PESA, Data & Self-Service USSD directory",
        codes = listOf(
            UssdCodeItem(
                id = "saf_001",
                name = "M-PESA Main Menu",
                code = "*334#",
                description = "Send money, withdraw cash, buy goods, pay bill, Lipa Na M-PESA",
                icon = "💰",
                category = "Safaricom",
                colorHex = "#10B981",
                isFavorite = true,
                steps = listOf(
                    UssdStepItem(1, "Send Money", "Enter recipient phone number"),
                    UssdStepItem(2, "Withdraw Cash", "Enter agent or ATM number"),
                    UssdStepItem(3, "Buy Airtime", "Enter airtime amount"),
                    UssdStepItem(4, "Pay Bill", "Enter business number & account"),
                    UssdStepItem(5, "Lipa Na M-PESA", "Enter till number"),
                    UssdStepItem(6, "My Account", "Check balance / Mini statement")
                )
            ),
            UssdCodeItem(
                id = "saf_002",
                name = "Check Airtime Balance",
                code = "*144#",
                description = "Check your current prepaid airtime and bonus balance",
                icon = "📱",
                category = "Safaricom",
                colorHex = "#10B981",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "saf_003",
                name = "Load Airtime (Voucher)",
                code = "*141*voucher#",
                description = "Load airtime from a scratch card (enter your scratch card PIN)",
                icon = "🎫",
                category = "Safaricom",
                colorHex = "#10B981",
                requiresInput = true,
                inputHint = "Enter Scratch Card Voucher PIN",
                inputPlaceholder = "e.g. 839201948201"
            ),
            UssdCodeItem(
                id = "saf_004",
                name = "Safaricom Self-Service",
                code = "*456#",
                description = "Tunukiwa offers, data bundles, voice bundles, Sambaza",
                icon = "📦",
                category = "Safaricom",
                colorHex = "#10B981"
            ),
            UssdCodeItem(
                id = "saf_005",
                name = "Bonga Points",
                code = "*544#",
                description = "Check Bonga Points balance, buy data bundles & redeem rewards",
                icon = "💎",
                category = "Safaricom",
                colorHex = "#10B981",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "saf_006",
                name = "Prepaid Customer Service",
                code = "*100#",
                description = "Prepaid customer support menu (balance, data products, SIM query)",
                icon = "📞",
                category = "Safaricom",
                colorHex = "#10B981"
            ),
            UssdCodeItem(
                id = "saf_007",
                name = "Postpaid Customer Service",
                code = "*200#",
                description = "Postpaid bill inquiry and customer care service menu",
                icon = "📞",
                category = "Safaricom",
                colorHex = "#10B981"
            ),
            UssdCodeItem(
                id = "saf_008",
                name = "Home Fibre & 5G",
                code = "*400#",
                description = "Safaricom Home Fibre and 5G Home subscriptions & bill payments",
                icon = "🏠",
                category = "Safaricom",
                colorHex = "#10B981"
            ),
            UssdCodeItem(
                id = "saf_009",
                name = "Okoa Jahazi",
                code = "*126*PIN#",
                description = "Emergency airtime advance credit (requires M-PESA PIN)",
                icon = "🆘",
                category = "Safaricom",
                colorHex = "#10B981",
                requiresInput = true,
                inputHint = "Enter M-PESA PIN",
                inputPlaceholder = "4-digit secret PIN"
            ),
            UssdCodeItem(
                id = "saf_010",
                name = "Check SIM Registration",
                code = "*106*2#",
                description = "Check all mobile SIM cards registered against your National ID",
                icon = "🆔",
                category = "Safaricom",
                colorHex = "#10B981"
            )
        )
    )

    val airtelCategory = UssdCategoryItem(
        id = "cat_airtel",
        name = "Airtel",
        icon = "📡",
        colorHex = "#EF4444", // Red
        description = "Airtel Kenya My Airtel, Airtel Money, Data & Airtime USSD codes",
        codes = listOf(
            UssdCodeItem(
                id = "air_001",
                name = "My Airtel Menu",
                code = "*544#",
                description = "Main My Airtel menu (bundles, balance, personalized offers)",
                icon = "📡",
                category = "Airtel",
                colorHex = "#EF4444",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "air_002",
                name = "Check Airtime Balance",
                code = "*133#",
                description = "Check your current Airtel main balance and active bonuses",
                icon = "📱",
                category = "Airtel",
                colorHex = "#EF4444",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "air_003",
                name = "Buy Data Bundles",
                code = "*131#",
                description = "Purchase daily, weekly, monthly 4G/5G data bundles",
                icon = "📦",
                category = "Airtel",
                colorHex = "#EF4444"
            ),
            UssdCodeItem(
                id = "air_004",
                name = "Airtel Money",
                code = "*150#",
                description = "Airtel Money wallet (Tuma Pesa, Lipa Bili, Toa Pesa)",
                icon = "💰",
                category = "Airtel",
                colorHex = "#EF4444",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "air_005",
                name = "Sambaza Airtime",
                code = "*100*1*4#",
                description = "Share and transfer airtime directly with another Airtel line",
                icon = "📤",
                category = "Airtel",
                colorHex = "#EF4444"
            )
        )
    )

    val telkomCategory = UssdCategoryItem(
        id = "cat_telkom",
        name = "Telkom",
        icon = "📶",
        colorHex = "#0284C7", // Blue
        description = "Telkom Kenya Self-Service, T-Kash & Internet Bundles",
        codes = listOf(
            UssdCodeItem(
                id = "tel_001",
                name = "Telkom Self-Service",
                code = "*188#",
                description = "Check airtime balance, buy bundles, manage your SIM line",
                icon = "📶",
                category = "Telkom",
                colorHex = "#0284C7",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "tel_002",
                name = "T-Kash Money",
                code = "*160#",
                description = "T-Kash mobile money main menu (send money, pay utility bills)",
                icon = "💰",
                category = "Telkom",
                colorHex = "#0284C7",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "tel_003",
                name = "Find Your Number",
                code = "*544#",
                description = "Find and display your active Telkom SIM mobile number",
                icon = "🔍",
                category = "Telkom",
                colorHex = "#0284C7"
            ),
            UssdCodeItem(
                id = "tel_004",
                name = "Buy Data Bundles",
                code = "*180#",
                description = "Purchase Telkom 4G internet bundles and night owl passes",
                icon = "📦",
                category = "Telkom",
                colorHex = "#0284C7"
            )
        )
    )

    val bankingCategory = UssdCategoryItem(
        id = "cat_banking",
        name = "Banks",
        icon = "🏦",
        colorHex = "#D97706", // Amber / Gold
        description = "Direct Mobile Banking & Account Management USSD Shortcodes",
        codes = listOf(
            UssdCodeItem(
                id = "bank_001",
                name = "Equity Bank",
                code = "*247#",
                description = "Eazzy Banking - send money, check balance, pay bills, loans",
                icon = "🏦",
                category = "Banks",
                colorHex = "#D97706",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "bank_002",
                name = "KCB Bank",
                code = "*522#",
                description = "KCB Mobile Banking - send money, check balance, KCB M-PESA",
                icon = "🏦",
                category = "Banks",
                colorHex = "#D97706",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "bank_003",
                name = "Co-operative Bank",
                code = "*667#",
                description = "MCo-op Cash - send money, check balance, pay utility bills",
                icon = "🏦",
                category = "Banks",
                colorHex = "#D97706",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "bank_004",
                name = "Family Bank",
                code = "*325#",
                description = "PesaPap - mobile banking services & funds transfer",
                icon = "🏦",
                category = "Banks",
                colorHex = "#D97706"
            ),
            UssdCodeItem(
                id = "bank_005",
                name = "Standard Chartered",
                code = "*722#",
                description = "SC Mobile - secure retail banking and account management",
                icon = "🏦",
                category = "Banks",
                colorHex = "#D97706"
            ),
            UssdCodeItem(
                id = "bank_006",
                name = "NCBA Bank",
                code = "*654#",
                description = "NCBA Mobile Banking (formerly CBA / NIC)",
                icon = "🏦",
                category = "Banks",
                colorHex = "#D97706"
            ),
            UssdCodeItem(
                id = "bank_007",
                name = "Ecobank Kenya",
                code = "*335#",
                description = "Ecobank omni-channel mobile banking services",
                icon = "🏦",
                category = "Banks",
                colorHex = "#D97706"
            )
        )
    )

    val governmentCategory = UssdCategoryItem(
        id = "cat_government",
        name = "Government",
        icon = "🏛️",
        colorHex = "#EA580C", // Orange
        description = "Public Utilities, Health, Tax & Citizen Portal Shortcuts",
        codes = listOf(
            UssdCodeItem(
                id = "gov_001",
                name = "KPLC Power",
                code = "*977#",
                description = "Prepaid electricity token purchase and postpaid power bill balance",
                icon = "💡",
                category = "Government",
                colorHex = "#EA580C",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "gov_002",
                name = "Nairobi Water",
                code = "*888#",
                description = "Nairobi Water bill payment, meter query and statement check",
                icon = "💧",
                category = "Government",
                colorHex = "#EA580C"
            ),
            UssdCodeItem(
                id = "gov_003",
                name = "NHIF / SHA / SHIF",
                code = "*155#",
                description = "Social Health Authority contributions and active status verification",
                icon = "🏥",
                category = "Government",
                colorHex = "#EA580C"
            ),
            UssdCodeItem(
                id = "gov_004",
                name = "KRA M-Service",
                code = "*572#",
                description = "KRA PIN validation, eTIMS compliance, and tax filings",
                icon = "📋",
                category = "Government",
                colorHex = "#EA580C"
            ),
            UssdCodeItem(
                id = "gov_005",
                name = "eCitizen Portal",
                code = "*222#",
                description = "Passport, driving licence, NTSA and unified government payments",
                icon = "🪪",
                category = "Government",
                colorHex = "#EA580C",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "gov_006",
                name = "Nairobi County",
                code = "*235#",
                description = "Parking fees, single business permits and county revenue services",
                icon = "🏛️",
                category = "Government",
                colorHex = "#EA580C"
            ),
            UssdCodeItem(
                id = "gov_007",
                name = "Unclaimed Assets",
                code = "*361#",
                description = "Check if you have unclaimed financial assets (UFAA portal)",
                icon = "💲",
                category = "Government",
                colorHex = "#EA580C"
            ),
            UssdCodeItem(
                id = "gov_008",
                name = "HELB Loan",
                code = "*642#",
                description = "Check Higher Education Loans Board balance & make loan repayments",
                icon = "🎓",
                category = "Government",
                colorHex = "#EA580C"
            ),
            UssdCodeItem(
                id = "gov_009",
                name = "NSSF Contributions",
                code = "*303#",
                description = "Check and manage National Social Security Fund member contributions",
                icon = "🏗️",
                category = "Government",
                colorHex = "#EA580C"
            ),
            UssdCodeItem(
                id = "gov_010",
                name = "CRB Status",
                code = "*433#",
                description = "Check your Credit Reference Bureau listing status and score",
                icon = "📊",
                category = "Government",
                colorHex = "#EA580C"
            )
        )
    )

    val bonusCategory = UssdCategoryItem(
        id = "cat_bonus",
        name = "Bonus",
        icon = "⭐",
        colorHex = "#8B5CF6", // Purple
        description = "Universal Hardware, Device Diagnostic & TIMS shortcuts",
        codes = listOf(
            UssdCodeItem(
                id = "bonus_001",
                name = "IMEI Number",
                code = "*#06#",
                description = "Display your mobile phone's unique hardware IMEI identifier",
                icon = "📱",
                category = "Bonus",
                colorHex = "#8B5CF6",
                isFavorite = true
            ),
            UssdCodeItem(
                id = "bonus_002",
                name = "NTSA TIMS",
                code = "22846",
                description = "Transport Information Management System vehicle inspection shortcut",
                icon = "🚗",
                category = "Bonus",
                colorHex = "#8B5CF6"
            )
        )
    )

    val allCategories: List<UssdCategoryItem> = listOf(
        safaricomCategory,
        airtelCategory,
        telkomCategory,
        bankingCategory,
        governmentCategory,
        bonusCategory
    )

    val allCodes: List<UssdCodeItem> = allCategories.flatMap { it.codes }

    fun searchCodes(query: String): List<UssdCodeItem> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return allCodes
        return allCodes.filter { codeItem ->
            codeItem.name.lowercase().contains(trimmed) ||
            codeItem.code.lowercase().contains(trimmed) ||
            codeItem.description.lowercase().contains(trimmed) ||
            codeItem.category.lowercase().contains(trimmed)
        }
    }

    fun getCategoryById(id: String): UssdCategoryItem? {
        return allCategories.firstOrNull { it.id == id }
    }
}
