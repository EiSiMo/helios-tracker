package com.example.helios_location_finder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat

class ListenerService : Service() {

    companion object {
        private const val TAG = "ListenerService"
        private const val CHANNEL_ID = "helios_listener"
        private const val NOTIFICATION_ID = 1

        fun start(context: android.content.Context) {
            val intent = Intent(context, ListenerService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val receiver = NtfyReceiver()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        val filter = IntentFilter("io.heckel.ntfy.MESSAGE_RECEIVED")
        ContextCompat.registerReceiver(
            this, receiver, filter, ContextCompat.RECEIVER_EXPORTED
        )
        Log.d(TAG, "Listener service started, receiver registered")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) { }
        Log.d(TAG, "Listener service stopped")
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Helios Tracker",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Helios Tracker listening for LOCATE commands"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Helios Tracker")
            .setContentText("Lauscht auf LOCATE-Anfragen")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
