package com.example.appcall.data.calling

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class VoipCallNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "VoipCallNotifListener"
        private val VOIP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "org.telegram.messenger",
            "org.telegram.plus",
            "com.facebook.orca",
            "com.viber.voip"
        )
        private var activeVoipCallId: String? = null
        private var activeVoipContactName: String? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        if (!VOIP_PACKAGES.contains(pkg)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getString(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getString(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: ""
        val category = notification.category

        val isCallNotification = category == Notification.CATEGORY_CALL ||
            text.contains("appel", ignoreCase = true) ||
            text.contains("call", ignoreCase = true) ||
            text.contains("sonnerie", ignoreCase = true) ||
            text.contains("en cours", ignoreCase = true) ||
            text.contains("incoming", ignoreCase = true) ||
            text.contains("ongoing", ignoreCase = true)

        if (isCallNotification && !title.isNullOrBlank() && !title.startsWith("WhatsApp")) {
            val appLabel = when (pkg) {
                "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
                "org.telegram.messenger", "org.telegram.plus" -> "Telegram"
                "com.facebook.orca" -> "Messenger"
                "com.viber.voip" -> "Viber"
                else -> "VoIP"
            }

            val cleanContact = title.trim()
            val formattedName = "$cleanContact ($appLabel)"

            if (activeVoipCallId == null) {
                val callId = "voip-${System.currentTimeMillis()}"
                activeVoipCallId = callId
                activeVoipContactName = formattedName
                Log.d(TAG, "VoIP Call Detected from $pkg: $formattedName (CallId: $callId)")

                // Store in prefs for persistent reference
                getSharedPreferences("call_recording_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString(CallingManager.KEY_ACTIVE_CALL_ID, callId)
                    .putString("active_contact_name", formattedName)
                    .putString("active_phone_number", appLabel)
                    .apply()

                PhoneCallRecorderService.start(
                    context = applicationContext,
                    callId = callId,
                    contactName = formattedName,
                    phoneNumber = appLabel
                )
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: return
        if (VOIP_PACKAGES.contains(pkg)) {
            val notification = sbn.notification ?: return
            val category = notification.category

            if (category == Notification.CATEGORY_CALL || activeVoipCallId != null) {
                Log.d(TAG, "VoIP Call Notification Removed from $pkg → Stopping recorder")
                if (activeVoipCallId != null) {
                    PhoneCallRecorderService.stop(applicationContext)
                    activeVoipCallId = null
                    activeVoipContactName = null
                }
            }
        }
    }
}
