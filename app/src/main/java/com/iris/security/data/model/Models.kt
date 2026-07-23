package com.iris.security.data.model

import com.google.gson.annotations.SerializedName

// ─── Alert Event ─────────────────────────────────────────────────────────────

data class AlertEvent(
    val id: String,
    val type: AlertType,
    val timestamp: Long,
    val imageUrl: String?,
    val label: String,          // "Unknown Person" / "Motion Detected"
    val confidence: Float?,     // face recognition confidence 0-1
    val acknowledged: Boolean = false
) {
    enum class AlertType {
        INTRUDER_DETECTED,
        MOTION_DETECTED,
        SYSTEM_ONLINE,
        SYSTEM_OFFLINE,
        LOW_BATTERY,
        WIFI_LOST
    }
}

// ─── Device Status ────────────────────────────────────────────────────────────

data class DeviceStatus(
    val isOnline: Boolean,
    val wifiRssi: Int,          // dBm
    val uptime: Long,           // seconds
    val detectionEnabled: Boolean,
    val streamUrl: String,
    val enrolledFaces: Int,
    val firmwareVersion: String = "1.0.0"
)

// ─── MQTT Payloads ────────────────────────────────────────────────────────────

data class MqttAlertPayload(
    @SerializedName("type") val type: String,
    @SerializedName("ts") val timestamp: Long,
    @SerializedName("img") val imageUrl: String?,
    @SerializedName("conf") val confidence: Float?,
    @SerializedName("label") val label: String
)

data class MqttStatusPayload(
    @SerializedName("online") val online: Boolean,
    @SerializedName("rssi") val rssi: Int,
    @SerializedName("uptime") val uptime: Long,
    @SerializedName("detect") val detectionEnabled: Boolean,
    @SerializedName("stream") val streamUrl: String,
    @SerializedName("faces") val enrolledFaces: Int
)

data class MqttCommandPayload(
    @SerializedName("cmd") val command: String,
    @SerializedName("value") val value: Any? = null
)

// ─── User Config ──────────────────────────────────────────────────────────────

data class IrisConfig(
    val deviceIp: String,
    val mqttBroker: String,
    val mqttPort: Int = 1883,
    val mqttUser: String = "",
    val mqttPassword: String = "",
    val deviceId: String = "iris_01",
    val alertsEnabled: Boolean = true,
    val motionSensitivity: Int = 5 // 1-10
)

// ─── Statistics ───────────────────────────────────────────────────────────────

data class DashboardStats(
    val todayAlerts: Int,
    val weekAlerts: Int,
    val lastMotion: Long?,
    val systemUptime: String,
    val recognitionAccuracy: Float
)
