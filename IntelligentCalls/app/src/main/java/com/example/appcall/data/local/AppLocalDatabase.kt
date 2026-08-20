package com.example.appcall.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.appcall.domain.model.CallSummary
import com.example.appcall.domain.model.Appointment
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLocalDatabase @Inject constructor(
    @ApplicationContext context: Context
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val TAG = "AppLocalDatabase"
        private const val DATABASE_NAME = "appcall_local.db"
        private const val DATABASE_VERSION = 9

        // Table Calls (Summaries cache)
        private const val TABLE_CALLS = "calls"
        private const val KEY_CALL_ID = "call_id"
        private const val KEY_SUMMARY_TEXT = "summary_text"
        private const val KEY_CONFIDENCE_SCORE = "confidence_score"
        private const val KEY_SUMMARY_STATUS = "summary_status"
        private const val KEY_APPOINTMENT_ID = "appointment_id"
        private const val KEY_APPOINTMENT_DATE = "appointment_date"
        private const val KEY_APPOINTMENT_STATUS = "appointment_status"
        private const val KEY_RAW_TRANSCRIPT = "raw_transcript"
        private const val KEY_SPEAKER_SEGMENTS = "speaker_segments"
        private const val KEY_SENTIMENT = "sentiment"
        private const val KEY_INTENT = "intent"
        private const val KEY_TAGS = "tags"
        private const val KEY_CONTACT_NAME_CALL = "contact_name"
        private const val KEY_PHONE_NUMBER_CALL = "phone_number"

        // Table Sync Queue
        private const val TABLE_SYNC_QUEUE = "sync_queue"
        private const val KEY_SYNC_ID = "sync_id"
        private const val KEY_ACTION_TYPE = "action_type" // UPLOAD_AUDIO, EDIT_SUMMARY, VALIDATE_APP, DISMISS_APP, ADD_TASK, TOGGLE_TASK, ADD_APPOINTMENT, ADD_FILE
        private const val KEY_FILE_PATH = "file_path"
        private const val KEY_PAYLOAD = "payload"

        // Table Tasks (To-do)
        private const val TABLE_TASKS = "tasks"
        private const val KEY_TASK_ID = "task_id"
        private const val KEY_TASK_TITLE = "title"
        private const val KEY_TASK_COMPLETED = "completed"

        // Table Agenda Appointments (Agenda)
        private const val TABLE_AGENDA = "agenda"
        private const val KEY_AGENDA_ID = "agenda_id"
        private const val KEY_AGENDA_TITLE = "title"
        private const val KEY_AGENDA_DATE = "scheduled_at"
        private const val KEY_AGENDA_CONTACT_NAME = "contact_name"
        private const val KEY_AGENDA_PHONE = "phone_number"
        private const val KEY_AGENDA_CALL_ID = "call_id"
        private const val KEY_AGENDA_STATUS = "status"

        // Table Files (Fichiers)
        private const val TABLE_FILES = "files"
        private const val KEY_FILE_ID = "file_id"
        private const val KEY_FILE_NAME = "name"
        private const val KEY_FILE_PATH_STORED = "path"
        private const val KEY_FILE_SIZE = "size"

        // Table Call History
        private const val TABLE_CALL_HISTORY = "call_history"
        private const val KEY_HIST_ID = "hist_id"
        private const val KEY_HIST_CONTACT_ID = "contact_id"
        private const val KEY_HIST_CONTACT_NAME = "contact_name"
        private const val KEY_HIST_DIRECTION = "direction"
        private const val KEY_HIST_STATUS = "status"
        private const val KEY_HIST_STARTED_AT = "started_at"
        private const val KEY_HIST_ENDED_AT = "ended_at"

        // Table Chat History
        private const val TABLE_CHAT = "chat_history"
        private const val KEY_CHAT_ID = "chat_id"
        private const val KEY_CHAT_SESSION_ID = "session_id"
        private const val KEY_CHAT_CONTACT_ID = "contact_id"
        private const val KEY_CHAT_IS_USER = "is_user"
        private const val KEY_CHAT_TEXT = "text"
        private const val KEY_CHAT_SOURCES = "sources_json"
        private const val KEY_CHAT_CREATED_AT = "created_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createCallsTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_CALLS (
                $KEY_CALL_ID TEXT PRIMARY KEY,
                $KEY_SUMMARY_TEXT TEXT,
                $KEY_CONFIDENCE_SCORE REAL,
                $KEY_SUMMARY_STATUS TEXT,
                $KEY_APPOINTMENT_ID TEXT,
                $KEY_APPOINTMENT_DATE TEXT,
                $KEY_APPOINTMENT_STATUS TEXT,
                $KEY_SENTIMENT TEXT,
                $KEY_INTENT TEXT,
                $KEY_TAGS TEXT,
                $KEY_CONTACT_NAME_CALL TEXT,
                $KEY_PHONE_NUMBER_CALL TEXT
            )
        """.trimIndent()

        val createSyncQueueTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_SYNC_QUEUE (
                $KEY_SYNC_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_CALL_ID TEXT,
                $KEY_ACTION_TYPE TEXT,
                $KEY_FILE_PATH TEXT,
                $KEY_PAYLOAD TEXT
            )
        """.trimIndent()

        val createTasksTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_TASKS (
                $KEY_TASK_ID TEXT PRIMARY KEY,
                $KEY_TASK_TITLE TEXT,
                $KEY_TASK_COMPLETED INTEGER DEFAULT 0
            )
        """.trimIndent()

        val createAgendaTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_AGENDA (
                $KEY_AGENDA_ID TEXT PRIMARY KEY,
                $KEY_AGENDA_TITLE TEXT,
                $KEY_AGENDA_DATE TEXT,
                $KEY_AGENDA_CONTACT_NAME TEXT,
                $KEY_AGENDA_PHONE TEXT,
                $KEY_AGENDA_CALL_ID TEXT,
                $KEY_AGENDA_STATUS TEXT
            )
        """.trimIndent()

        val createFilesTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_FILES (
                $KEY_FILE_ID TEXT PRIMARY KEY,
                $KEY_FILE_NAME TEXT,
                $KEY_FILE_PATH_STORED TEXT,
                $KEY_FILE_SIZE TEXT
            )
        """.trimIndent()

        val createCallHistoryTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_CALL_HISTORY (
                $KEY_HIST_ID TEXT PRIMARY KEY,
                $KEY_HIST_CONTACT_ID TEXT,
                $KEY_HIST_CONTACT_NAME TEXT,
                $KEY_HIST_DIRECTION TEXT,
                $KEY_HIST_STATUS TEXT,
                $KEY_HIST_STARTED_AT TEXT,
                $KEY_HIST_ENDED_AT TEXT
            )
        """.trimIndent()

        val createChatTable = """
            CREATE TABLE IF NOT EXISTS $TABLE_CHAT (
                $KEY_CHAT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_CHAT_SESSION_ID TEXT,
                $KEY_CHAT_CONTACT_ID TEXT,
                $KEY_CHAT_IS_USER INTEGER,
                $KEY_CHAT_TEXT TEXT,
                $KEY_CHAT_SOURCES TEXT,
                $KEY_CHAT_CREATED_AT INTEGER
            )
        """.trimIndent()

        db.execSQL(createCallsTable)
        db.execSQL(createSyncQueueTable)
        db.execSQL(createTasksTable)
        db.execSQL(createAgendaTable)
        db.execSQL(createFilesTable)
        db.execSQL(createCallHistoryTable)
        db.execSQL(createChatTable)

        // Seed with initial mock data if empty
        seedMockData(db)
        Log.d(TAG, "Local database tables created/verified successfully")
    }

    private fun seedMockData(db: SQLiteDatabase) {
        try {
            // Mock Tasks
            db.execSQL("INSERT OR IGNORE INTO $TABLE_TASKS ($KEY_TASK_ID, $KEY_TASK_TITLE, $KEY_TASK_COMPLETED) VALUES ('task-1', 'Appeler le client pour validation', 0)")
            db.execSQL("INSERT OR IGNORE INTO $TABLE_TASKS ($KEY_TASK_ID, $KEY_TASK_TITLE, $KEY_TASK_COMPLETED) VALUES ('task-2', 'Préparer la présentation commerciale', 1)")

            // Mock Agenda
            db.execSQL("INSERT OR IGNORE INTO $TABLE_AGENDA ($KEY_AGENDA_ID, $KEY_AGENDA_TITLE, $KEY_AGENDA_DATE) VALUES ('agenda-1', 'Réunion d''équipe hebdomadaire', '2026-07-17T10:00:00Z')")
        } catch (e: Exception) {
            Log.w(TAG, "Mock data seeding skipped: ${e.message}")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Safe migration: never drop user tables!
        onCreate(db)
        try {
            db.execSQL("ALTER TABLE $TABLE_CALLS ADD COLUMN $KEY_SENTIMENT TEXT")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE $TABLE_CALLS ADD COLUMN $KEY_INTENT TEXT")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE $TABLE_CALLS ADD COLUMN $KEY_TAGS TEXT")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE $TABLE_CALLS ADD COLUMN $KEY_CONTACT_NAME_CALL TEXT")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE $TABLE_CALLS ADD COLUMN $KEY_PHONE_NUMBER_CALL TEXT")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE $TABLE_AGENDA ADD COLUMN $KEY_AGENDA_CONTACT_NAME TEXT")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE $TABLE_AGENDA ADD COLUMN $KEY_AGENDA_PHONE TEXT")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE $TABLE_AGENDA ADD COLUMN $KEY_AGENDA_CALL_ID TEXT")
        } catch (e: Exception) {}
        try {
            db.execSQL("ALTER TABLE $TABLE_AGENDA ADD COLUMN $KEY_AGENDA_STATUS TEXT")
        } catch (e: Exception) {}
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    // --- Chat History Operations ---

    fun saveChatMessage(contactId: String?, isUser: Boolean, text: String, sourcesJson: String? = null, sessionId: String? = null) {
        try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(KEY_CHAT_SESSION_ID, sessionId)
                put(KEY_CHAT_CONTACT_ID, contactId)
                put(KEY_CHAT_IS_USER, if (isUser) 1 else 0)
                put(KEY_CHAT_TEXT, text)
                put(KEY_CHAT_SOURCES, sourcesJson)
                put(KEY_CHAT_CREATED_AT, System.currentTimeMillis())
            }
            db.insert(TABLE_CHAT, null, values)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chat message: ${e.message}")
        }
    }

    fun getChatHistory(contactId: String?): List<LocalChatMessage> {
        val list = mutableListOf<LocalChatMessage>()
        try {
            val db = readableDatabase
            val selection = if (contactId == null) "$KEY_CHAT_CONTACT_ID IS NULL" else "$KEY_CHAT_CONTACT_ID = ?"
            val selectionArgs = if (contactId == null) null else arrayOf(contactId)
            val cursor = db.query(TABLE_CHAT, null, selection, selectionArgs, null, null, "$KEY_CHAT_ID ASC")
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        LocalChatMessage(
                            id = it.getInt(it.getColumnIndexOrThrow(KEY_CHAT_ID)),
                            sessionId = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_SESSION_ID)),
                            contactId = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_CONTACT_ID)),
                            isUser = it.getInt(it.getColumnIndexOrThrow(KEY_CHAT_IS_USER)) == 1,
                            text = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_TEXT)),
                            sourcesJson = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_SOURCES)),
                            createdAt = it.getLong(it.getColumnIndexOrThrow(KEY_CHAT_CREATED_AT))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get chat history: ${e.message}")
        }
        return list
    }

    fun clearChatHistory(contactId: String?) {
        try {
            val db = writableDatabase
            val selection = if (contactId == null) "$KEY_CHAT_CONTACT_ID IS NULL" else "$KEY_CHAT_CONTACT_ID = ?"
            val selectionArgs = if (contactId == null) null else arrayOf(contactId)
            db.delete(TABLE_CHAT, selection, selectionArgs)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear chat history: ${e.message}")
        }
    }

    fun getChatSessions(contactId: String?): List<ChatSessionSummary> {
        val list = mutableListOf<ChatSessionSummary>()
        try {
            val db = readableDatabase
            val selection = if (contactId == null) "$KEY_CHAT_CONTACT_ID IS NULL" else "$KEY_CHAT_CONTACT_ID = ?"
            val selectionArgs = if (contactId == null) null else arrayOf(contactId)
            val cursor = db.query(TABLE_CHAT, null, selection, selectionArgs, null, null, "$KEY_CHAT_ID DESC")
            val sessionMap = linkedMapOf<String, MutableList<LocalChatMessage>>()
            cursor.use {
                while (it.moveToNext()) {
                    val rawSid = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_SESSION_ID))
                    val sId = if (!rawSid.isNullOrBlank()) rawSid else "session-principal"
                    val msg = LocalChatMessage(
                        id = it.getInt(it.getColumnIndexOrThrow(KEY_CHAT_ID)),
                        sessionId = sId,
                        contactId = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_CONTACT_ID)),
                        isUser = it.getInt(it.getColumnIndexOrThrow(KEY_CHAT_IS_USER)) == 1,
                        text = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_TEXT)),
                        sourcesJson = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_SOURCES)),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(KEY_CHAT_CREATED_AT))
                    )
                    sessionMap.getOrPut(sId) { mutableListOf() }.add(msg)
                }
            }
            for ((sId, msgs) in sessionMap) {
                val firstUserMsg = msgs.lastOrNull { it.isUser }?.text ?: msgs.lastOrNull()?.text ?: "Conversation"
                val lastTs = msgs.firstOrNull()?.createdAt ?: System.currentTimeMillis()
                list.add(
                    ChatSessionSummary(
                        sessionId = sId,
                        contactId = contactId,
                        previewText = firstUserMsg.take(60),
                        messageCount = msgs.size,
                        lastTimestamp = lastTs
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get chat sessions: ${e.message}")
        }
        return list
    }

    fun getSessionMessages(sessionId: String): List<LocalChatMessage> {
        val list = mutableListOf<LocalChatMessage>()
        try {
            val db = readableDatabase
            val (selection, selectionArgs) = if (sessionId == "session-principal") {
                Pair("$KEY_CHAT_SESSION_ID = ? OR $KEY_CHAT_SESSION_ID IS NULL", arrayOf("session-principal"))
            } else {
                Pair("$KEY_CHAT_SESSION_ID = ?", arrayOf(sessionId))
            }
            val cursor = db.query(TABLE_CHAT, null, selection, selectionArgs, null, null, "$KEY_CHAT_ID ASC")
            cursor.use {
                while (it.moveToNext()) {
                    list.add(
                        LocalChatMessage(
                            id = it.getInt(it.getColumnIndexOrThrow(KEY_CHAT_ID)),
                            sessionId = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_SESSION_ID)) ?: "session-principal",
                            contactId = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_CONTACT_ID)),
                            isUser = it.getInt(it.getColumnIndexOrThrow(KEY_CHAT_IS_USER)) == 1,
                            text = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_TEXT)),
                            sourcesJson = it.getString(it.getColumnIndexOrThrow(KEY_CHAT_SOURCES)),
                            createdAt = it.getLong(it.getColumnIndexOrThrow(KEY_CHAT_CREATED_AT))
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get session messages: ${e.message}")
        }
        return list
    }

    fun deleteSession(sessionId: String) {
        try {
            val db = writableDatabase
            if (sessionId == "session-principal") {
                db.delete(TABLE_CHAT, "$KEY_CHAT_SESSION_ID = ? OR $KEY_CHAT_SESSION_ID IS NULL", arrayOf(sessionId))
            } else {
                db.delete(TABLE_CHAT, "$KEY_CHAT_SESSION_ID = ?", arrayOf(sessionId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete session: ${e.message}")
        }
    }

    // --- Calls Table Operations ---

    fun saveCallSummary(summary: CallSummary) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_CALL_ID, summary.callId)
            put(KEY_SUMMARY_TEXT, summary.summaryText)
            put(KEY_CONFIDENCE_SCORE, summary.confidenceScore)
            put(KEY_SUMMARY_STATUS, summary.status)
            put(KEY_APPOINTMENT_ID, summary.detectedAppointmentId)
            put(KEY_APPOINTMENT_DATE, summary.appointment?.scheduledAt)
            put(KEY_APPOINTMENT_STATUS, summary.appointment?.status)
            put(KEY_SENTIMENT, summary.sentiment)
            put(KEY_INTENT, summary.intent)
            put(KEY_TAGS, summary.tags.joinToString(","))
            put(KEY_CONTACT_NAME_CALL, summary.contactName ?: summary.appointment?.contactName)
            put(KEY_PHONE_NUMBER_CALL, summary.phoneNumber ?: summary.appointment?.phoneNumber)
        }
        db.insertWithOnConflict(TABLE_CALLS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        db.update(TABLE_CALLS, values, "$KEY_CALL_ID = ?", arrayOf(summary.callId))
    }

    fun getCallSummary(callId: String): CallSummary? {
        val db = readableDatabase
        val cursor = db.query(TABLE_CALLS, null, "$KEY_CALL_ID = ?", arrayOf(callId), null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                val appointmentId = it.getString(it.getColumnIndexOrThrow(KEY_APPOINTMENT_ID))
                val appointmentDate = it.getString(it.getColumnIndexOrThrow(KEY_APPOINTMENT_DATE))
                val appointmentStatus = it.getString(it.getColumnIndexOrThrow(KEY_APPOINTMENT_STATUS))

                val appointment = if (appointmentId != null) {
                    Appointment(
                        id = appointmentId,
                        contactId = "1",
                        scheduledAt = appointmentDate ?: "",
                        status = appointmentStatus ?: ""
                    )
                } else null

                val tagsRaw = try {
                    val idx = it.getColumnIndex(KEY_TAGS)
                    if (idx >= 0 && !it.isNull(idx)) it.getString(idx) else null
                } catch (e: Exception) { null }
                val tagsList = tagsRaw?.split(",")?.map { t -> t.trim() }?.filter { t -> t.isNotEmpty() } ?: emptyList()

                val sentiment = try {
                    val idx = it.getColumnIndex(KEY_SENTIMENT)
                    if (idx >= 0 && !it.isNull(idx)) it.getString(idx) else null
                } catch (e: Exception) { null }

                val intent = try {
                    val idx = it.getColumnIndex(KEY_INTENT)
                    if (idx >= 0 && !it.isNull(idx)) it.getString(idx) else null
                } catch (e: Exception) { null }

                val contactName = try {
                    val idx = it.getColumnIndex(KEY_CONTACT_NAME_CALL)
                    if (idx >= 0 && !it.isNull(idx)) it.getString(idx) else null
                } catch (e: Exception) { null }

                val phoneNumber = try {
                    val idx = it.getColumnIndex(KEY_PHONE_NUMBER_CALL)
                    if (idx >= 0 && !it.isNull(idx)) it.getString(idx) else null
                } catch (e: Exception) { null }

                return CallSummary(
                    id = "local-sum-$callId",
                    callId = callId,
                    summaryText = it.getString(it.getColumnIndexOrThrow(KEY_SUMMARY_TEXT)) ?: "",
                    status = it.getString(it.getColumnIndexOrThrow(KEY_SUMMARY_STATUS)) ?: "PROPOSED",
                    sentiment = sentiment,
                    intent = intent,
                    tags = tagsList,
                    confidenceScore = if (it.isNull(it.getColumnIndexOrThrow(KEY_CONFIDENCE_SCORE))) null else it.getDouble(it.getColumnIndexOrThrow(KEY_CONFIDENCE_SCORE)),
                    detectedAppointmentId = appointmentId,
                    appointment = appointment,
                    contactName = contactName,
                    phoneNumber = phoneNumber
                )
            }
        }
        return null
    }

    fun saveTranscript(callId: String, rawText: String, speakerSegmentsJson: String?, confidenceScore: Double?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_CALL_ID, callId)
            put(KEY_RAW_TRANSCRIPT, rawText)
            put(KEY_SPEAKER_SEGMENTS, speakerSegmentsJson)
            if (confidenceScore != null) put(KEY_CONFIDENCE_SCORE, confidenceScore)
        }
        db.insertWithOnConflict(TABLE_CALLS, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        db.update(TABLE_CALLS, values, "$KEY_CALL_ID = ?", arrayOf(callId))
    }

    fun getLocalTranscript(callId: String): com.example.appcall.data.model.TranscriptDto? {
        val db = readableDatabase
        return try {
            val cursor = db.query(TABLE_CALLS, null, "$KEY_CALL_ID = ?", arrayOf(callId), null, null, null)
            cursor.use {
                if (it.moveToFirst()) {
                    val rawIndex = it.getColumnIndex(KEY_RAW_TRANSCRIPT)
                    val segIndex = it.getColumnIndex(KEY_SPEAKER_SEGMENTS)
                    val confIndex = it.getColumnIndex(KEY_CONFIDENCE_SCORE)
                    val raw = if (rawIndex >= 0) it.getString(rawIndex) else null
                    val segJson = if (segIndex >= 0) it.getString(segIndex) else null
                    val conf = if (confIndex >= 0 && !it.isNull(confIndex)) it.getDouble(confIndex) else 98.0
                    if (!raw.isNullOrBlank()) {
                        val segments = if (!segJson.isNullOrBlank()) {
                            try {
                                com.google.gson.Gson().fromJson(segJson, Array<com.example.appcall.data.model.SpeakerSegmentDto>::class.java).toList()
                            } catch (_: Exception) { null }
                        } else null
                        com.example.appcall.data.model.TranscriptDto(
                            id = "local-t-$callId",
                            callId = callId,
                            rawText = raw,
                            language = "fr",
                            confidenceScore = conf,
                            speakerSegments = segments
                        )
                    } else null
                } else null
            }
        } catch (_: Exception) { null }
    }

    // --- Call History Operations ---

    fun saveCallHistoryItem(id: String, contactId: String, contactName: String, direction: String, status: String, startedAt: String, endedAt: String?) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_HIST_ID, id)
            put(KEY_HIST_CONTACT_ID, contactId)
            put(KEY_HIST_CONTACT_NAME, contactName)
            put(KEY_HIST_DIRECTION, direction)
            put(KEY_HIST_STATUS, status)
            put(KEY_HIST_STARTED_AT, startedAt)
            put(KEY_HIST_ENDED_AT, endedAt)
        }
        db.insertWithOnConflict(TABLE_CALL_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getCallHistory(): List<LocalCallHistoryItem> {
        val list = mutableListOf<LocalCallHistoryItem>()
        val db = readableDatabase
        val cursor = db.query(TABLE_CALL_HISTORY, null, null, null, null, null, "$KEY_HIST_STARTED_AT DESC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    LocalCallHistoryItem(
                        id = it.getString(it.getColumnIndexOrThrow(KEY_HIST_ID)),
                        contactId = it.getString(it.getColumnIndexOrThrow(KEY_HIST_CONTACT_ID)),
                        contactName = it.getString(it.getColumnIndexOrThrow(KEY_HIST_CONTACT_NAME)),
                        direction = it.getString(it.getColumnIndexOrThrow(KEY_HIST_DIRECTION)),
                        status = it.getString(it.getColumnIndexOrThrow(KEY_HIST_STATUS)),
                        startedAt = it.getString(it.getColumnIndexOrThrow(KEY_HIST_STARTED_AT)),
                        endedAt = it.getString(it.getColumnIndexOrThrow(KEY_HIST_ENDED_AT))
                    )
                )
            }
        }
        return list
    }

    // --- Tasks Operations ---

    fun saveTask(id: String, title: String, completed: Boolean) {
        val db = writableDatabase
        var resolvedTitle = title
        if (resolvedTitle.isBlank()) {
            val existing = getTasks().firstOrNull { it.id == id }
            resolvedTitle = existing?.title ?: "Tâche"
        }
        val values = ContentValues().apply {
            put(KEY_TASK_ID, id)
            put(KEY_TASK_TITLE, resolvedTitle)
            put(KEY_TASK_COMPLETED, if (completed) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_TASKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteTask(id: String) {
        val db = writableDatabase
        db.delete(TABLE_TASKS, "$KEY_TASK_ID = ?", arrayOf(id))
    }

    fun deleteAgendaAppointment(id: String) {
        val db = writableDatabase
        db.delete(TABLE_AGENDA, "$KEY_AGENDA_ID = ?", arrayOf(id))
    }

    fun getTasks(): List<LocalTask> {
        val list = mutableListOf<LocalTask>()
        val db = readableDatabase
        val cursor = db.query(TABLE_TASKS, null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    LocalTask(
                        id = it.getString(it.getColumnIndexOrThrow(KEY_TASK_ID)),
                        title = it.getString(it.getColumnIndexOrThrow(KEY_TASK_TITLE)),
                        completed = it.getInt(it.getColumnIndexOrThrow(KEY_TASK_COMPLETED)) == 1
                    )
                )
            }
        }
        return list
    }

    // --- Agenda Operations ---

    fun saveAgendaAppointment(
        id: String,
        title: String,
        scheduledAt: String,
        contactName: String? = null,
        phoneNumber: String? = null,
        callId: String? = null,
        status: String? = "CONFIRMED"
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_AGENDA_ID, id)
            put(KEY_AGENDA_TITLE, title)
            put(KEY_AGENDA_DATE, scheduledAt)
            put(KEY_AGENDA_CONTACT_NAME, contactName)
            put(KEY_AGENDA_PHONE, phoneNumber)
            put(KEY_AGENDA_CALL_ID, callId)
            put(KEY_AGENDA_STATUS, status ?: "CONFIRMED")
        }
        db.insertWithOnConflict(TABLE_AGENDA, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun addAgendaAppointment(item: LocalAgendaItem) {
        saveAgendaAppointment(
            id = item.id,
            title = item.title,
            scheduledAt = item.scheduledAt,
            contactName = item.contactName,
            phoneNumber = item.phoneNumber,
            callId = item.callId,
            status = item.status
        )
    }

    fun getAgendaAppointments(): List<LocalAgendaItem> {
        val list = mutableListOf<LocalAgendaItem>()
        val db = readableDatabase
        val cursor = db.query(TABLE_AGENDA, null, null, null, null, null, "$KEY_AGENDA_DATE ASC")
        cursor.use {
            while (it.moveToNext()) {
                val contactNameIdx = it.getColumnIndex(KEY_AGENDA_CONTACT_NAME)
                val phoneIdx = it.getColumnIndex(KEY_AGENDA_PHONE)
                val callIdIdx = it.getColumnIndex(KEY_AGENDA_CALL_ID)
                val statusIdx = it.getColumnIndex(KEY_AGENDA_STATUS)

                list.add(
                    LocalAgendaItem(
                        id = it.getString(it.getColumnIndexOrThrow(KEY_AGENDA_ID)),
                        title = it.getString(it.getColumnIndexOrThrow(KEY_AGENDA_TITLE)),
                        scheduledAt = it.getString(it.getColumnIndexOrThrow(KEY_AGENDA_DATE)),
                        contactName = if (contactNameIdx != -1 && !it.isNull(contactNameIdx)) it.getString(contactNameIdx) else null,
                        phoneNumber = if (phoneIdx != -1 && !it.isNull(phoneIdx)) it.getString(phoneIdx) else null,
                        callId = if (callIdIdx != -1 && !it.isNull(callIdIdx)) it.getString(callIdIdx) else null,
                        status = if (statusIdx != -1 && !it.isNull(statusIdx)) it.getString(statusIdx) else "CONFIRMED"
                    )
                )
            }
        }
        return list
    }

    // --- Files Operations ---

    fun saveFile(id: String, name: String, path: String, size: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_FILE_ID, id)
            put(KEY_FILE_NAME, name)
            put(KEY_FILE_PATH_STORED, path)
            put(KEY_FILE_SIZE, size)
        }
        db.insertWithOnConflict(TABLE_FILES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getFiles(): List<LocalFileItem> {
        val list = mutableListOf<LocalFileItem>()
        val db = readableDatabase
        val cursor = db.query(TABLE_FILES, null, null, null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    LocalFileItem(
                        id = it.getString(it.getColumnIndexOrThrow(KEY_FILE_ID)),
                        name = it.getString(it.getColumnIndexOrThrow(KEY_FILE_NAME)),
                        path = it.getString(it.getColumnIndexOrThrow(KEY_FILE_PATH_STORED)),
                        size = it.getString(it.getColumnIndexOrThrow(KEY_FILE_SIZE))
                    )
                )
            }
        }
        return list
    }

    fun getFileForCall(callId: String): LocalFileItem? {
        val db = readableDatabase
        val cursor = db.query(TABLE_FILES, null, "$KEY_FILE_ID = ?", arrayOf(callId), null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return LocalFileItem(
                    id = it.getString(it.getColumnIndexOrThrow(KEY_FILE_ID)),
                    name = it.getString(it.getColumnIndexOrThrow(KEY_FILE_NAME)),
                    path = it.getString(it.getColumnIndexOrThrow(KEY_FILE_PATH_STORED)),
                    size = it.getString(it.getColumnIndexOrThrow(KEY_FILE_SIZE))
                )
            }
        }
        return null
    }

    // --- Sync Queue Operations ---

    fun addToSyncQueue(callId: String, actionType: String, filePath: String? = null, payload: String? = null) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_CALL_ID, callId)
            put(KEY_ACTION_TYPE, actionType)
            put(KEY_FILE_PATH, filePath)
            put(KEY_PAYLOAD, payload)
        }
        db.insert(TABLE_SYNC_QUEUE, null, values)
    }

    fun getPendingSyncItems(): List<SyncItem> {
        val list = mutableListOf<SyncItem>()
        val db = readableDatabase
        val cursor = db.query(TABLE_SYNC_QUEUE, null, null, null, null, null, "$KEY_SYNC_ID ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    SyncItem(
                        id = it.getInt(it.getColumnIndexOrThrow(KEY_SYNC_ID)),
                        callId = it.getString(it.getColumnIndexOrThrow(KEY_CALL_ID)),
                        actionType = it.getString(it.getColumnIndexOrThrow(KEY_ACTION_TYPE)),
                        filePath = it.getString(it.getColumnIndexOrThrow(KEY_FILE_PATH)),
                        payload = it.getString(it.getColumnIndexOrThrow(KEY_PAYLOAD))
                    )
                )
            }
        }
        return list
    }

    fun removeSyncItem(id: Int) {
        val db = writableDatabase
        db.delete(TABLE_SYNC_QUEUE, "$KEY_SYNC_ID = ?", arrayOf(id.toString()))
    }
}

data class LocalCallHistoryItem(val id: String, val contactId: String, val contactName: String, val direction: String, val status: String, val startedAt: String, val endedAt: String?)
data class LocalTask(val id: String, val title: String, val completed: Boolean)
data class LocalAgendaItem(
    val id: String,
    val title: String,
    val scheduledAt: String,
    val contactName: String? = null,
    val phoneNumber: String? = null,
    val callId: String? = null,
    val status: String? = "CONFIRMED"
)
data class LocalFileItem(val id: String, val name: String, val path: String, val size: String)
data class LocalChatMessage(val id: Int, val sessionId: String?, val contactId: String?, val isUser: Boolean, val text: String, val sourcesJson: String?, val createdAt: Long)
data class ChatSessionSummary(val sessionId: String, val contactId: String?, val previewText: String, val messageCount: Int, val lastTimestamp: Long)
data class SyncItem(val id: Int, val callId: String, val actionType: String, val filePath: String?, val payload: String?)
