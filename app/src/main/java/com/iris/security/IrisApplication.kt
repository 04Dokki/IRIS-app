package com.iris.security

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.iris.security.util.PreferenceManager

class IrisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // High-priority intruder alert channel
        val alertChannel = NotificationChannel(
            CHANNEL_ALERTS,
            "Intruder Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Urgent alerts when IRIS detects an unrecognized intruder"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 300, 200, 300, 200, 300)
            enableLights(true)
            lightColor = 0xFFE53935.toInt()
        }

        // General status channel
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            "System Status",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "IRIS background monitoring status"
        }

        // Motion detection channel
        val motionChannel = NotificationChannel(
            CHANNEL_MOTION,
            "Motion Detected",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications when motion is detected"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 150, 100, 150)
        }

        manager.createNotificationChannels(
            listOf(alertChannel, statusChannel, motionChannel)
        )
    }

    companion object {
        lateinit var instance: IrisApplication
            private set

        const val CHANNEL_ALERTS = "iris_alerts"
        const val CHANNEL_STATUS = "iris_status"
        const val CHANNEL_MOTION = "iris_motion"
    }
}
