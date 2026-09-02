package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.engine.UssdSessionManager

class CodeeAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CodeeAccessibility"
        private var instance: CodeeAccessibilityService? = null
        private var ussdCallback: ((String) -> Unit)? = null
        private var inputCallback: ((String) -> Unit)? = null

        // Comprehensive HashSet of global OEM dialers, AOSP, and OEM UI packages
        private val DIALER_PACKAGES = hashSetOf(
            // Google / Pixel / AOSP
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.phone",
            "com.android.incallui",
            "com.android.server.telecom",

            // Samsung
            "com.samsung.android.dialer",
            "com.samsung.android.incallui",

            // Xiaomi, Redmi, POCO
            "com.xiaomi.contacts",

            // Oppo, Realme, OnePlus
            "com.oplus.dialer",
            "com.coloros.dialer",
            "com.heytap.dialer",
            "com.oneplus.dialer",

            // Huawei & Honor
            "com.huawei.incallui",
            "com.hihonor.incallui",

            // Transsion (Tecno, Infinix, itel)
            "com.transsion.dialer",
            "com.transsion.telecom",
            "com.itel.dialer",
            "com.infinix.dialer",
            "com.sh.smart.caller"
        )

        fun getInstance(): CodeeAccessibilityService? = instance

        fun setUssdCallback(callback: (String) -> Unit) {
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
    private var lastUssdText = ""
    private var lastUssdTime = 0L
    private val DEBOUNCE_TIME = 1000L // 1 second debounce

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        UssdSessionManager.setAccessibilityServiceInstance(this)

        // Configure the accessibility service to monitor all window and content changes
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                    AccessibilityEvent.TYPE_VIEW_CLICKED

            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC

            // Leave packageNames null so all OEM dialers are monitored without strict filtering
            packageNames = null

            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS

            notificationTimeout = 100
        }

        serviceInfo = info
        Log.d(TAG, "✅ Universal Codee Accessibility Service connected successfully")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Prevent processing if already working on something
        if (isProcessing) return

        // 1. Try event source
        val eventSource = event.source
        if (eventSource != null && isUssdDialog(eventSource)) {
            val ussdText = extractUssdText(eventSource)
            if (!ussdText.isNullOrEmpty()) {
                processUssdResponse(ussdText, eventSource)
                return
            }
        }

        // 2. Try root in active window
        val root = rootInActiveWindow
        if (root != null && isUssdDialog(root)) {
            val ussdText = extractUssdText(root)
            if (!ussdText.isNullOrEmpty()) {
                processUssdResponse(ussdText, root)
                return
            }
        }

        // 3. Search interactive background windows (for hidden dialing strategy)
        try {
            for (window in windows) {
                val windowRoot = window.root ?: continue
                if (isUssdDialog(windowRoot)) {
                    val ussdText = extractUssdText(windowRoot)
                    if (!ussdText.isNullOrEmpty()) {
                        processUssdResponse(ussdText, windowRoot)
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Could not inspect all windows: ${e.message}")
        }
    }

    private fun isUssdDialog(node: AccessibilityNodeInfo): Boolean {
        val packageName = node.packageName?.toString() ?: ""

        // 1. Check if it matches known global dialers or keyword heuristics
        val isKnownDialer = DIALER_PACKAGES.contains(packageName) ||
                packageName.contains("phone", ignoreCase = true) ||
                packageName.contains("dialer", ignoreCase = true) ||
                packageName.contains("incall", ignoreCase = true) ||
                packageName.contains("telecom", ignoreCase = true)

        // 2. Generic AlertDialog fallback (catches custom OEM dialog popups regardless of brand)
        val className = node.className?.toString() ?: ""
        val isDialog = className.contains("AlertDialog", ignoreCase = true) ||
                className.contains("Dialog", ignoreCase = true) ||
                className.contains("Alert", ignoreCase = true)

        // 3. Check for typical USSD content indicators
        val text = node.text?.toString() ?: ""
        val hasUssdContent = text.contains("CON", ignoreCase = true) ||
                text.contains("END", ignoreCase = true) ||
                text.contains("*") ||
                text.contains("#") ||
                text.contains("M-PESA", ignoreCase = true) ||
                text.contains("Safaricom", ignoreCase = true) ||
                text.contains("Airtel", ignoreCase = true) ||
                text.contains("Telkom", ignoreCase = true) ||
                text.contains("balance", ignoreCase = true) ||
                text.contains("PIN", ignoreCase = true) ||
                text.contains("Reply", ignoreCase = true)

        val hasText = !node.text.isNullOrEmpty() || node.childCount > 0

        // If session is active, be extra responsive
        if (UssdSessionManager.isSessionActive() && (isDialog || hasUssdContent || isKnownDialer)) {
            return true
        }

        return (isKnownDialer && (hasUssdContent || hasText)) || (isDialog && hasUssdContent) || (isDialog && isKnownDialer)
    }

    private fun extractUssdText(node: AccessibilityNodeInfo): String? {
        val textBuilder = StringBuilder()
        extractTextFromNode(node, textBuilder)
        val fullText = textBuilder.toString()

        // Clean up the text
        val cleanedText = fullText
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleanedText.isEmpty()) return null
        if (cleanedText.length < 3) return null

        return cleanedText
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo, builder: StringBuilder) {
        // Add this node's text
        val nodeText = node.text?.toString()
        if (!nodeText.isNullOrEmpty()) {
            val className = node.className?.toString() ?: ""
            if (!className.contains("Button", ignoreCase = true) && !className.contains("EditText", ignoreCase = true)) {
                builder.append(nodeText).append("\n")
            }
        }

        // Recursively get text from children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                extractTextFromNode(child, builder)
                child.recycle()
            }
        }
    }

    private fun processUssdResponse(text: String, rootNode: AccessibilityNodeInfo) {
        val currentTime = System.currentTimeMillis()

        // Debounce to prevent duplicate processing / loops
        if (text == lastUssdText && currentTime - lastUssdTime < DEBOUNCE_TIME) {
            Log.d(TAG, "⏳ Debouncing duplicate USSD text")
            return
        }

        lastUssdText = text
        lastUssdTime = currentTime

        Log.d(TAG, "📱 USSD Response captured: $text")

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

        // Find input nodes and notify session manager if active
        val inputNode = findInputField(rootNode)
        val sendButton = findSendButton(rootNode)
        val cancelButton = findCancelButton(rootNode)

        UssdSessionManager.onUssdDialogCaptured(
            text = text,
            inputNode = inputNode,
            sendButton = sendButton,
            cancelButton = cancelButton
        )
    }

    /**
     * Respond to USSD with user input (e.g., "1", "2", PIN).
     * Called when user taps an option or submits text.
     */
    fun respondToUssd(response: String): Boolean {
        try {
            isProcessing = true
            
            // Gather candidate roots: active root first, followed by all interactive window roots
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
                // Find input field
                val inputField = findInputField(root)
                if (inputField != null) {
                    // Set text via Accessibility action or text property
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

                    // Find and click send button
                    val sendButton = findSendButton(root)
                    if (sendButton != null) {
                        val clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        Log.d(TAG, "✅ Clicked Send button with response '$response' (success=$clicked)")
                        isProcessing = false
                        return true
                    }

                    // If no send button, try clicking input field or submit
                    inputField.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    isProcessing = false
                    return true
                }

                // Fallback: Try to find clickable buttons with matching text
                val buttons = findButtonsByText(root, response)
                for (button in buttons) {
                    button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "✅ Clicked button: $response")
                    isProcessing = false
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
        return respondToUssd(text)
    }

    fun dismissActiveDialog(cancelButton: AccessibilityNodeInfo?): Boolean {
        return try {
            if (cancelButton != null) {
                cancelButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                val root = rootInActiveWindow
                if (root != null) {
                    val cancel = findCancelButton(root)
                    cancel?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing dialog", e)
            false
        }
    }

    private fun findInputField(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.className?.toString() ?: ""
        if (className.contains("EditText", ignoreCase = true) || className.contains("Input", ignoreCase = true) || node.isEditable) {
            return node
        }

        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.contains("input") || viewId.contains("edit") || viewId.contains("text")) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findInputField(child)
                if (result != null) return result
            }
        }

        return null
    }

    private fun findSendButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
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

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findSendButton(child)
                if (result != null) return result
            }
        }

        return null
    }

    private fun findCancelButton(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
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

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val result = findCancelButton(child)
                if (result != null) return result
            }
        }

        return null
    }

    private fun findButtonsByText(node: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()

        val nodeText = node.text?.toString() ?: ""
        if (node.isClickable && nodeText.equals(text, ignoreCase = true)) {
            result.add(node)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                result.addAll(findButtonsByText(child, text))
            }
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
