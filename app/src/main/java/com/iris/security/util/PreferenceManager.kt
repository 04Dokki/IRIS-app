package com.iris.security.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.iris.security.IrisApplication
import com.iris.security.data.model.IrisConfig

class PreferenceManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
    private val gson = Gson()

    // ─── Setup state ────────────────────────────────────────────────────────

    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    // ─── Device config ──────────────────────────────────────────────────────

    var config: IrisConfig?
        get() {
            val json = prefs.getString(KEY_CONFIG, null) ?: return null
            return gson.fromJson(json, IrisConfig::class.java)
        }
        set(value) {
            val json = value?.let { gson.toJson(it) }
            prefs.edit().putString(KEY_CONFIG, json).apply()
        }

    // ─── Alert preferences ──────────────────────────────────────────────────

    var alertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ALERTS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ALERTS_ENABLED, value).apply()

    var motionAlertsEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOTION_ALERTS, true)
        set(value) = prefs.edit().putBoolean(KEY_MOTION_ALERTS, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION, value).apply()

    // ─── Stream ─────────────────────────────────────────────────────────────

    var lastKnownStreamUrl: String
        get() = prefs.getString(KEY_STREAM_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STREAM_URL, value).apply()

    // ─── FCM Token ──────────────────────────────────────────────────────────

    var fcmToken: String
        get() = prefs.getString(KEY_FCM_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FCM_TOKEN, value).apply()

    // ─── Helpers ────────────────────────────────────────────────────────────

    fun clearAll() = prefs.edit().clear().apply()

    companion object {
        private const val PREF_FILE = "iris_prefs"
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_CONFIG = "iris_config"
        private const val KEY_ALERTS_ENABLED = "alerts_enabled"
        private const val KEY_MOTION_ALERTS = "motion_alerts"
        private const val KEY_SOUND = "sound_enabled"
        private const val KEY_VIBRATION = "vibration_enabled"
        private const val KEY_STREAM_URL = "stream_url"
        private const val KEY_FCM_TOKEN = "fcm_token"

        @Volatile
        private var instance: PreferenceManager? = null

        fun getInstance(): PreferenceManager =
            instance ?: synchronized(this) {
                instance ?: PreferenceManager(IrisApplication.instance).also { instance = it }
            }
    }
}
