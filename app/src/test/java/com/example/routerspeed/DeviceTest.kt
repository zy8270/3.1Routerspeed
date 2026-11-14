package com.example.routerspeed

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceTest {
    @Test
    fun currentSpeedIsAccumulatedAndSortedByActivity() {
        val tracker = DeviceActivityTracker()
        val initial = listOf(
            device(name = "A", up = 100, down = 0, tx = 999_999, rx = 999_999),
            device(name = "B", up = 50, down = 0, tx = 0, rx = 0),
        )

        val first = tracker.update(initial, now = 1_000)
        assertEquals(listOf("A", "B"), first.map { it.device.displayName })
        assertEquals(0.0, first.first().activityKBytes, 0.001)

        val second = tracker.update(
            listOf(
                device(name = "A", up = 100, down = 0),
                device(name = "B", up = 150, down = 50),
            ),
            now = 4_000,
        )

        assertEquals(listOf("B", "A"), second.map { it.device.displayName })
        assertEquals(600.0, second[0].activityKBytes, 0.001)
        assertEquals(300.0, second[1].activityKBytes, 0.001)
    }

    @Test
    fun resetTimingPreservesTotalsWithoutFillingTheNetworkGap() {
        val tracker = DeviceActivityTracker()
        val device = device(name = "A", up = 100, down = 20)
        tracker.update(listOf(device), now = 0)
        tracker.update(listOf(device), now = 1_000)

        tracker.resetTiming()
        val afterReconnect = tracker.update(listOf(device), now = 11_000)

        assertEquals(120.0, afterReconnect.single().activityKBytes, 0.001)
    }

    @Test
    fun historyTracksAllDevicesAndRouterAndTrimsOldSamples() {
        val history = SpeedHistory()
        history.record(
            listOf(
                device(name = "A", up = 10, down = 20),
                device(name = "B", up = 5, down = 7),
            ),
            now = 0,
        )
        val second = history.record(
            listOf(
                device(name = "A", up = 20, down = 10),
                device(name = "B", up = 10, down = 10),
            ),
            now = 3_000,
        )

        assertEquals(2, second.getValue("A").size)
        assertEquals(2, second.getValue("B").size)
        assertEquals(30, second.getValue(ROUTER_HISTORY_KEY).last().upRate)
        assertEquals(20, second.getValue(ROUTER_HISTORY_KEY).last().downRate)

        val trimmed = history.record(
            listOf(
                device(name = "A", up = 1, down = 1),
                device(name = "B", up = 2, down = 2),
            ),
            now = SPEED_HISTORY_WINDOW_MILLIS + 1,
        )
        assertEquals(
            listOf(3_000L, SPEED_HISTORY_WINDOW_MILLIS + 1),
            trimmed.getValue("A").map { it.timestamp },
        )
    }

    private fun device(
        name: String,
        up: Long,
        down: Long,
        tx: Long = 0,
        rx: Long = 0,
    ) = Device(
        id = name,
        hostName = name,
        actualName = "",
        ipAddress = "",
        macAddress = name,
        active = true,
        interfaceType = "Wireless",
        frequency = "5GHz",
        upRate = up,
        downRate = down,
        txKBytes = tx,
        rxKBytes = rx,
    )
}
