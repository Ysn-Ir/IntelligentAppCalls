package com.example.appcall.data.local

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.appcall.data.api.ApiService
import com.example.appcall.data.model.SummaryEditRequest
import com.example.appcall.data.repository.TokenStorage
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun localDatabase(): AppLocalDatabase
        fun apiService(): ApiService
        fun tokenStorage(): TokenStorage
    }

    companion object {
        private const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting offline sync worker execution")
        
        // Resolve dependencies via Hilt Entry Point
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        val localDatabase = entryPoint.localDatabase()
        val apiService = entryPoint.apiService()
        val tokenStorage = entryPoint.tokenStorage()

        val pendingItems = localDatabase.getPendingSyncItems()
        if (pendingItems.isEmpty()) {
            Log.d(TAG, "No pending items to sync")
            return Result.success()
        }

        val auth = tokenStorage.authHeader ?: "Bearer dummy_test_token"
        var failedAny = false

        for (item in pendingItems) {
            Log.d(TAG, "Processing sync item ${item.id} | Action: ${item.actionType} | Call: ${item.callId}")
            try {
                val success = when (item.actionType) {
                    "UPLOAD_AUDIO" -> {
                        val file = item.filePath?.let { File(it) }
                        if (file != null && file.exists()) {
                            val requestBody = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                            val body = MultipartBody.Part.createFormData("file", file.name, requestBody)
                            val response = apiService.uploadCallAudio(auth, item.callId, body)
                            if (response.isSuccessful) {
                                // Delete file after successful upload to free device space
                                file.delete()
                                true
                            } else false
                        } else {
                            // File was deleted or does not exist, discard task
                            true
                        }
                    }
                    "EDIT_SUMMARY" -> {
                        val newText = item.payload ?: ""
                        val response = apiService.editCallSummary(auth, item.callId, SummaryEditRequest(newText, null))
                        response.isSuccessful
                    }
                    "VALIDATE_SUMMARY" -> {
                        val response = apiService.validateCallSummary(auth, item.callId)
                        response.isSuccessful
                    }
                    "VALIDATE_APP" -> {
                        val response = apiService.validateAppointment(auth, item.callId)
                        response.isSuccessful
                    }
                    "DISMISS_APP" -> {
                        val response = apiService.dismissAppointment(auth, item.callId)
                        response.isSuccessful
                    }
                    "ADD_TASK", "TOGGLE_TASK" -> {
                        val payload = item.payload
                        if (!payload.isNullOrBlank()) {
                            try {
                                val obj = org.json.JSONObject(payload)
                                val dto = com.example.appcall.data.model.TaskDto(
                                    id = obj.getString("id"),
                                    title = obj.getString("title"),
                                    completed = obj.getBoolean("completed")
                                )
                                val response = apiService.createTask(auth, dto)
                                response.isSuccessful
                            } catch (e: Exception) {
                                true
                            }
                        } else true
                    }
                    "ADD_AGENDA" -> {
                        val payload = item.payload
                        if (!payload.isNullOrBlank()) {
                            try {
                                val obj = org.json.JSONObject(payload)
                                val dto = com.example.appcall.data.model.AgendaDto(
                                    id = obj.getString("id"),
                                    title = obj.getString("title"),
                                    scheduledAt = obj.getString("scheduledAt")
                                )
                                val response = apiService.createAgendaItem(auth, dto)
                                response.isSuccessful
                            } catch (e: Exception) {
                                true
                            }
                        } else true
                    }
                    "ADD_FILE" -> {
                        val payload = item.payload
                        if (!payload.isNullOrBlank()) {
                            try {
                                val obj = org.json.JSONObject(payload)
                                val dto = com.example.appcall.data.model.FileDto(
                                    id = obj.getString("id"),
                                    name = obj.getString("name"),
                                    path = obj.getString("path"),
                                    size = obj.getString("size")
                                )
                                val response = apiService.createFileItem(auth, dto)
                                response.isSuccessful
                            } catch (e: Exception) {
                                true
                            }
                        } else true
                    }
                    else -> true
                }

                if (success) {
                    localDatabase.removeSyncItem(item.id)
                    Log.d(TAG, "Sync item ${item.id} completed successfully")
                } else {
                    failedAny = true
                    Log.w(TAG, "Sync item ${item.id} failed to process")
                }
            } catch (e: Exception) {
                failedAny = true
                Log.e(TAG, "Exception during sync item ${item.id}", e)
            }
        }

        return if (failedAny) Result.retry() else Result.success()
    }
}
