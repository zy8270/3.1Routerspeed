package com.example.routerspeed

import java.util.Locale

data class Device(
    val id: String,
    val hostName: String,
    val actualName: String,
    val ipAddress: String,
    val macAddress: String,
    val active: Boolean,
    val interfaceType: String,
    val frequency: String,
    val upRate: Long,
    val downRate: Long,
    val txKBytes: Long,
    val rxKBytes: Long,
) {
    val displayName: String
        get() = actualName.ifBlank { hostName }.ifBlank { macAddress }.ifBlank { ipAddress }

    val trackingKey: String
        get() = macAddress.ifBlank { id }.ifBlank { ipAddress }.ifBlank { hostName }

    val currentRate: Long
        get() = upRate.coerceAtLeast(0) + downRate.coerceAtLeast(0)
}

data class ActiveDevice(
    val device: Device,
    val activityKBytes: Double,
)

const val ROUTER_HISTORY_KEY = "__router_total__"
const val SPEED_HISTORY_WINDOW_MILLIS = 10 * 60 * 1_000L

data class SpeedPoint(
    val timestamp: Long,
    val upRate: Long,
    val downRate: Long,
)

class SpeedHistory {
    private val histories = mutableMapOf<String, MutableList<SpeedPoint>>()

    @Synchronized
    fun record(devices: List<Device>, now: Long): Map<String, List<SpeedPoint>> {
        devices.forEach { device ->
            histories.getOrPut(device.trackingKey) { mutableListOf() }.add(
                SpeedPoint(now, device.upRate.coerceAtLeast(0), device.downRate.coerceAtLeast(0)),
            )
        }
        histories.getOrPut(ROUTER_HISTORY_KEY) { mutableListOf() }.add(
            SpeedPoint(
                timestamp = now,
                upRate = devices.sumOf { it.upRate.coerceAtLeast(0) },
                downRate = devices.sumOf { it.downRate.coerceAtLeast(0) },
            ),
        )

        val cutoff = now - SPEED_HISTORY_WINDOW_MILLIS
        histories.values.forEach { points ->
            points.removeAll { it.timestamp < cutoff }
        }
        return histories.mapValues { it.value.toList() }
    }

    @Synchronized
    fun reset() {
        histories.clear()
    }
}

class DeviceActivityTracker {
    private val totals = mutableMapOf<String, Double>()
    private var previousKeys = emptySet<String>()
    private var lastSampleAt: Long? = null

    @Synchronized
    fun update(devices: List<Device>, now: Long): List<ActiveDevice> {
        val elapsedSeconds = lastSampleAt?.let { (now - it).coerceAtLeast(0) / 1_000.0 } ?: 0.0
        val hadPreviousSample = lastSampleAt != null

        devices.forEach { device ->
            val key = device.trackingKey
            val increment = if (hadPreviousSample && key in previousKeys) {
                device.currentRate * elapsedSeconds
            } else {
                0.0
            }
            totals[key] = (totals[key] ?: 0.0) + increment
        }

        lastSampleAt = now
        previousKeys = devices.mapTo(mutableSetOf()) { it.trackingKey }
        return devices.map { device ->
            ActiveDevice(device, totals[device.trackingKey] ?: 0.0)
        }.sortedWith(
            compareByDescending<ActiveDevice> { it.activityKBytes }
                .thenByDescending { it.device.currentRate }
                .thenBy { it.device.displayName },
        )
    }

    @Synchronized
    fun resetTiming() {
        lastSampleAt = null
        previousKeys = emptySet()
    }

    @Synchronized
    fun reset() {
        totals.clear()
        resetTiming()
    }
}

fun formatTraffic(kilobytes: Long): String = formatTraffic(kilobytes.toDouble())

fun formatTraffic(kilobytes: Double): String {
    val units = arrayOf("KB", "MB", "GB", "TB", "PB")
    var value = kilobytes.coerceAtLeast(0.0)
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val decimals = when {
        unitIndex == 0 -> 0
        value >= 100 -> 1
        else -> 2
    }
    return String.format(Locale.getDefault(), "%.${decimals}f %s", value, units[unitIndex])
}

fun formatRate(kilobytesPerSecond: Long): String = "${formatTraffic(kilobytesPerSecond)}/s"
