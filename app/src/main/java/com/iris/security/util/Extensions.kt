package com.iris.security.util

import android.content.Context
import android.content.res.Resources
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import com.iris.security.data.model.AlertEvent
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

// ─── View Extensions ─────────────────────────────────────────────────────────

fun View.visible() { visibility = View.VISIBLE }
fun View.invisible() { visibility = View.INVISIBLE }
fun View.gone() { visibility = View.GONE }
fun View.isVisible() = visibility == View.VISIBLE

// ─── Context Extensions ──────────────────────────────────────────────────────

fun Context.toast(message: String, long: Boolean = false) {
    Toast.makeText(this, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun Fragment.toast(message: String, long: Boolean = false) {
    requireContext().toast(message, long)
}

// ─── Time Formatting ─────────────────────────────────────────────────────────

fun Long.toTimeAgo(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < TimeUnit.MINUTES.toMillis(1) -> "Just now"
        diff < TimeUnit.HOURS.toMillis(1) -> "${diff / TimeUnit.MINUTES.toMillis(1)}m ago"
        diff < TimeUnit.DAYS.toMillis(1) -> "${diff / TimeUnit.HOURS.toMillis(1)}h ago"
        diff < TimeUnit.DAYS.toMillis(7) -> "${diff / TimeUnit.DAYS.toMillis(1)}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(this))
        }
    }
}

fun Long.toFormattedDateTime(): String {
    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFormattedTime(): String {
    val sdf = SimpleDateFormat("h:mm:ss a", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toUptimeString(): String {
    val hours = TimeUnit.SECONDS.toHours(this)
    val minutes = TimeUnit.SECONDS.toMinutes(this) % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

// ─── RSSI Signal Quality ─────────────────────────────────────────────────────

fun Int.toSignalStrength(): String = when {
    this >= -50 -> "Excellent"
    this >= -60 -> "Good"
    this >= -70 -> "Fair"
    this >= -80 -> "Poor"
    else -> "Very Poor"
}

fun Int.toSignalBars(): Int = when {
    this >= -50 -> 4
    this >= -60 -> 3
    this >= -70 -> 2
    this >= -80 -> 1
    else -> 0
}

// ─── Alert Helpers ───────────────────────────────────────────────────────────

fun AlertEvent.AlertType.toDisplayLabel(): String = when (this) {
    AlertEvent.AlertType.INTRUDER_DETECTED -> "Intruder Detected"
    AlertEvent.AlertType.MOTION_DETECTED -> "Motion Detected"
    AlertEvent.AlertType.SYSTEM_ONLINE -> "System Online"
    AlertEvent.AlertType.SYSTEM_OFFLINE -> "System Offline"
    AlertEvent.AlertType.LOW_BATTERY -> "Low Battery"
    AlertEvent.AlertType.WIFI_LOST -> "Wi-Fi Lost"
}

fun AlertEvent.AlertType.isUrgent(): Boolean =
    this == AlertEvent.AlertType.INTRUDER_DETECTED ||
    this == AlertEvent.AlertType.SYSTEM_OFFLINE

// ─── DP/PX conversion ────────────────────────────────────────────────────────

val Int.dp: Int get() = (this * Resources.getSystem().displayMetrics.density).toInt()

// ─── LiveData once observer ──────────────────────────────────────────────────

fun <T> LiveData<T>.observeOnce(owner: LifecycleOwner, observer: (T) -> Unit) {
    observe(owner, object : Observer<T> {
        override fun onChanged(value: T) {
            observer(value)
            removeObserver(this)
        }
    })
}
