package com.iris.security.data.repository

import com.iris.security.data.model.AlertEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory alert repository.
 * In a production app, persist to Room DB.
 */
class AlertRepository {

    private val _alerts = MutableStateFlow<List<AlertEvent>>(emptyList())
    val alerts: StateFlow<List<AlertEvent>> = _alerts

    fun addAlert(event: AlertEvent) {
        _alerts.update { current ->
            // Keep max 200 alerts in memory
            val updated = listOf(event) + current
            if (updated.size > 200) updated.take(200) else updated
        }
    }

    fun acknowledgeAlert(id: String) {
        _alerts.update { current ->
            current.map { if (it.id == id) it.copy(acknowledged = true) else it }
        }
    }

    fun acknowledgeAll() {
        _alerts.update { current ->
            current.map { it.copy(acknowledged = true) }
        }
    }

    fun clearAll() {
        _alerts.value = emptyList()
    }

    fun getUnacknowledgedCount(): Int =
        _alerts.value.count { !it.acknowledged }

    fun getTodayAlertCount(): Int {
        val startOfDay = System.currentTimeMillis().let {
            it - (it % 86_400_000)
        }
        return _alerts.value.count { it.timestamp >= startOfDay }
    }

    fun getIntruderAlerts(): List<AlertEvent> =
        _alerts.value.filter { it.type == AlertEvent.AlertType.INTRUDER_DETECTED }
}
