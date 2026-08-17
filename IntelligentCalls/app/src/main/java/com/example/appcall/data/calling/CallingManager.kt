package com.example.appcall.data.calling

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.appcall.data.calling.PhoneStateBroadcastReceiver.Companion.KEY_ACTIVE_CALL_ID
import com.example.appcall.data.calling.PhoneStateBroadcastReceiver.Companion.PREF_NAME
import com.example.appcall.domain.model.Contact
import com.example.appcall.domain.repository.VoipRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CallState {
    object Idle : CallState
    object Connecting : CallState
    data class Active(
        val callId: String,
        val contactName: String,
        val isMuted: Boolean = false,
        val isSpeakerOn: Boolean = true   // speakerphone is on by default for recording
    ) : CallState
    object Disconnected : CallState
    data class Error(val message: String) : CallState
}

/**
 * Manages PSTN call detection and recording for the IntelligentCalls app.
 *
 * Architecture:
 * - Registers a TelephonyCallback on construction so it detects ALL phone calls,
 *   whether initiated through this app or through the native dialer.
 * - When a call goes OFFHOOK (active), starts PhoneCallRecorderService.
 * - When a call goes IDLE (ended), stops PhoneCallRecorderService which
 *   uploads the recording to the backend for AI analysis.
 * - startCall() dials a contact's number via Intent.ACTION_CALL (native dialer).
 */
