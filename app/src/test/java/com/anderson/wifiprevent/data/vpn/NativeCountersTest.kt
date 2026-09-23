package com.anderson.wifiprevent.data.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeCountersTest {
    @Test
    fun mapsNativeTunDirectionAndUnits() {
        val counters = nativeCounters(longArrayOf(12, 1200, 7, 700))

        assertEquals(12L, counters?.transmittedPackets)
        assertEquals(1200L, counters?.transmittedBytes)
        assertEquals(7L, counters?.receivedPackets)
        assertEquals(700L, counters?.receivedBytes)
    }

    @Test
    fun rejectsIncompleteNativeStats() {
        assertNull(nativeCounters(longArrayOf(1, 2, 3)))
    }
}
