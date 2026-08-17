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
        private const val DATABASE_VERSION = 5

        // Table Calls (Summaries cache)
        private const val TABLE_CALLS = "calls"
        private const val KEY_CALL_ID = "call_id"
        private const val KEY_SUMMARY_TEXT = "summary_text"
        private const val KEY_CONFIDENCE_SCORE = "confidence_score"
        private const val KEY_SUMMARY_STATUS = "summary_status"
        private const val KEY_APPOINTMENT_ID = "appointment_id"
        private const val KEY_APPOINTMENT_DATE = "appointment_date"
        private const val KEY_APPOINTMENT_STATUS = "appointment_status"

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
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createCallsTable = """
            CREATE TABLE $TABLE_CALLS (
                $KEY_CALL_ID TEXT PRIMARY KEY,
                $KEY_SUMMARY_TEXT TEXT,
                $KEY_CONFIDENCE_SCORE REAL,
                $KEY_SUMMARY_STATUS TEXT,
                $KEY_APPOINTMENT_ID TEXT,
                $KEY_APPOINTMENT_DATE TEXT,
                $KEY_APPOINTMENT_STATUS TEXT
            )
        """.trimIndent()

        val createSyncQueueTable = """
            CREATE TABLE $TABLE_SYNC_QUEUE (
                $KEY_SYNC_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $KEY_CALL_ID TEXT,
                $KEY_ACTION_TYPE TEXT,
                $KEY_FILE_PATH TEXT,
                $KEY_PAYLOAD TEXT
            )
        """.trimIndent()

        val createTasksTable = """
            CREATE TABLE $TABLE_TASKS (
                $KEY_TASK_ID TEXT PRIMARY KEY,
                $KEY_TASK_TITLE TEXT,
                $KEY_TASK_COMPLETED INTEGER DEFAULT 0
            )
        """.trimIndent()

        val createAgendaTable = """
            CREATE TABLE $TABLE_AGENDA (
                $KEY_AGENDA_ID TEXT PRIMARY KEY,
                $KEY_AGENDA_TITLE TEXT,
                $KEY_AGENDA_DATE TEXT
            )
        """.trimIndent()

        val createFilesTable = """
            CREATE TABLE $TABLE_FILES (
                $KEY_FILE_ID TEXT PRIMARY KEY,
                $KEY_FILE_NAME TEXT,
                $KEY_FILE_PATH_STORED TEXT,
                $KEY_FILE_SIZE TEXT
            )
        """.trimIndent()

        val createCallHistoryTable = """
            CREATE TABLE $TABLE_CALL_HISTORY (
                $KEY_HIST_ID TEXT PRIMARY KEY,
                $KEY_HIST_CONTACT_ID TEXT,
                $KEY_HIST_CONTACT_NAME TEXT,
                $KEY_HIST_DIRECTION TEXT,
                $KEY_HIST_STATUS TEXT,
                $KEY_HIST_STARTED_AT TEXT,
                $KEY_HIST_ENDED_AT TEXT
            )
        """.trimIndent()

        db.execSQL(createCallsTable)
        db.execSQL(createSyncQueueTable)
        db.execSQL(createTasksTable)
        db.execSQL(createAgendaTable)
        db.execSQL(createFilesTable)
        db.execSQL(createCallHistoryTable)

        // Seed with initial mock data
        seedMockData(db)
        Log.d(TAG, "Local database tables created and seeded successfully")
    }

    private fun seedMockData(db: SQLiteDatabase) {
        // Mock Tasks
        db.execSQL("INSERT INTO $TABLE_TASKS ($KEY_TASK_ID, $KEY_TASK_TITLE, $KEY_TASK_COMPLETED) VALUES ('task-1', 'Appeler le client pour validation', 0)")
        db.execSQL("INSERT INTO $TABLE_TASKS ($KEY_TASK_ID, $KEY_TASK_TITLE, $KEY_TASK_COMPLETED) VALUES ('task-2', 'Préparer la présentation commerciale', 1)")

        // Mock Agenda
        db.execSQL("INSERT INTO $TABLE_AGENDA ($KEY_AGENDA_ID, $KEY_AGENDA_TITLE, $KEY_AGENDA_DATE) VALUES ('agenda-1', 'Réunion d''équipe hebdomadaire', '2026-07-17T10:00:00Z')")

        // Mock Call History
        db.execSQL("INSERT INTO $TABLE_CALL_HISTORY ($KEY_HIST_ID, $KEY_HIST_CONTACT_ID, $KEY_HIST_CONTACT_NAME, $KEY_HIST_DIRECTION, $KEY_HIST_STATUS, $KEY_HIST_STARTED_AT, $KEY_HIST_ENDED_AT) VALUES ('call-1', '1', 'Jean Dupont', 'OUTBOUND', 'COMPLETED', '2026-07-16T10:30:00Z', '2026-07-16T10:34:25Z')")
        db.execSQL("INSERT INTO $TABLE_CALL_HISTORY ($KEY_HIST_ID, $KEY_HIST_CONTACT_ID, $KEY_HIST_CONTACT_NAME, $KEY_HIST_DIRECTION, $KEY_HIST_STATUS, $KEY_HIST_STARTED_AT, $KEY_HIST_ENDED_AT) VALUES ('call-2', '2', 'Marie Martin', 'INBOUND', 'MISSED', '2026-07-16T09:15:00Z', NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CALLS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SYNC_QUEUE")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TASKS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_AGENDA")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FILES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CALL_HISTORY")
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
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
        }
        db.insertWithOnConflict(TABLE_CALLS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
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

                return CallSummary(
                    id = "local-sum-$callId",
                    callId = callId,
                    summaryText = it.getString(it.getColumnIndexOrThrow(KEY_SUMMARY_TEXT)) ?: "",
                    status = it.getString(it.getColumnIndexOrThrow(KEY_SUMMARY_STATUS)) ?: "PROPOSED",
                    confidenceScore = if (it.isNull(it.getColumnIndexOrThrow(KEY_CONFIDENCE_SCORE))) null else it.getDouble(it.getColumnIndexOrThrow(KEY_CONFIDENCE_SCORE)),
                    detectedAppointmentId = appointmentId,
                    appointment = appointment
                )
            }
        }
        return null
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
        val values = ContentValues().apply {
            put(KEY_TASK_ID, id)
            put(KEY_TASK_TITLE, title)
            put(KEY_TASK_COMPLETED, if (completed) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_TASKS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
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

    fun saveAgendaAppointment(id: String, title: String, scheduledAt: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(KEY_AGENDA_ID, id)
            put(KEY_AGENDA_TITLE, title)
            put(KEY_AGENDA_DATE, scheduledAt)
        }
        db.insertWithOnConflict(TABLE_AGENDA, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAgendaAppointments(): List<LocalAgendaItem> {
        val list = mutableListOf<LocalAgendaItem>()
        val db = readableDatabase
        val cursor = db.query(TABLE_AGENDA, null, null, null, null, null, "$KEY_AGENDA_DATE ASC")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    LocalAgendaItem(
                        id = it.getString(it.getColumnIndexOrThrow(KEY_AGENDA_ID)),
                        title = it.getString(it.getColumnIndexOrThrow(KEY_AGENDA_TITLE)),
                        scheduledAt = it.getString(it.getColumnIndexOrThrow(KEY_AGENDA_DATE))
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
data class LocalAgendaItem(val id: String, val title: String, val scheduledAt: String)
data class LocalFileItem(val id: String, val name: String, val path: String, val size: String)
data class SyncItem(val id: Int, val callId: String, val actionType: String, val filePath: String?, val payload: String?)
