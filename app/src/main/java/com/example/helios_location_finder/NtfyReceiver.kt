package com.example.helios_location_finder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class NtfyReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NtfyReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: return
        val topic = intent.getStringExtra("topic") ?: ""

        Log.d(TAG, "Received ntfy message on topic '$topic': $message")

        val listenTopic = Prefs.getListenTopic(context)
        if (listenTopic.isBlank()) {
            Log.w(TAG, "No listen topic configured, ignoring")
            return
        }

        if (topic != listenTopic) {
            Log.d(TAG, "Topic '$topic' does not match configured '$listenTopic', ignoring")
            return
        }

        if (message.trim().equals("LOCATE", ignoreCase = true)) {
            Log.d(TAG, "LOCATE command received, enqueuing LocationWorker")
            val workRequest = OneTimeWorkRequestBuilder<LocationWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "locate", ExistingWorkPolicy.REPLACE, workRequest
            )
        }
    }
}
