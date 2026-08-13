package com.example.appcall.data.calling

import android.util.Log
import com.example.appcall.data.repository.TokenStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiveTranscriptManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val tokenStorage: TokenStorage
) {
    private val TAG = "LiveTranscriptManager"
    private var webSocket: WebSocket? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    fun connect(callId: String) {
        disconnect() // Ensure any previous connection is cleared
        _transcript.value = ""

        // Use stored token or fallback dummy for offline/bypass-login testing
        val token = tokenStorage.token ?: "dummy_test_token"

        // Using standard WS port matching backend
        val hostIp = com.example.appcall.di.NetworkConfig.hostIp
        val wsUrl = "ws://$hostIp:8000/api/v1/ws/calls/$callId/live-transcript"
        Log.d(TAG, "Connecting to WebSocket: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened")
                _isConnected.value = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket message received: $text")
                coroutineScope.launch {
                    val chunk = parseTranscriptChunk(text)
                    // Append each incoming chunk — matches server's UPDATE raw_text behaviour
                    val current = _transcript.value
                    _transcript.value = if (current.isEmpty()) chunk else "$current $chunk"
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code / $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code / $reason")
                _isConnected.value = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                _isConnected.value = false
            }
        })
    }

    private fun parseTranscriptChunk(message: String): String {
        return try {
            val json = org.json.JSONObject(message)
            when {
                json.has("text") -> json.getString("text")
                json.has("raw_text") -> json.getString("raw_text")
                json.has("transcript") -> json.getString("transcript")
                else -> message
            }
        } catch (e: Exception) {
            message
        }
    }

    fun disconnect() {
        Log.d(TAG, "Disconnecting WebSocket")
        webSocket?.close(1000, "App requested disconnect")
        webSocket = null
        _isConnected.value = false
    }
}
