package com.example.helios_location_finder

import android.content.Context

object Prefs {
    private const val NAME = "helios_prefs"
    const val KEY_LISTEN_TOPIC = "listen_topic"
    const val KEY_REPLY_TOPIC = "reply_topic"

    private fun prefs(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun getListenTopic(context: Context): String =
        prefs(context).getString(KEY_LISTEN_TOPIC, "") ?: ""

    fun getReplyTopic(context: Context): String =
        prefs(context).getString(KEY_REPLY_TOPIC, "") ?: ""

    fun setListenTopic(context: Context, topic: String) =
        prefs(context).edit().putString(KEY_LISTEN_TOPIC, topic).apply()

    fun setReplyTopic(context: Context, topic: String) =
        prefs(context).edit().putString(KEY_REPLY_TOPIC, topic).apply()
}
