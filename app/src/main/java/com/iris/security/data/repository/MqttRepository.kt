package com.iris.security.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.iris.security.data.model.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttRepository(private val context: Context) {

    private var client: MqttClient? = null
    private val gson = Gson()

    // ─── Observable state ────────────────────────────────────────────────────

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _alerts = MutableSharedFlow<AlertEvent>(replay = 0, extraBufferCapacity = 50)
    val alerts: SharedFlow<AlertEvent> = _alerts

    private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus

    // ─── Topics ──────────────────────────────────────────────────────────────

    private fun alertTopic(deviceId: String) = "iris/$deviceId/alert"
    private fun statusTopic(deviceId: String) = "iris/$deviceId/status"
    private fun cmdTopic(deviceId: String) = "iris/$deviceId/cmd"

    // ─── Connection ──────────────────────────────────────────────────────────

    fun connect(config: IrisConfig) {
        try {
            _connectionState.value = ConnectionState.CONNECTING

            val serverUri = "ssl://${config.mqttBroker}:${config.mqttPort}"
            client = MqttClient(serverUri, "iris_android_${System.currentTimeMillis()}", MemoryPersistence())

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 30
                isAutomaticReconnect = true
                if (config.mqttUser.isNotEmpty()) {
                    userName = config.mqttUser
                    password = config.mqttPassword.toCharArray()
                }
                // Last-will: device offline message
                setWill(
                    statusTopic(config.deviceId),
                    gson.toJson(MqttStatusPayload(
                        online = false, rssi = 0, uptime = 0,
                        detectionEnabled = false, streamUrl = "", enrolledFaces = 0
                    )).toByteArray(),
                    1, true
                )
            }

            client?.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    _connectionState.value = ConnectionState.CONNECTED
                    subscribeToTopics(config.deviceId)
                    Log.d(TAG, "MQTT connected${if (reconnect) " (reconnect)" else ""}")
                }

                override fun connectionLost(cause: Throwable?) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    Log.w(TAG, "MQTT connection lost: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    message?.payload?.let { payload ->
                        handleMessage(topic ?: "", String(payload), config.deviceId)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client?.connect(options)

        } catch (e: MqttException) {
            _connectionState.value = ConnectionState.ERROR
            Log.e(TAG, "MQTT connect error: reasonCode=${e.reasonCode} message=${e.message} cause=${e.cause}", e)
        }
    }

    private fun subscribeToTopics(deviceId: String) {
        try {
            client?.subscribe(alertTopic(deviceId), 1)
            client?.subscribe(statusTopic(deviceId), 1)
            Log.d(TAG, "Subscribed to IRIS topics for device: $deviceId")
        } catch (e: MqttException) {
            Log.e(TAG, "Subscribe error: ${e.message}")
        }
    }

    private fun handleMessage(topic: String, payload: String, deviceId: String) {
        try {
            when {
                topic == alertTopic(deviceId) -> {
                    val alert = gson.fromJson(payload, MqttAlertPayload::class.java)
                    val event = AlertEvent(
                        id = "${alert.timestamp}_${alert.type}",
                        type = mapAlertType(alert.type),
                        timestamp = alert.timestamp,
                        imageUrl = alert.imageUrl,
                        label = alert.label,
                        confidence = alert.confidence
                    )
                    _alerts.tryEmit(event)
                    Log.d(TAG, "Alert received: ${event.type} - ${event.label}")
                }
                topic == statusTopic(deviceId) -> {
                    val status = gson.fromJson(payload, MqttStatusPayload::class.java)
                    _deviceStatus.value = DeviceStatus(
                        isOnline = status.online,
                        wifiRssi = status.rssi,
                        uptime = status.uptime,
                        detectionEnabled = status.detectionEnabled,
                        streamUrl = status.streamUrl,
                        enrolledFaces = status.enrolledFaces
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MQTT message on $topic: ${e.message}")
        }
    }

    private fun mapAlertType(type: String): AlertEvent.AlertType = when (type) {
        "intruder" -> AlertEvent.AlertType.INTRUDER_DETECTED
        "motion" -> AlertEvent.AlertType.MOTION_DETECTED
        "online" -> AlertEvent.AlertType.SYSTEM_ONLINE
        "offline" -> AlertEvent.AlertType.SYSTEM_OFFLINE
        "battery" -> AlertEvent.AlertType.LOW_BATTERY
        "wifi_lost" -> AlertEvent.AlertType.WIFI_LOST
        else -> AlertEvent.AlertType.MOTION_DETECTED
    }

    // ─── Commands to ESP32-S3 ─────────────────────────────────────────────────

    fun sendCommand(deviceId: String, command: String, value: Any? = null) {
        try {
            val payload = gson.toJson(MqttCommandPayload(command, value))
            val msg = MqttMessage(payload.toByteArray()).apply { qos = 1 }
            client?.publish(cmdTopic(deviceId), msg)
            Log.d(TAG, "Command sent: $command")
        } catch (e: MqttException) {
            Log.e(TAG, "Command send error: ${e.message}", e)
        }
    }

    fun enableDetection(deviceId: String) = sendCommand(deviceId, "detect_on")
    fun disableDetection(deviceId: String) = sendCommand(deviceId, "detect_off")
    fun triggerAlarm(deviceId: String) = sendCommand(deviceId, "alarm_on")
    fun silenceAlarm(deviceId: String) = sendCommand(deviceId, "alarm_off")
    fun requestStatus(deviceId: String) = sendCommand(deviceId, "status")
    fun rebootDevice(deviceId: String) = sendCommand(deviceId, "reboot")

    fun disconnect() {
        try {
            client?.disconnect()
            client = null
            _connectionState.value = ConnectionState.DISCONNECTED
        } catch (e: MqttException) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        }
    }

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

    companion object {
        private const val TAG = "MqttRepository"
    }
}
