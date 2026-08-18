package com.example.appcall.data.calling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.appcall.data.local.AppLocalDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manifest-registered BroadcastReceiver for PHONE_STATE changes.
 *
 * This is more reliable than the in-process TelephonyCallback on Samsung devices
 * because it runs at OS-level and fires for ALL outgoing/incoming call state changes,
 * including those initiated by the app via Intent.ACTION_CALL.
 *
 * OFFHOOK fires when the call is actually CONNECTED (recipient answered).
 * IDLE fires when the call ENDS.
 */
class PhoneStateBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneStateReceiver"
        const val PREF_NAME = "call_recording_prefs"
        const val KEY_ACTIVE_CALL_ID = "active_call_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        Log.d(TAG, "Phone state changed: $state (number: $incomingNumber)")

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        if (!incomingNumber.isNullOrBlank()) {
            prefs.edit().putString("active_phone_number", incomingNumber).apply()
        }

        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call is now ACTIVE (connected). Start recording.
                val callId = prefs.getString(KEY_ACTIVE_CALL_ID, null)
                    ?: "native-${System.currentTimeMillis()}"
                prefs.edit().putString(KEY_ACTIVE_CALL_ID, callId).apply()

                val contactName = prefs.getString("active_contact_name", null) ?: "Appel Téléphonique"
                val phoneNumber = prefs.getString("active_phone_number", null) ?: incomingNumber

                try {
                    val db = AppLocalDatabase(context)
                    db.saveCallHistoryItem(
                        id = callId,
                        contactId = phoneNumber ?: "native",
                        contactName = contactName,
                        direction = "OUTBOUND",
                        status = "IN_PROGRESS",
                        startedAt = nowIso,
                        endedAt = null
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not log call start in history: ${e.message}")
                }

                Log.d(TAG, "OFFHOOK → starting recorder for callId=$callId")
                PhoneCallRecorderService.start(context, callId)
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended. Stop recording.
                Log.d(TAG, "IDLE → stopping recorder")
                val callId = prefs.getString(KEY_ACTIVE_CALL_ID, null)
                val contactName = prefs.getString("active_contact_name", null) ?: "Appel Téléphonique"
                val phoneNumber = prefs.getString("active_phone_number", null) ?: incomingNumber

                if (callId != null) {
                    try {
                        val db = AppLocalDatabase(context)
                        val existing = db.getCallHistory().firstOrNull { it.id == callId }
                        val startedAt = existing?.startedAt ?: nowIso
                        db.saveCallHistoryItem(
                            id = callId,
                            contactId = phoneNumber ?: existing?.contactId ?: "native",
                            contactName = contactName,
                            direction = existing?.direction ?: "OUTBOUND",
                            status = "COMPLETED",
                            startedAt = startedAt,
                            endedAt = nowIso
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not log call completion in history: ${e.message}")
                    }
                }

                PhoneCallRecorderService.stop(context)
                // Clear stored callId
                prefs.edit().remove(KEY_ACTIVE_CALL_ID).apply()
            }

            TelephonyManager.EXTRA_STATE_RINGING -> {
                Log.d(TAG, "RINGING — waiting for answer")
            }
        }
    }
}
