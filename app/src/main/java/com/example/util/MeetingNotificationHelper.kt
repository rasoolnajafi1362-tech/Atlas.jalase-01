package com.example.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.data.MeetingNote
import com.example.data.resolveStatus
import com.example.data.ResolutionStatusType
import com.example.data.local.MemberTaskEntity

/**
 * Enterprise Notification Helper managing smart system reminders for meeting resolutions,
 * deadlines, and pending action items for responsible board members based on Room database records.
 */
object MeetingNotificationHelper {

    const val CHANNEL_ID = "atlas_resolutions_reminder_channel"
    private const val CHANNEL_NAME = "یادآوری مصوبات و تعهدات جلسات اطلس"
    private const val CHANNEL_DESC = "ارسال اعلانات خودکار مهلت زمانی مصوبات و پیگیری وظایف اعضای مسئول"

    private var channelInitialized = false

    fun initNotificationChannel(context: Context) {
        if (channelInitialized) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
        channelInitialized = true
    }

    /**
     * Checks whether POST_NOTIFICATIONS permission is granted (always true on < Android 13).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Sends a rich Android system notification reminding the responsible member about a resolution deadline.
     */
    fun sendActionItemReminder(
        context: Context,
        note: MeetingNote,
        sessionTitle: String
    ): Boolean {
        initNotificationChannel(context)
        if (!hasNotificationPermission(context)) return false

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            note.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val status = note.resolveStatus()
        val statusPrefix = when (status) {
            ResolutionStatusType.DEFERRED -> "🚨 اخطار مصوبه معوق"
            ResolutionStatusType.IN_PROGRESS -> "⏳ یادآوری مصوبه در جریان"
            ResolutionStatusType.COMPLETED -> "✅ مصوبه تکمیل‌شده"
        }

        val title = "$statusPrefix: ${note.speaker}"
        val content = "«${note.text}» • مهلت: ${note.due.ifBlank { "فوری" }}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("مصوبه جلسه: $sessionTitle\nنوع مصوبه: ${note.type}\nعضو مسئول: ${note.speaker}\nمهلت اقدام: ${note.due}\nمتن تعهد: ${note.text}")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = (note.id.hashCode() and 0x7FFFFFFF)
        manager.notify(notificationId, notification)
        return true
    }

    /**
     * Sends reminders for all non-completed resolutions in the list.
     * Returns count of notifications successfully sent.
     */
    fun sendAllPendingReminders(
        context: Context,
        notes: List<MeetingNote>,
        sessionTitle: String
    ): Int {
        var sentCount = 0
        val pending = notes.filter { !it.isDone }
        for (note in pending) {
            if (sendActionItemReminder(context, note, sessionTitle)) {
                sentCount++
            }
        }
        return sentCount
    }

    /**
     * Sends a system notification reminding a board member about an upcoming deadline
     * for an assigned action item (`MemberTaskEntity`) stored in the Room database.
     */
    fun sendRoomMemberTaskDeadlineReminder(
        context: Context,
        task: MemberTaskEntity
    ): Boolean {
        if (task.isDone) return false
        initNotificationChannel(context)
        if (!hasNotificationPermission(context)) return false

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("target_task_id", task.taskId)
            putExtra("target_assignee", task.assigneeName)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            task.taskId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val urgencyPrefix = when (task.priority.trim()) {
            "فوری" -> "🚨 یادآوری فوری مهلت اقدام"
            "بالا" -> "⏰ یادآوری مهلت مصوبه هیئت‌مدیره"
            else -> "📅 یادآوری مهلت وظیفه محوله"
        }

        val roleBadge = if (task.assigneeRole.isNotBlank()) " (${task.assigneeRole})" else ""
        val title = "$urgencyPrefix: ${task.assigneeName}$roleBadge"
        val content = "«${task.taskTitle}» • مهلت: ${task.deadlineLabel} (${task.calendarDate})"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        "جلسه: ${task.sessionTitle}\n" +
                            "عضو مسئول: ${task.assigneeName}$roleBadge\n" +
                            "عنوان اقدام: ${task.taskTitle}\n" +
                            "مهلت زمانی: ${task.deadlineLabel} (تاریخ تقویم: ${task.calendarDate})\n" +
                            "اولویت: ${task.priority}"
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationId = ((task.taskId.hashCode() xor task.id) and 0x7FFFFFFF)
        manager.notify(notificationId, notification)
        return true
    }

    /**
     * Dispatches system notifications for all upcoming (non-completed) board member action item
     * deadlines retrieved from the Room database (`member_tasks` table).
     * Optionally filters by [assigneeFilter].
     */
    fun sendAllUpcomingRoomTaskDeadlineReminders(
        context: Context,
        tasks: List<MemberTaskEntity>,
        assigneeFilter: String? = null
    ): Int {
        var sentCount = 0
        val pendingTasks = tasks.filter { task ->
            !task.isDone && (assigneeFilter.isNullOrBlank() || assigneeFilter == "ALL" ||
                task.assigneeName.contains(assigneeFilter, ignoreCase = true))
        }
        for (task in pendingTasks) {
            if (sendRoomMemberTaskDeadlineReminder(context, task)) {
                sentCount++
            }
        }
        return sentCount
    }
}
