package com.example.appcall.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.appcall.data.api.ApiService
import com.example.appcall.data.local.AppLocalDatabase
import com.example.appcall.data.model.*
import com.example.appcall.data.repository.TokenStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val localDatabase: AppLocalDatabase,
    private val tokenStorage: TokenStorage
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isSyncing = false

    init {
        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val builder = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            cm.registerNetworkCallback(builder.build(), object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i("OfflineSyncManager", "Network connected -> Triggering automatic sync")
                    triggerSync()
                }
            })
        } catch (e: Exception) {
            Log.e("OfflineSyncManager", "Failed to register network callback: ${e.message}")
        }
    }

    fun triggerSync() {
        scope.launch {
            syncAll()
        }
    }

    suspend fun syncAll() {
        if (isSyncing) return
        isSyncing = true

        val token = tokenStorage.authHeader
        if (token.isNullOrBlank()) {
            isSyncing = false
            return
        }

        try {
            val pending = localDatabase.getPendingSyncItems()
            if (pending.isEmpty()) {
                isSyncing = false
                return
            }

            Log.i("OfflineSyncManager", "Starting sync for ${pending.size} pending items")

            for (item in pending) {
                var success = false
                try {
                    when (item.actionType) {
                        "ADD_TASK", "TOGGLE_TASK" -> {
                            if (!item.payload.isNullOrBlank()) {
                                val json = JSONObject(item.payload)
                                val taskDto = TaskDto(
                                    id = json.optString("id", item.callId),
                                    title = json.optString("title", ""),
                                    completed = json.optBoolean("completed", false)
                                )
                                val res = apiService.createTask(token, taskDto)
                                if (res.isSuccessful) success = true
                            }
                        }

                        "DELETE_TASK" -> {
                            val res = apiService.deleteTask(token, item.callId)
                            if (res.isSuccessful || res.code() == 404) success = true
                        }

                        "ADD_AGENDA" -> {
                            if (!item.payload.isNullOrBlank()) {
                                val json = JSONObject(item.payload)
                                val agendaDto = AgendaDto(
                                    id = json.optString("id", item.callId),
                                    title = json.optString("title", ""),
                                    scheduledAt = json.optString("scheduled_at", json.optString("time", ""))
                                )
                                val res = apiService.createAgendaItem(token, agendaDto)
                                if (res.isSuccessful) success = true
                            }
                        }

                        "VALIDATE_SUMMARY" -> {
                            val res = apiService.validateCallSummary(token, item.callId)
                            if (res.isSuccessful || res.code() == 404) success = true
                        }

                        "EDIT_SUMMARY" -> {
                            val text = item.payload ?: ""
                            val res = apiService.editCallSummary(token, item.callId, SummaryEditRequest(newText = text, voiceCommandTranscript = null))
                            if (res.isSuccessful || res.code() == 404) success = true
                        }

                        "VALIDATE_APP" -> {
                            val res = apiService.validateAppointment(token, item.callId)
                            if (res.isSuccessful || res.code() == 404) success = true
                        }

                        "DISMISS_APP" -> {
                            val res = apiService.dismissAppointment(token, item.callId)
                            if (res.isSuccessful || res.code() == 404) success = true
                        }

                        "UPLOAD_AUDIO" -> {
                            if (!item.filePath.isNullOrBlank()) {
                                val file = File(item.filePath)
                                if (file.exists() && file.length() > 0) {
                                    val reqBody = file.asRequestBody("audio/wav".toMediaTypeOrNull())
                                    val part = MultipartBody.Part.createFormData("file", file.name, reqBody)
                                    val res = apiService.uploadCallAudio(token, item.callId, part)
                                    if (res.isSuccessful) success = true
                                } else {
                                    // File no longer exists locally, remove from queue
                                    success = true
                                }
                            } else {
                                success = true
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("OfflineSyncManager", "Error syncing item ${item.id} (${item.actionType}): ${e.message}")
                }

                if (success) {
                    localDatabase.removeSyncItem(item.id)
                    Log.i("OfflineSyncManager", "Synced and removed item ${item.id} (${item.actionType})")
                }
            }
        } finally {
            isSyncing = false
        }
    }
}
