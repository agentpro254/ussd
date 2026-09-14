package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.engine.UssdSessionManager
import java.lang.ref.WeakReference

class CodeeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CodeeAccessibility"
        private var instance: CodeeAccessibilityService? = null
        private var ussdCallback: ((String) -> Unit)? = null
        private var inputCallback: ((String) -> Unit)? = null

        fun getInstance(): CodeeAccessibilityService? = instance

        fun setUssdCallback(callback: ((String) -> Unit)?) {
            ussdCallback = callback
        }

        fun setInputCallback(callback: (String) -> Unit) {
            inputCallback = callback
        }

        fun clearCallbacks() {
            ussdCallback = null
            inputCallback = null
        }

        fun sendUssdResponse(response: String): Boolean {
            val service = instance ?: return false
            return service.respondToUssd(response)
        }
    }

    private var isProcessing = false
    private var lastInputNode: WeakReference<AccessibilityNodeInfo>? = null
    private var lastSendButton: WeakReference<AccessibilityNodeInfo>? = null
    private var lastCancelButton: WeakReference<AccessibilityNodeInfo>? = null
    private var lastUssdText = ""
    private var lastUssdTextNormalized = ""
    private var lastUssdTime = 0L
    private val DEBOUNCE_TIME = 2000L // 2 seconds debounce

    private fun normalizeForDebounce(text: String): String {
        return text
            // Strip timestamps (e.g., 12:34:56, 12:34, 2024-01-01)
            .replace(Regex("""\b\d{1,2}:\d{2}(?::\d{2})?\b"""), "")
            .replace(Regex("""\b\d{4}[-/.]\d{1,2}[-/.]\d{1,2}\b"""), "")
            .replace(Regex("""\b\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4}\b"""), "")
            // Strip transaction codes, reference numbers, receipts
            .replace(Regex("""(?i)\b(?:tx|txn|ref|trans|id|code|receipt)\s*[:#]?\s*[A-Z0-9]{5,}\b"""), "")
            .replace(Regex("""\b[A-Z0-9]{8,}\b"""), "")
            // Collapse whitespace
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()
    }

    /**
     * Dynamically detects the active system dialer and telephony package(s)
     * using TelecomManager, PackageManager, ActivityManager, and TelephonyManager.
     */
    fun getDynamicDialerPackages(): Set<String> {
        val packages = mutableSetOf<String>()

        // 1. TelecomManager default dialer & system dialer
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.defaultDialerPackage?.let { if (it.isNotBlank()) packages.add(it) }
                telecomManager?.systemDialerPackage?.let { if (it.isNotBlank()) packages.add(it) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Telecom dialer query error: ${e.message}")
        }

        // 2. PackageManager intent resolution for dial and call
        try {
            val dialIntent = Intent(Intent.ACTION_DIAL)
            val dialResolves = packageManager.queryIntentActivities(dialIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolve in dialResolves) {
                resolve.activityInfo?.packageName?.let { packages.add(it) }
            }
            val allDialResolves = packageManager.queryIntentActivities(dialIntent, 0)
            for (resolve in allDialResolves) {
                resolve.activityInfo?.packageName?.let { packages.add(it) }
            }

            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:123"))
            val callResolves = packageManager.queryIntentActivities(callIntent, 0)
            for (resolve in callResolves) {
                resolve.activityInfo?.packageName?.let { packages.add(it) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "PackageManager dialer query error: ${e.message}")
        }

        // 3. ActivityManager active telephony & dialer processes
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            activityManager?.runningAppProcesses?.forEach { process ->
                val pName = process.processName.lowercase()
                if (pName.contains("phone") ||
                    pName.contains("dialer") ||
                    pName.contains("telecom") ||
                    pName.contains("incall") ||
                    pName.contains("stk")
                ) {
                    process.pkgList?.forEach { packages.add(it) }
                    packages.add(process.processName.substringBefore(":"))
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "ActivityManager running processes query error: ${e.message}")
        }

        // 4. TelephonyManager default system packages
        packages.add("com.android.phone")
        packages.add("android")

        return packages
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            instance = this
            UssdSessionManager.setAccessibilityServiceInstance(this)

            val info = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_FOCUSED or
                        AccessibilityEvent.TYPE_VIEW_SCROLLED or
                        AccessibilityEvent.TYPE_VIEW_LONG_CLICKED

                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                packageNames = null

                flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS

                notificationTimeout = 50
            }
            serviceInfo = info

            Log.d("ACCESS_DEBUG", "SERVICE CONNECTED")
            Log.d("ACCESS_DEBUG", "packageNames=${serviceInfo?.packageNames?.joinToString()}")
            Log.d("ACCESS_DEBUG", "eventTypes=${serviceInfo?.eventTypes}")
            Log.d("ACCESS_DEBUG", "flags=${serviceInfo?.flags}")
            val canRetrieve = (serviceInfo?.capabilities ?: 0) and AccessibilityServiceInfo.CAPABILITY_CAN_RETRIEVE_WINDOW_CONTENT != 0
            Log.d("ACCESS_DEBUG", "canRetrieveWindowContent=$canRetrieve")
            Log.d("ACCESS_DEBUG", "FINAL eventTypes=${serviceInfo?.eventTypes}")
            Log.d("ACCESS_DEBUG", "FINAL flags=${serviceInfo?.flags}")
            Log.d("ACCESS_DEBUG", "FINAL capabilities=${serviceInfo?.capabilities}")
            Log.d(TAG, "✅ Universal Codee Accessibility Service connected with FLAG_RETRIEVE_INTERACTIVE_WINDOWS")
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onServiceConnected", t)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            Log.d("ACCESS_DEBUG", "RAW EVENT pkg=${event.packageName} type=${event.eventType}")

            val eventPkg = event.packageName?.toString() ?: ""
            if (eventPkg == "com.android.settings" ||
                eventPkg == "com.android.systemui" ||
                eventPkg.startsWith("com.google.android.apps.nexuslauncher") ||
                eventPkg.startsWith("com.aistudio") ||
                eventPkg.startsWith("com.example") ||
                eventPkg.contains("codee", ignoreCase = true)) {
                return
            }

            // STEP 1: READ the dialog WHILE IT IS VISIBLE. Do not hide it yet.
            val directSource = event.source
            var capturedText: String? = null
            var capturedNode: AccessibilityNodeInfo? = null

            if (directSource != null) {
                val srcPkg = directSource.packageName?.toString() ?: ""
                if (srcPkg != "com.android.settings" &&
                    srcPkg != "com.android.systemui" &&
                    !srcPkg.startsWith("com.google.android.apps.nexuslauncher") &&
                    !srcPkg.startsWith("com.aistudio") &&
                    !srcPkg.startsWith("com.example") &&
                    !srcPkg.contains("codee", ignoreCase = true)) {
                    val directText = extractUssdText(directSource)
                    if (directText != null && directText.length > 3 && looksLikeUssd(directText)) {
                        capturedText = directText
                        capturedNode = directSource
                        Log.d("ACCESS_DEBUG", "DIRECT SOURCE CAPTURED pkg=$srcPkg text=$directText")
                    }
                }
            }

            if (capturedText == null) {
                val dialog = findUssdDialogInWindows(event.source)
                if (dialog != null) {
                    capturedText = dialog.first
                    capturedNode = dialog.second
                    Log.d("ACCESS_DEBUG", "WINDOW CAPTURED pkg=${capturedNode.packageName} text=$capturedText")
                }
            }

            // STEP 2: Now that we have the text, notify the session manager.
            if (capturedText != null && capturedNode != null) {
                processUssdResponse(capturedText, capturedNode)
            }
        } catch (t: Throwable) {
            Log.e("ACCESS_DEBUG", "Safe handled error in onAccessibilityEvent", t)
        }
    }

    private fun countNumberedMenuLines(text: String): Int {
        val regex = Regex("""(?m)^\s*\d{1,3}\s*[\.\)\-\:]\s*\S+""")
        return regex.findAll(text).count()
    }

    private fun looksLikeUssd(text: String): Boolean {
        val menuLineCount = countNumberedMenuLines(text)
        val hasMenu = menuLineCount >= 3           // require at least 3 consecutive options
        val hasKeywords = hasUssdKeywords(text)
        val hasNonUssd = text.contains("Accessibility") ||
                text.contains("TalkBack") ||
                text.contains("Settings") ||
                text.contains("Magnification") ||
                text.contains("Display size") ||
                text.contains("Shortcut settings") ||
                text.contains("App info")
        return (hasMenu || hasKeywords) && !hasNonUssd
    }

    /**
     * Iterates through all available interactive windows to find the system USSD dialog.
     * Package-agnostic global search across ALL windows without hardcoded dialer allowlists.
     */
    fun findUssdDialogInWindows(eventSource: AccessibilityNodeInfo? = null): Pair<String, AccessibilityNodeInfo>? {
        try {
            val candidateRoots = mutableListOf<AccessibilityNodeInfo>()

            // 1. All interactive windows (this is where the real dialer dialog lives)
            try {
                val allWindows = windows
                Log.d("ACCESS_DEBUG", "TOTAL WINDOWS=${allWindows.size}")
                for ((i, w) in allWindows.withIndex()) {
                    val wRoot = w.root
                    Log.d("ACCESS_DEBUG", "WIN[$i] type=${w.type} active=${w.isActive} focused=${w.isFocused} root=${wRoot?.packageName}/${wRoot?.className} childCount=${wRoot?.childCount}")
                    if (wRoot != null) {
                        candidateRoots.add(wRoot)
                    } else {
                        Log.w("ACCESS_DEBUG", "WIN[$i] root was null — overlay window. Falling back.")
                        rootInActiveWindow?.let { if (!candidateRoots.contains(it)) candidateRoots.add(it) }
                        eventSource?.let { if (!candidateRoots.contains(it)) candidateRoots.add(it) }
                    }
                }
            } catch (e: Exception) {
                Log.e("ACCESS_DEBUG", "WINDOWS LIST FAILED", e)
            }

            // 2. Add active root and event source as fallback
            rootInActiveWindow?.let { if (!candidateRoots.contains(it)) candidateRoots.add(it) }
            eventSource?.let { if (!candidateRoots.contains(it)) candidateRoots.add(it) }

            for (windowRoot in candidateRoots) {
                Log.d("ACCESS_DEBUG", "Window pkg=${windowRoot.packageName} class=${windowRoot.className} childCount=${windowRoot.childCount}")
                val windowPkg = windowRoot.packageName?.toString() ?: ""
                val windowClass = windowRoot.className?.toString() ?: ""

                // Skip settings, systemui, launcher, or our own package
                if (windowPkg == "com.android.settings" ||
                    windowPkg == "com.android.systemui" ||
                    windowPkg.startsWith("com.google.android.apps.nexuslauncher") ||
                    windowPkg.startsWith("com.aistudio") ||
                    windowPkg.startsWith("com.example") ||
                    windowPkg.contains("codee", ignoreCase = true)) {
                    continue
                }

                // Extract the full text via extractUssdText(windowRoot)
                val text = extractUssdText(windowRoot) ?: continue
                if (text.isBlank() || text.length < 3) continue

                // Check acceptance criteria
                val menuLineCount = countNumberedMenuLines(text)
                val hasMenu = menuLineCount >= 3
                val hasKeywords = hasUssdKeywords(text)
                val hasNonUssd = text.contains("Accessibility") ||
                        text.contains("TalkBack") ||
                        text.contains("Settings") ||
                        text.contains("Magnification") ||
                        text.contains("Display size") ||
                        text.contains("Shortcut settings") ||
                        text.contains("App info")

                if (hasMenu) {
                    Log.d("ACCESS_DEBUG", "Numbered menu detected ($menuLineCount lines), accepting as USSD dialog")
                }

                if ((hasMenu || hasKeywords) && !hasNonUssd) {
                    Log.d("ACCESS_DEBUG", "MATCH (universal) pkg=${windowRoot.packageName} class=${windowRoot.className} text=$text")
                    return Pair(text, windowRoot)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not inspect all windows: ${e.message}")
        }
        return null
    }

    /**
     * Reorders our app to the foreground so it covers any system dialer popup
     * while leaving the underlying USSD session intact and alive.
     */
    fun bringAppToFront() {
        try {
            val bringIntent = Intent(this, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(bringIntent)
            Log.d("ACCESS_DEBUG", "📲 Reordered MainActivity to front to cover system dialog")
        } catch (e: Exception) {
            Log.w(TAG, "Could not bring MainActivity to front: ${e.message}")
        }
    }

    private fun hasNumberedMenuLines(text: String): Boolean {
        return text.lines().any { line ->
            val t = line.trim()
            if (t.isEmpty()) return@any false

            // Check for digit-based menu items (e.g. "0 )", "1 )", "0)", "1)", "0.", "1.", "1:", "1 -", "1 ")
            if (t[0].isDigit()) {
                if (t.length >= 2 && (t[1] == ')' || t[1] == '.' || t[1] == ':' || t[1] == '-' || t[1] == ' ')) return@any true
                if (t.length >= 3 && t[1] == ' ' && t[2] == ')') return@any true
                if (t.length >= 3 && t[1].isDigit() && (t[2] == ')' || t[2] == '.' || t[2] == ':' || t[2] == '-' || t[2] == ' ')) return@any true
                if (t.length >= 4 && t[1].isDigit() && t[2] == ' ' && t[3] == ')') return@any true
            }

            // Check for navigation menu items (e.g. "* next", "# next", "*back", "#back", "*", "#")
            if (t.startsWith("*") || t.startsWith("#")) {
                val lower = t.lowercase()
                if (lower.contains("next") || lower.contains("back") || lower.contains("more") || t.length <= 4) {
                    return@any true
                }
            }

            false
        }
    }

    private fun hasUssdKeywords(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("enter") ||
                lower.contains("reply") ||
                lower.contains("select") ||
                lower.contains("press") ||
                lower.contains("confirm") ||
                lower.contains("pin") ||
                lower.contains("session") ||
                lower.contains("invalid") ||
                lower.contains("mmi") ||
                lower.contains("con") ||
                lower.contains("end")
    }

    private fun extractUssdText(node: AccessibilityNodeInfo): String? {
        val textBuilder = StringBuilder()
        extractTextFromNode(node, textBuilder)
        val fullText = textBuilder.toString()

        // Clean up each line while preserving newlines for proper menu/option display
        val lines = fullText.split('\n')
            .map { it.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { it.isNotEmpty() }
        val cleanedText = lines.joinToString("\n")

        if (cleanedText.isEmpty()) return null
        if (cleanedText.length < 3) return null

        return cleanedText
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo, builder: StringBuilder) {
        try {
            val nodeText = node.text?.toString()
            if (!nodeText.isNullOrEmpty()) builder.append(nodeText).append("\n")

            val contentDesc = node.contentDescription?.toString()
            if (!contentDesc.isNullOrEmpty()) builder.append(contentDesc).append("\n")

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i) ?: continue
                extractTextFromNode(child, builder)
            }
        } catch (_: Exception) {
            // Node might have been recycled by system
        }
    }

    private fun processUssdResponse(text: String, rootNode: AccessibilityNodeInfo) {
        val currentTime = System.currentTimeMillis()
        val normalized = normalizeForDebounce(text)

        // Debounce to prevent duplicate processing / loops
        if (normalized == lastUssdTextNormalized && currentTime - lastUssdTime < DEBOUNCE_TIME) {
            Log.d(TAG, "⏳ Debouncing duplicate USSD text: $normalized")
            return
        }

        lastUssdText = text
        lastUssdTextNormalized = normalized
        lastUssdTime = currentTime

        Log.d(TAG, "📱 USSD Response captured: $text")

        // Store node references while visible
        val inputNode = findInputField(rootNode)
        val sendButton = findSendButton(rootNode)
        val cancelButton = findCancelButton(rootNode)

        lastInputNode = if (inputNode != null) WeakReference(inputNode) else null
        lastSendButton = if (sendButton != null) WeakReference(sendButton) else null
        lastCancelButton = if (cancelButton != null) WeakReference(cancelButton) else null

        // Broadcast Intent for system receivers
        try {
            val intent = Intent("USSDRESPONSE").apply {
                putExtra("response", text)
                putExtra("timestamp", currentTime)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send broadcast", e)
        }

        // Trigger in-app callback
        ussdCallback?.invoke(text)

        if (text.length < 3) {
            Log.w("USSD_LOOP", "Ignoring short text: '$text'")
            return
        }

        // Notify session manager (which caches nodes and then brings app to front)
        UssdSessionManager.onUssdDialogCaptured(
            text = text,
            inputNode = inputNode,
            sendButton = sendButton,
            cancelButton = cancelButton
        )
    }

    /**
     * Respond to USSD with user input (e.g., "1", "2", PIN).
     * Injects text into the dialog and clicks 'SEND', then brings the app to the front.
     */
    fun respondToUssd(response: String): Boolean {
        try {
            isProcessing = true

            // STEP 1: Find the input field from lastInputNode (cached earlier)
            val inputField = lastInputNode?.get()
            if (inputField != null) {
                // STEP 2: Set the text
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, response)
                }
                val setTextSuccess = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (!setTextSuccess) {
                    try {
                        inputField.text = response
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not set input field text directly: ${e.message}")
                    }
                }

                // STEP 3: Click send button
                val sendButton = lastSendButton?.get() ?: findSendButton(inputField)
                if (sendButton != null) {
                    val clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "✅ Clicked Send button with response '$response' (success=$clicked)")
                    isProcessing = false
                    // STEP 4: THEN call bringAppToFront()
                    bringAppToFront()
                    return true
                }

                inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                isProcessing = false
                bringAppToFront()
                return true
            }

            // Fallback: Check candidate roots if cached lastInputNode was null
            val candidateRoots = mutableListOf<AccessibilityNodeInfo>()
            rootInActiveWindow?.let { candidateRoots.add(it) }
            try {
                for (window in windows) {
                    val wRoot = window.root
                    if (wRoot != null && !candidateRoots.contains(wRoot)) {
                        candidateRoots.add(wRoot)
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Error listing windows in respondToUssd: ${e.message}")
            }

            for (root in candidateRoots) {
                val foundInput = findInputField(root)
                if (foundInput != null) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, response)
                    }
                    val setTextSuccess = foundInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    if (!setTextSuccess) {
                        try {
                            foundInput.text = response
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not set input field text directly: ${e.message}")
                        }
                    }

                    val sendButton = findSendButton(root)
                    if (sendButton != null) {
                        val clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "✅ Clicked Send button with response '$response' (success=$clicked)")
                        isProcessing = false
                        bringAppToFront()
                        return true
                    }

                    foundInput.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    isProcessing = false
                    bringAppToFront()
                    return true
                }

                // Fallback: Try to find clickable buttons with matching text
                val buttons = findButtonsByText(root, response)
                for (button in buttons) {
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "✅ Clicked button: $response")
                    isProcessing = false
                    bringAppToFront()
                    return true
                }
            }

            isProcessing = false
            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending USSD response: ${e.message}")
            isProcessing = false
            return false
        }
    }

    fun submitTextToActiveDialog(node: AccessibilityNodeInfo?, text: String, sendButton: AccessibilityNodeInfo?): Boolean {
        if (node != null) {
            lastInputNode = WeakReference(node)
        }
        if (sendButton != null) {
            lastSendButton = WeakReference(sendButton)
        }
        return respondToUssd(text)
    }

    /**
     * [DO NOT CLICK CANCEL]: Preserves the background dialog to avoid terminating the carrier session.
     */
    fun dismissActiveDialog(cancelButton: AccessibilityNodeInfo?): Boolean {
        Log.d(TAG, "🔒 Preserving system USSD dialog in background (not clicking cancel to keep session alive)")
        bringAppToFront()
        return true
    }

    private fun findInputField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return try {
            val className = node.className?.toString() ?: ""
            if (node.isEditable || className.contains("EditText", ignoreCase = true)) {
                return node
            }

            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            if ((viewId.contains("input") || viewId.contains("edit")) && !className.contains("TextView", ignoreCase = true)) {
                return node
            }

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findInputField(child)
                    if (result != null) return result
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return try {
            val text = node.text?.toString()?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val combined = "$text $contentDesc"

            if (combined.contains("send") ||
                combined.contains("ok") ||
                combined.contains("confirm") ||
                combined.contains("submit") ||
                combined.contains("proceed") ||
                combined.contains("reply") ||
                combined.contains("yes")
            ) {
                return node
            }

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findSendButton(child)
                    if (result != null) return result
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun findCancelButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return try {
            val text = node.text?.toString()?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val combined = "$text $contentDesc"

            if (combined.contains("cancel") ||
                combined.contains("dismiss") ||
                combined.contains("close") ||
                combined.contains("back")
            ) {
                return node
            }

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findCancelButton(child)
                    if (result != null) return result
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun findButtonsByText(node: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        try {
            val nodeText = node.text?.toString() ?: ""
            if (node.isClickable && nodeText.equals(text, ignoreCase = true)) {
                result.add(node)
            }

            val count = node.childCount
            for (i in 0 until count) {
                val child = node.getChild(i)
                if (child != null) {
                    result.addAll(findButtonsByText(child, text))
                }
            }
        } catch (_: Exception) {
            // Safe fallback
        }
        return result
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        clearCallbacks()
        UssdSessionManager.setAccessibilityServiceInstance(null)
        Log.d(TAG, "Accessibility Service destroyed")
    }
}
