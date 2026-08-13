package com.example.appcall.data.calling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

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
        Log.d(TAG, "Phone state changed: $state")

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call is now ACTIVE (connected). Start recording.
                val callId = prefs.getString(KEY_ACTIVE_CALL_ID, null)
                    ?: "native-${System.currentTimeMillis()}"
                Log.d(TAG, "OFFHOOK → starting recorder for callId=$callId")
                PhoneCallRecorderService.start(context, callId)
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended. Stop recording.
                Log.d(TAG, "IDLE → stopping recorder")
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
