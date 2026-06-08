package com.example.drakofit

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class TrackingService : Service() {

    private val CHANNEL_ID = "tracking_channel"

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        createNotificationChannel()

        val notification = createNotification()

        startForeground(1, notification)

        return START_STICKY
    }

    override fun onDestroy() {
        stopForeground(true)
        super.onDestroy()
    }

    // ---------------------------------------------------
    // FOREGROUND SETUP
    // ---------------------------------------------------

    private fun createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Canal de tracking de actividad"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DrakoFit activo")
            .setContentText("Registrando actividad en segundo plano")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }
}