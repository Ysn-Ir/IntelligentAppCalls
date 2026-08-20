package com.example.appcall.data.calling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
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
            val resolvedName = resolveContactNameFromNumber(context, incomingNumber)
            if (!resolvedName.isNullOrBlank()) {
                prefs.edit().putString("active_contact_name", resolvedName).apply()
            }
        }

        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault()).format(Date())

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call is now ACTIVE (connected). Start recording.
                val callId = prefs.getString(KEY_ACTIVE_CALL_ID, null)
                    ?: "native-${System.currentTimeMillis()}"
                prefs.edit().putString(KEY_ACTIVE_CALL_ID, callId).apply()

                var (resolvedNum, resolvedName) = resolveLatestCallInfo(context)
                if (resolvedNum.isNullOrBlank()) {
                    resolvedNum = incomingNumber
                }
                if (resolvedName.isNullOrBlank() && !resolvedNum.isNullOrBlank()) {
                    resolvedName = resolveContactNameFromNumber(context, resolvedNum)
                }

                val finalContactName = resolvedName ?: resolvedNum ?: "Appel Téléphonique"
                val finalPhoneNumber = resolvedNum

                prefs.edit()
                    .putString("active_contact_name", finalContactName)
                    .putString("active_phone_number", finalPhoneNumber)
                    .apply()

                try {
                    val db = AppLocalDatabase(context)
                    db.saveCallHistoryItem(
                        id = callId,
                        contactId = finalPhoneNumber ?: "native",
                        contactName = finalContactName,
                        direction = "OUTBOUND",
                        status = "IN_PROGRESS",
                        startedAt = nowIso,
                        endedAt = null
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Could not log call start in history: ${e.message}")
                }

                Log.d(TAG, "OFFHOOK → starting recorder for callId=$callId (Contact: $finalContactName, Phone: $finalPhoneNumber)")
                PhoneCallRecorderService.start(context, callId, finalContactName, finalPhoneNumber)
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended. Stop recording.
                Log.d(TAG, "IDLE → stopping recorder")
                val callId = prefs.getString(KEY_ACTIVE_CALL_ID, null)

                var (resolvedNum, resolvedName) = resolveLatestCallInfo(context)
                if (resolvedNum.isNullOrBlank()) {
                    resolvedNum = prefs.getString("active_phone_number", null) ?: incomingNumber
                }
                if (resolvedName.isNullOrBlank() && !resolvedNum.isNullOrBlank()) {
                    resolvedName = resolveContactNameFromNumber(context, resolvedNum)
                }

                val finalContactName = resolvedName ?: prefs.getString("active_contact_name", null) ?: resolvedNum ?: "Appel Téléphonique"
                val finalPhoneNumber = resolvedNum ?: prefs.getString("active_phone_number", null)

                if (callId != null) {
                    try {
                        val db = AppLocalDatabase(context)
                        val existing = db.getCallHistory().firstOrNull { it.id == callId }
                        val startedAt = existing?.startedAt ?: nowIso
                        db.saveCallHistoryItem(
                            id = callId,
                            contactId = finalPhoneNumber ?: existing?.contactId ?: "native",
                            contactName = finalContactName,
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
                // Clear stored callId and active contact data so subsequent calls don't inherit stale values
                prefs.edit()
                    .remove(KEY_ACTIVE_CALL_ID)
                    .remove("active_contact_name")
                    .remove("active_phone_number")
                    .apply()
            }

            TelephonyManager.EXTRA_STATE_RINGING -> {
                Log.d(TAG, "RINGING — waiting for answer")
            }
        }
    }

    private fun resolveLatestCallInfo(context: Context): Pair<String?, String?> {
        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME
                ),
                null,
                null,
                "${CallLog.Calls.DATE} DESC LIMIT 1"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val numIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
                    val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                    val number = if (numIndex >= 0) it.getString(numIndex) else null
                    val name = if (nameIndex >= 0) it.getString(nameIndex) else null
                    return Pair(number, name)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query CallLog: ${e.message}")
        }
        return Pair(null, null)
    }

    private fun resolveContactNameFromNumber(context: Context, number: String?): String? {
        if (number.isNullOrBlank()) return null
        try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (index >= 0) return it.getString(index)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not resolve contact name: ${e.message}")
        }
        return null
    }
}
