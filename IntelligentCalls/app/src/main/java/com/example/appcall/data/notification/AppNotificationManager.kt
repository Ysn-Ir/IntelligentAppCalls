package com.example.appcall.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.appcall.MainActivity

object AppNotificationManager {

    const val CHANNEL_AGENDA = "channel_appcall_agenda"
    const val CHANNEL_CALLS = "channel_appcall_calls"
    const val CHANNEL_TASKS = "channel_appcall_tasks"

    fun initChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val agendaChannel = NotificationChannel(
                CHANNEL_AGENDA,
                "Rappels d'Agenda & Rendez-vous",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications pour vos rendez-vous et réunions programmés"
                enableVibration(true)
            }

            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Analyses & Résumés d'Appels",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications lorsque le résumé et la transcription d'un appel sont prêts"
            }

            val tasksChannel = NotificationChannel(
                CHANNEL_TASKS,
                "Tâches & Rappels",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications pour les tâches et actions à réaliser"
            }

            manager.createNotificationChannel(agendaChannel)
            manager.createNotificationChannel(callsChannel)
            manager.createNotificationChannel(tasksChannel)
        }
    }

    fun showAppointmentNotification(
        context: Context,
        appointmentId: String,
        title: String,
        scheduledAt: String,
        contactName: String? = null
    ) {
        initChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_section", 2) // Agenda tab
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            appointmentId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val withWhom = if (!contactName.isNullOrBlank()) " avec $contactName" else ""
        val contentText = "Prévu : $scheduledAt$withWhom"

        val notification = NotificationCompat.Builder(context, CHANNEL_AGENDA)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("📅 Rendez-vous : $title")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$contentText\nAppuyez pour ouvrir votre agenda."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(appointmentId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission not granted on Android 13+
        }
    }

    fun showTaskNotification(
        context: Context,
        taskId: String,
        title: String
    ) {
        initChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_section", 3) // Tasks tab
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_TASKS)
            .setSmallIcon(android.R.drawable.checkbox_on_background)
            .setContentTitle("📋 Tâche à réaliser")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission not granted on Android 13+
        }
    }

    fun showCallProcessedNotification(
        context: Context,
        callId: String,
        contactName: String?,
        summaryPreview: String?
    ) {
        initChannels(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to_summary_call_id", callId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val caller = contactName ?: "Appel téléphonique"
        val bodyText = summaryPreview?.takeIf { it.isNotBlank() } ?: "Transcription et résumé IA disponibles."

        val notification = NotificationCompat.Builder(context, CHANNEL_CALLS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎙️ Appel analysé : $caller")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$bodyText\nAppuyez pour voir les détails et le rendez-vous."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(callId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission not granted on Android 13+
        }
    }
}
