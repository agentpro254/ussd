package com.example.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.engine.UssdSessionManager

class CodeeAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "CodeeAccessibilityService connected successfully")
        UssdSessionManager.setAccessibilityServiceInstance(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            val rootNode = rootInActiveWindow ?: return
            handleNodeHierarchy(rootNode, event.packageName?.toString() ?: "")
        }
    }

    private fun handleNodeHierarchy(rootNode: AccessibilityNodeInfo, pkgName: String) {
        // Look for common USSD dialog packages
        val isUssdCandidate = pkgName.contains("phone", ignoreCase = true) ||
                pkgName.contains("dialer", ignoreCase = true) ||
                pkgName.contains("telecom", ignoreCase = true) ||
                pkgName.contains("incall", ignoreCase = true) ||
                pkgName.contains("android", ignoreCase = true)

        if (!isUssdCandidate && !UssdSessionManager.isSessionActive()) {
            return
        }

        // Collect all text nodes
        val textList = mutableListOf<String>()
        var inputNode: AccessibilityNodeInfo? = null
        var sendButtonNode: AccessibilityNodeInfo? = null
        var cancelButtonNode: AccessibilityNodeInfo? = null

        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return

            val className = node.className?.toString() ?: ""
            val text = node.text?.toString()?.trim()

            if (className.contains("EditText", ignoreCase = true) || node.isEditable) {
                inputNode = node
            } else if (className.contains("Button", ignoreCase = true) || node.isClickable) {
                val btnText = text?.lowercase() ?: ""
                if (btnText.contains("send") || btnText.contains("ok") || btnText.contains("reply") || btnText.contains("submit")) {
                    sendButtonNode = node
                } else if (btnText.contains("cancel") || btnText.contains("dismiss") || btnText.contains("close")) {
                    cancelButtonNode = node
                }
            }

            if (!text.isNullOrBlank() && !className.contains("Button", ignoreCase = true) && !className.contains("EditText", ignoreCase = true)) {
                textList.add(text)
            }

            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }

        traverse(rootNode)

        val combinedText = textList.joinToString("\n")
        if (combinedText.isNotBlank()) {
            UssdSessionManager.onUssdDialogCaptured(
                text = combinedText,
                inputNode = inputNode,
                sendButton = sendButtonNode,
                cancelButton = cancelButtonNode
            )
        }
    }

    fun submitTextToActiveDialog(node: AccessibilityNodeInfo?, text: String, sendButton: AccessibilityNodeInfo?): Boolean {
        try {
            if (node != null && node.isEditable) {
                val arguments = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
            sendButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting text to active dialog", e)
            return false
        }
    }

    fun dismissActiveDialog(cancelButton: AccessibilityNodeInfo?): Boolean {
        return try {
            cancelButton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing active dialog", e)
            false
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "CodeeAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        UssdSessionManager.setAccessibilityServiceInstance(null)
    }

    companion object {
        private const val TAG = "CodeeAccessibility"
    }
}
