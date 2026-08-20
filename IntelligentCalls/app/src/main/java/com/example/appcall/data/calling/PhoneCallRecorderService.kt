package com.example.appcall.data.calling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.appcall.data.notification.AppNotificationManager
import com.example.appcall.domain.repository.VoipRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Foreground service that records call audio using MediaRecorder.
 *
 * NOTE: MediaRecorder produces real audio during PSTN calls on Samsung Galaxy S21.
 * Despite maxAmplitude() returning 0 (Samsung patches this API during calls),
 * the actual .mp4 output file contains the captured audio.
 * AudioRecord (raw PCM) is zeroed by Knox at kernel level and must NOT be used.
 *
 * Strategy:
 * - Records with VOICE_RECOGNITION source (bypasses Samsung AEC/noise suppression)
 * - Falls back through MIC, VOICE_COMMUNICATION, DEFAULT
 * - Runs as a foreground service (required on Android 9+ for background mic access)
 * - Uploads the recording to the backend when the call ends for AI analysis
 */
@AndroidEntryPoint
class PhoneCallRecorderService : Service() {

    companion object {
        private const val TAG = "PhoneCallRecorderService"
        private const val NOTIF_ID = 9001
        private const val CHANNEL_ID = "call_recording"

        const val EXTRA_CALL_ID = "extra_call_id"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"

        fun start(context: Context, callId: String, contactName: String? = null, phoneNumber: String? = null) {
            val intent = Intent(context, PhoneCallRecorderService::class.java)
                .putExtra(EXTRA_CALL_ID, callId)
                .putExtra(EXTRA_CONTACT_NAME, contactName)
                .putExtra(EXTRA_PHONE_NUMBER, phoneNumber)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PhoneCallRecorderService::class.java))
        }
    }

    @Inject
    lateinit var voipRepository: VoipRepository

    @Inject
    lateinit var shizukuManager: ShizukuManager

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var activeCallId: String? = null
    private var activeContactName: String? = null
    private var activePhoneNumber: String? = null
    private var monitorJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val incomingCallId = intent?.getStringExtra(EXTRA_CALL_ID)
        Log.d(TAG, "Service starting for call: $incomingCallId (currently recording: $activeCallId)")

        // Guard: already recording the same call — skip
        if (mediaRecorder != null && incomingCallId == activeCallId) {
            Log.d(TAG, "Already recording callId=$activeCallId — skipping duplicate start")
            return START_NOT_STICKY
        }

        // Different callId arrived while recording — stop previous first
        if (mediaRecorder != null) {
            Log.d(TAG, "New callId arrived — stopping previous recording")
            stopRecordingAndUpload()
        }

        activeCallId = incomingCallId
        activeContactName = intent?.getStringExtra(EXTRA_CONTACT_NAME)
        activePhoneNumber = intent?.getStringExtra(EXTRA_PHONE_NUMBER)
        startForeground(NOTIF_ID, buildNotification())
        startRecording()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service stopping")
        stopRecordingAndUpload()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────────────────────────────────────────────────────────────────────
    // Recording
    // ─────────────────────────────────────────────────────────────────────────

    private fun startRecording() {
        try {
            if (shizukuManager.isShizukuAvailable()) {
                Log.d(TAG, "Shizuku ADB available — attempting to grant CAPTURE_AUDIO_OUTPUT")
                shizukuManager.grantPrivilegedPermissions(this)
            }

            val dir = File(filesDir, "recordings").also { it.mkdirs() }
            val callId = activeCallId ?: System.currentTimeMillis().toString()
            val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.getDefault()).format(java.util.Date())
            val rawName = activeContactName?.takeIf { it.isNotBlank() && !it.startsWith("Appel") }
                ?: activePhoneNumber?.takeIf { it.isNotBlank() }
                ?: "Appel"
            val cleanName = rawName.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(24)
            val file = File(dir, "Appel_${cleanName}_${timestamp}_${callId.take(6)}.mp4")
            recordingFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            // Try audio sources in order. VOICE_RECOGNITION bypasses Samsung's AEC/NS
            // processing that can suppress audio during PSTN calls.
            val sources = listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                MediaRecorder.AudioSource.DEFAULT
            )

            var started = false
            for (source in sources) {
                try {
                    recorder.reset()
                    recorder.setAudioSource(source)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    recorder.setAudioSamplingRate(44100)
                    recorder.setAudioEncodingBitRate(128000)
                    recorder.setAudioChannels(1)
                    recorder.setOutputFile(file.absolutePath)
                    recorder.prepare()
                    recorder.start()
                    mediaRecorder = recorder
                    Log.d(TAG, "MediaRecorder started with AudioSource=$source → ${file.name}")
                    started = true

                    // Log file size every 5 seconds to confirm data is being written.
                    // NOTE: maxAmplitude() is patched to return 0 on Samsung during PSTN calls
                    // even when the file DOES contain real audio — do not trust it.
                    monitorJob = scope.launch {
                        while (isActive && mediaRecorder != null) {
                            delay(5000)
                            val size = file.length()
                            Log.d(TAG, "Recording in progress: ${file.name} size=${size} bytes")
                        }
                    }
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "AudioSource=$source failed: ${e.message}, trying next")
                }
            }

            if (!started) {
                Log.e(TAG, "All audio sources failed — cannot record this call")
                recorder.release()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fatal error starting recording", e)
        }
    }

    private fun stopRecordingAndUpload() {
        val callId = activeCallId
        val file = recordingFile
        activeCallId = null
        recordingFile = null
        monitorJob?.cancel()
        monitorJob = null

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            @Suppress("DEPRECATION")
            audioManager?.stopBluetoothSco()
            @Suppress("DEPRECATION")
            audioManager?.isBluetoothScoOn = false
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping Bluetooth SCO: ${e.message}")
        }

        try {
            mediaRecorder?.apply {
                try { stop() } catch (e: Exception) {
                    Log.w(TAG, "MediaRecorder stop exception (short recording): ${e.message}")
                }
                release()
            }
            mediaRecorder = null
            Log.d(TAG, "Recorder stopped. File: ${file?.name} (${file?.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing recorder: ${e.message}")
            mediaRecorder = null
        }

        if (callId != null && file != null && file.exists() && file.length() > 128) {
            Log.d(TAG, "Recording finished: ${file.length()} bytes for call $callId (${file.name})")

            val sizeKb = "${(file.length() + 1023) / 1024} KB"
            val nowIso = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.getDefault()).format(java.util.Date())

            val db = com.example.appcall.data.local.AppLocalDatabase(this)
            val existing = db.getCallHistory().firstOrNull { it.id == callId }
            val prefs = getSharedPreferences("call_recording_prefs", Context.MODE_PRIVATE)
            val contactName = existing?.contactName?.takeIf { it.isNotBlank() && it != "native" }
                ?: prefs.getString("active_contact_name", null)
                ?: "Appel Téléphonique"
            val phoneNumber = existing?.contactId?.takeIf { it.startsWith("+") || it.any { c -> c.isDigit() } }
                ?: prefs.getString("active_phone_number", null)

            try {
                db.saveFile(id = callId, name = file.name, path = file.absolutePath, size = sizeKb)
                db.saveCallHistoryItem(
                    id = callId,
                    contactId = phoneNumber ?: existing?.contactId ?: "native",
                    contactName = contactName,
                    direction = existing?.direction ?: "OUTBOUND",
                    status = "COMPLETED",
                    startedAt = existing?.startedAt ?: nowIso,
                    endedAt = nowIso
                )
            } catch (e: Exception) {
                Log.w(TAG, "Could not persist call details locally: ${e.message}")
            }

            // Use a fresh independent scope — the service scope is cancelled in onDestroy()
            CoroutineScope(Dispatchers.IO).launch {
                voipRepository.uploadCallAudio(callId, file)
                    .onSuccess {
                        Log.d(TAG, "Upload successful for call $callId")
                        AppNotificationManager.showCallProcessedNotification(
                            this@PhoneCallRecorderService,
                            callId,
                                contactName,
                            "Enregistrement transféré. Résumé et transcription IA en cours."
                        )
                    }
                    .onFailure { Log.e(TAG, "Upload failed: ${it.message}") }
            }
        } else {
            Log.w(TAG, "Recording too small or missing — skipping upload (${file?.length()} bytes)")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Call Recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active while a call is being recorded"
            setSound(null, null)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("IntelligentCalls")
            .setContentText("🔴 Recording call for AI analysis…")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
