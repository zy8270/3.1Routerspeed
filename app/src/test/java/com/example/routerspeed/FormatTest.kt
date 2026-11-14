package com.example.routerspeed

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormatTest {
    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun trafficUsesBinaryUnits() {
        assertEquals("0 KB", formatTraffic(0))
        assertEquals("1.00 MB", formatTraffic(1_024))
        assertEquals("1.50 MB", formatTraffic(1_536))
        assertEquals("1.00 GB", formatTraffic(1_048_576))
        assertEquals("1.50 MB/s", formatRate(1_536))
    }
}
