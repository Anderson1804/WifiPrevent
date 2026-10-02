package com.anderson.wifiprevent

import com.anderson.wifiprevent.domain.traffic.*
import org.junit.Assert.*
import org.junit.Test

class LabScheduleTest {
    @Test fun burstAndDistributedHaveEqualCountsButDifferentTimes() {
        val burst = labOffsets(LabProfile.BURST)
        val distributed = labOffsets(LabProfile.DISTRIBUTED)
        assertEquals(20, burst.size)
        assertEquals(burst.size, distributed.size)
        assertTrue(burst.last() <= 10_000)
        assertTrue(distributed.last() > 30_000)
        assertTrue(burst.zipWithNext().all { (a,b) -> b - a >= 500 })
    }
    @Test fun rejectsInternetAddressesAndMalformedHosts() {
        listOf("8.8.8.8", "127.0.0.1", "localhost", "172.32.1.2", "192.168.1.999", "10.1.2").forEach {
            assertFalse(it, isPrivateLabAddress(it))
        }
        listOf("10.0.2.2", "192.168.1.2", "172.16.1.2").forEach { assertTrue(isPrivateLabAddress(it)) }
    }
}
