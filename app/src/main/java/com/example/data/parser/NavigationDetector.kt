package com.example.data.parser

import com.example.data.model.UssdMenuOption

/**
 * Data class representing detected navigation controls extracted from USSD menu options.
 */
data class NavigationOptions(
    val back: UssdMenuOption? = null,
    val next: UssdMenuOption? = null,
    val exit: UssdMenuOption? = null,
    val main: UssdMenuOption? = null,
    val regularOptions: List<UssdMenuOption> = emptyList()
) {
    val hasNavigation: Boolean
        get() = back != null || next != null || exit != null || main != null

    val allNavOptions: List<UssdMenuOption>
        get() = listOfNotNull(back, main, next, exit)
}

/**
 * Smart Navigation Detector for USSD Menus.
 * Identifies telco pagination, return paths, main menu shortcuts, and exit actions
 * based on keyword linguistics (EN, SW, FR) and standard telecom dial codes (98, 99, 0, 00, #, *).
 */
object NavigationDetector {

    // Common navigation keywords and their multilingual mappings (English, Swahili, French)
    private val BACK_PATTERNS = listOf(
        "back", "previous", "return", "go back", "prev", "step back",
        "nyuma", "rudi", "nyuma kidogo", "rudi nyuma",
        "précédent", "retour", "revenir"
    )

    private val NEXT_PATTERNS = listOf(
        "next", "continue", "more", "forward", "further", "next page", "more options",
        "mbele", "endelea", "zaidi", "kurasa inayofuata",
        "suivant", "suite", "continuer", "plus"
    )

    private val MAIN_PATTERNS = listOf(
        "main menu", "menu kuu", "main", "home", "home menu", "mwanzo",
        "menu principal", "accueil", "root menu"
    )

    private val EXIT_PATTERNS = listOf(
        "exit", "cancel", "end", "quit", "close", "stop", "dismiss", "abort",
        "toka", "futa", "sitisha", "kufunga", "ondoka",
        "quitter", "annuler", "sortir", "fermer"
    )

    private fun matchesWordPattern(text: String, patterns: List<String>): Boolean {
        val lower = text.lowercase().trim()
        return patterns.any { pattern ->
            if (pattern.contains(" ")) {
                lower.contains(pattern)
            } else {
                Regex("""\b${Regex.escape(pattern)}\b""").containsMatchIn(lower)
            }
        }
    }

    /**
     * Inspects a list of options and separates regular selectable items from navigation controls.
     */
    fun detectNavigationOptions(options: List<UssdMenuOption>): NavigationOptions {
        var backOption: UssdMenuOption? = null
        var nextOption: UssdMenuOption? = null
        var exitOption: UssdMenuOption? = null
        var mainOption: UssdMenuOption? = null
        val regularOptions = mutableListOf<UssdMenuOption>()

        for (option in options) {
            val text = option.label.lowercase().trim()
            val number = option.id.trim()

            when {
                // Check Main Menu first if labelled as main
                isMainOption(text, number) -> {
                    mainOption = option
                }
                // Check Back
                isBackOption(text, number) -> {
                    backOption = option.copy(isBack = true)
                }
                // Check Next / More
                isNextOption(text, number) -> {
                    nextOption = option.copy(isNext = true)
                }
                // Check Exit / Cancel
                isExitOption(text, number) -> {
                    exitOption = option
                }
                else -> {
                    regularOptions.add(option)
                }
            }
        }

        return NavigationOptions(
            back = backOption,
            next = nextOption,
            exit = exitOption,
            main = mainOption,
            regularOptions = regularOptions
        )
    }

    fun isBackOption(text: String, number: String): Boolean {
        val hasBackKeyword = matchesWordPattern(text, BACK_PATTERNS)
        val isCommonBackNumber = number == "98" || (number == "0" && (hasBackKeyword || text.contains("back") || text.contains("rudi")))
        return hasBackKeyword || number == "98" || isCommonBackNumber
    }

    fun isNextOption(text: String, number: String): Boolean {
        val hasNextKeyword = matchesWordPattern(text, NEXT_PATTERNS)
        val isCommonNextNumber = number == "99" || number == "#" || number == "*"
        return hasNextKeyword || isCommonNextNumber
    }

    fun isExitOption(text: String, number: String): Boolean {
        val hasExitKeyword = matchesWordPattern(text, EXIT_PATTERNS)
        val isCommonExitNumber = number == "00" || (number == "0" && (hasExitKeyword || text.contains("exit") || text.contains("cancel") || text.contains("toka") || text.contains("futa") || text.contains("sitisha")))
        return hasExitKeyword || isCommonExitNumber
    }

    fun isMainOption(text: String, number: String): Boolean {
        val hasMainKeyword = matchesWordPattern(text, MAIN_PATTERNS)
        val isCommonMainNumber = (number == "0" || number == "00") && hasMainKeyword
        return hasMainKeyword || isCommonMainNumber
    }
}
