package com.shadowhat.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shadowhat.app.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CampaignWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHANNEL_ID = "shadowhat_campaign_channel"
        const val NOTIFICATION_ID = 101
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(appContext)
        val dao = database.dao()

        createNotificationChannel()
        showProgressNotification("SHADOWHAT Activo", "Supervisando cola de campañas persistente")

        try {
            val pendingTasks = dao.getPendingTasks()
            if (pendingTasks.isEmpty()) {
                showProgressNotification("SHADOWHAT", "Todas las tareas de campaña están al día")
                return@withContext Result.success()
            }

            // In Android background mode, WorkManager keeps the state intact and notifies the user
            showProgressNotification(
                "Campaña en espera de confirmación",
                "${pendingTasks.size} grupos pendientes para envío asistido"
            )

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SHADOWHAT Campañas",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notificaciones de estado y progreso de campañas"
            }
            val manager = appContext.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun showProgressNotification(title: String, content: String) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)

        manager?.notify(NOTIFICATION_ID, builder.build())
    }
}
