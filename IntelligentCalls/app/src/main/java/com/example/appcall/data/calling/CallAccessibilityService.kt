package com.example.appcall.data.calling

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class CallAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallAccessibilityService"
        var instance: CallAccessibilityService? = null
            private set

        private var activeCallId: String? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "CallAccessibilityService connected!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkg = event.packageName?.toString() ?: return
        if (pkg == "com.whatsapp" || pkg == "com.whatsapp.w4b") {
            try {
                handleWhatsAppEvent(event)
            } catch (e: Exception) {
                Log.w(TAG, "Error inspecting WhatsApp accessibility event: ${e.message}")
            }
        }
    }

    private fun handleWhatsAppEvent(event: AccessibilityEvent) {
        val root = rootInActiveWindow ?: return
        val textList = mutableListOf<String>()
        collectAllText(root, textList)

        val isCallWindow = textList.any { t ->
            t.contains("appel vocal", ignoreCase = true) ||
            t.contains("appel vidéo", ignoreCase = true) ||
            t.contains("en cours", ignoreCase = true) ||
            t.contains("sonnerie", ignoreCase = true) ||
            t.contains("voip", ignoreCase = true)
        }

        if (isCallWindow) {
            val contactName = textList.firstOrNull { t ->
                t.isNotBlank() &&
                !t.contains("appel", ignoreCase = true) &&
                !t.contains("mute", ignoreCase = true) &&
                !t.contains("haut-parleur", ignoreCase = true) &&
                !t.contains("vidéo", ignoreCase = true) &&
                !t.contains("en cours", ignoreCase = true) &&
                !t.contains("chiffré", ignoreCase = true) &&
                !t.contains("bouton", ignoreCase = true) &&
                t.length in 2..35
            } ?: "Contact WhatsApp"

            val formattedName = "$contactName (WhatsApp)"

            if (activeCallId == null) {
                val callId = "wa-${System.currentTimeMillis()}"
                activeCallId = callId
                Log.d(TAG, "WhatsApp Call Window Detected: $formattedName ($callId)")

                getSharedPreferences("call_recording_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString(CallingManager.KEY_ACTIVE_CALL_ID, callId)
                    .putString("active_contact_name", formattedName)
                    .putString("active_phone_number", "WhatsApp")
                    .apply()

                PhoneCallRecorderService.start(
                    context = applicationContext,
                    callId = callId,
                    contactName = formattedName,
                    phoneNumber = "WhatsApp"
                )
            }
        }
    }

    private fun collectAllText(node: AccessibilityNodeInfo?, result: MutableList<String>) {
        if (node == null) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        for (i in 0 until node.childCount) {
            collectAllText(node.getChild(i), result)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "CallAccessibilityService interrupted")
    }

    override fun onDestroy() {
        instance = null
        if (activeCallId != null) {
            PhoneCallRecorderService.stop(applicationContext)
            activeCallId = null
        }
        super.onDestroy()
    }
}