@Singleton
class CallingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voipRepository: VoipRepository,
    private val liveTranscriptManager: LiveTranscriptManager
) {
    private val TAG = "CallingManager"

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private var activeCallId: String? = null
    val currentActiveCallId: String? get() = activeCallId
    private var activeContactName: String = "Appel Téléphonique"

    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var collectionJob: kotlinx.coroutines.Job? = null

    // Track OFFHOOK so we correctly fire onCallEnded only after an active call
    private var wasOffhook = false

    companion object {
        const val PREF_NAME = "call_recording_prefs"
        const val KEY_ACTIVE_CALL_ID = "active_call_id"

        fun getContactNameFromNumber(context: Context, number: String?): String {
            if (number.isNullOrBlank()) return "Appel Téléphonique"
            try {
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.READ_CONTACTS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    val uri = android.net.Uri.withAppendedPath(
                        android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        android.net.Uri.encode(number)
                    )
                    val cursor = context.contentResolver.query(
                        uri,
                        arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                        null, null, null
                    )
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val name = it.getString(0)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("CallingManager", "Error resolving contact name: ${e.message}")
            }
            return number
        }
    }

    init {
        registerPhoneStateListener()
    }

    /**
     * Registers a TelephonyCallback to detect native PSTN call state transitions.
     * This runs for the lifetime of the app process so any phone call triggers recording.
     */
    private fun registerPhoneStateListener() {
        try {
            val tm = context.getSystemService(TelephonyManager::class.java)
            val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    when (state) {
                        TelephonyManager.CALL_STATE_OFFHOOK -> {
                            if (!wasOffhook) {
                                wasOffhook = true
                                onCallBecameActive()
                            }
                        }
                        TelephonyManager.CALL_STATE_IDLE -> {
                            if (wasOffhook) {
                                wasOffhook = false
                                onCallBecameIdle()
                            }
                        }
                        TelephonyManager.CALL_STATE_RINGING -> {
                            Log.d(TAG, "Incoming call ringing")
                        }
                    }
                }
            }
            tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
            Log.d(TAG, "TelephonyCallback registered — auto-recording all PSTN calls")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register TelephonyCallback: ${e.message}")
        }
    }

    private fun onCallBecameActive() {
        val callId = activeCallId ?: run {
            // Call detected that wasn't initiated through this app (e.g. native dialer)
            val id = "native-${System.currentTimeMillis()}"
            activeCallId = id
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_ACTIVE_CALL_ID, id).apply()
            val phone = prefs.getString("active_phone_number", null)
            val name = prefs.getString("active_contact_name", null) ?: getContactNameFromNumber(context, phone)
            activeContactName = name
            coroutineScope.launch {
                voipRepository.addCallToHistory(id, "dev-$id", activeContactName, "OUTBOUND", "ONGOING")
            }
            id
        }
        Log.d(TAG, "TelephonyCallback OFFHOOK — call active callId=$callId")
        _callState.value = CallState.Active(callId = callId, contactName = activeContactName)
        // BroadcastReceiver also starts the service — guard inside service prevents double-start
        PhoneCallRecorderService.start(context, callId)

        // Start live transcript collection if available
        collectionJob?.cancel()
        collectionJob = coroutineScope.launch {
            liveTranscriptManager.transcript.collect { text ->
                if (text.isNotEmpty()) _transcript.value = text
            }
        }
    }

    private fun onCallBecameIdle() {
        val callId = activeCallId
        Log.d(TAG, "Call ended — stopping recorder for callId=$callId")

        // Stop the recording service (it uploads the file on destroy)
        PhoneCallRecorderService.stop(context)
        liveTranscriptManager.disconnect()
        collectionJob?.cancel()

        coroutineScope.launch {
            callId?.let { voipRepository.endCall(it) }
        }

        _callState.value = CallState.Disconnected
        activeCallId = null
        activeContactName = "Appel Téléphonique"
    }

    /**
     * Dials a contact's number through the native Android dialer.
     * The TelephonyCallback handles recording automatically once the call connects.
     */
    fun startCall(contact: Contact) {
        // Guard: prevent re-entry if a call is already in progress
        val currentState = _callState.value
        if (currentState is CallState.Connecting || currentState is CallState.Active) {
            Log.w(TAG, "startCall() ignored — already in state $currentState")
            return
        }

        _callState.value = CallState.Connecting
        activeContactName = contact.fullName.ifBlank { contact.phoneNumber }
        _transcript.value = ""

        coroutineScope.launch {
            val prefs = context.getSharedPreferences("call_settings", Context.MODE_PRIVATE)
            val useBridgeMode = prefs.getBoolean("use_pbx_bridge", false)
            val gatewayNumber = prefs.getString("pbx_gateway_number", "") ?: ""

            // Create a call row on the backend so we have an ID ready
            val callResult = voipRepository.initiateCall(contact.id)
            val callId = callResult.getOrNull()?.id ?: "native-${System.currentTimeMillis()}"
            activeCallId = callId
            Log.d(TAG, "Created call row $callId for ${contact.fullName} (${contact.phoneNumber})")

            // Persist callId & caller info for BroadcastReceiver and summary screens
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ACTIVE_CALL_ID, callId)
                .putString("active_contact_name", activeContactName)
                .putString("active_phone_number", contact.phoneNumber)
                .apply()

            // Determine dial target: if Bridge Mode is active and gateway set, dial gateway number
            val dialNumber = if (useBridgeMode && gatewayNumber.isNotBlank()) gatewayNumber else contact.phoneNumber
            Log.d(TAG, "Dialing via SIM: $dialNumber (Bridge Mode=$useBridgeMode, Target=${contact.phoneNumber})")

            // Launch native dialer
            val dialIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${dialNumber}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(dialIntent)
                _callState.value = CallState.Active(callId = callId, contactName = activeContactName)

                // Start local recorder service
                Log.d(TAG, "Starting recorder early for callId=$callId")
                PhoneCallRecorderService.start(context, callId)

            } catch (e: Exception) {
                Log.e(TAG, "Could not launch dialer: ${e.message}")
                _callState.value = CallState.Error("Could not open phone dialer: ${e.message}")
                activeCallId = null
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit().remove(KEY_ACTIVE_CALL_ID).apply()
            }
        }
    }


    fun toggleMute() {
        val state = _callState.value
        if (state is CallState.Active) {
            val next = !state.isMuted
            val am = context.getSystemService(android.media.AudioManager::class.java)
            am.isMicrophoneMute = next
            _callState.value = state.copy(isMuted = next)
        }
    }

    fun toggleSpeaker(audioManager: android.media.AudioManager) {
        val state = _callState.value
        if (state is CallState.Active) {
            val next = !state.isSpeakerOn
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = next
            _callState.value = state.copy(isSpeakerOn = next)
        }
    }

    fun disconnect() {
        val callId = activeCallId
        Log.d(TAG, "Manual disconnect requested for callId=$callId")

        // Stop the recording service explicitly
        PhoneCallRecorderService.stop(context)
        liveTranscriptManager.disconnect()
        collectionJob?.cancel()

        // Clear persisted callId so BroadcastReceiver doesn't restart on next IDLE
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().remove(KEY_ACTIVE_CALL_ID).apply()

        coroutineScope.launch {
            callId?.let { voipRepository.endCall(it) }
        }

        _callState.value = CallState.Disconnected
        activeCallId = null
        activeContactName = "Unknown"
        wasOffhook = false
    }

    fun reset() {
        _callState.value = CallState.Idle
        _transcript.value = ""
        collectionJob?.cancel()
        liveTranscriptManager.disconnect()
    }
}
