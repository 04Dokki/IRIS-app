package com.iris.security.ui.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iris.security.data.model.AlertEvent
import com.iris.security.data.model.DeviceStatus
import com.iris.security.data.repository.AlertRepository
import com.iris.security.data.repository.MqttRepository
import com.iris.security.util.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferenceManager.getInstance()
    val mqttRepo = MqttRepository(application)
    val alertRepo = AlertRepository()

    // ─── Exposed state ───────────────────────────────────────────────────────

    val connectionState = mqttRepo.connectionState
    val deviceStatus: StateFlow<DeviceStatus?> = mqttRepo.deviceStatus

    val alerts: StateFlow<List<AlertEvent>> = alertRepo.alerts

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount

    // ─── Detection arm/disarm state ──────────────────────────────────────────

    private val _detectionArmed = MutableStateFlow(true)
    val detectionArmed: StateFlow<Boolean> = _detectionArmed

    // ─── Init ────────────────────────────────────────────────────────────────

    init {
        connectMqtt()
        collectAlerts()
        collectStatus()
    }

    private fun connectMqtt() {
        val config = prefs.config ?: return
        viewModelScope.launch(Dispatchers.IO) {
            mqttRepo.connect(config)
        }
    }

    private fun collectAlerts() {
        viewModelScope.launch {
            mqttRepo.alerts.collect { event ->
                alertRepo.addAlert(event)
                _unreadCount.value = alertRepo.getUnacknowledgedCount()
            }
        }
    }

    private fun collectStatus() {
        viewModelScope.launch {
            mqttRepo.deviceStatus.collect { status ->
                status?.let {
                    _detectionArmed.value = it.detectionEnabled
                }
            }
        }
    }

    // ─── Commands ────────────────────────────────────────────────────────────

    fun toggleDetection() {
        val config = prefs.config ?: return
        val deviceId = config.deviceId
        if (_detectionArmed.value) {
            _detectionArmed.value = false
            viewModelScope.launch(Dispatchers.IO) { mqttRepo.disableDetection(deviceId) }
        } else {
            _detectionArmed.value = true
            viewModelScope.launch(Dispatchers.IO) { mqttRepo.enableDetection(deviceId) }
        }
    }

    fun triggerAlarm() {
        val deviceId = prefs.config?.deviceId ?: return
        viewModelScope.launch(Dispatchers.IO) { mqttRepo.triggerAlarm(deviceId) }
    }

    fun silenceAlarm() {
        val deviceId = prefs.config?.deviceId ?: return
        viewModelScope.launch(Dispatchers.IO) { mqttRepo.silenceAlarm(deviceId) }
    }

    fun acknowledgeAll() {
        alertRepo.acknowledgeAll()
        _unreadCount.value = 0
    }

    fun acknowledgeAlert(id: String) {
        alertRepo.acknowledgeAlert(id)
        _unreadCount.value = alertRepo.getUnacknowledgedCount()
    }

    fun refreshStatus() {
        val deviceId = prefs.config?.deviceId ?: return
        viewModelScope.launch(Dispatchers.IO) { mqttRepo.requestStatus(deviceId) }
    }

    fun getStreamUrl(): String = deviceStatus.value?.streamUrl
        ?: prefs.lastKnownStreamUrl
        ?: prefs.config?.let { "http://${it.deviceIp}:81/stream" }
        ?: ""

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch(Dispatchers.IO) { mqttRepo.disconnect() }
    }
}
