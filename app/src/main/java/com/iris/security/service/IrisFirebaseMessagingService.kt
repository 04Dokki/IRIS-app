package com.iris.security.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.iris.security.IrisApplication
import com.iris.security.R
import com.iris.security.ui.dashboard.MainActivity
import com.iris.security.util.PreferenceManager

class IrisFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PreferenceManager.getInstance().fcmToken = token
        // In production: send token to your backend/ESP32 server
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val prefs = PreferenceManager.getInstance()
        if (!prefs.alertsEnabled) return

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "IRIS Alert"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Motion or intruder detected"

        val type = message.data["type"] ?: "motion"
        val isIntruder = type == "intruder"

        sendNotification(title, body, isIntruder)
    }

    private fun sendNotification(title: String, body: String, urgent: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (urgent) IrisApplication.CHANNEL_ALERTS else IrisApplication.CHANNEL_MOTION
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (urgent) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_DEFAULT
            )

        if (urgent) {
            builder.setColor(getColor(R.color.iris_danger))
            builder.setVibrate(longArrayOf(0, 400, 200, 400, 200, 400))
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
