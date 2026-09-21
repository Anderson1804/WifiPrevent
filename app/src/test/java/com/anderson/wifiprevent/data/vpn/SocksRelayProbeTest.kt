package com.anderson.wifiprevent.data.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SocksRelayProbeTest {
    @Test
    fun greetingRequestsSocks5WithoutAuthentication() {
        assertArrayEquals(
            byteArrayOf(0x05, 0x01, 0x00),
            SocksRelayProbe.GREETING
        )
    }

    @Test
    fun developmentEndpointUsesAndroidEmulatorHostBridge() {
        assertEquals("10.0.2.2", SocksRelayProbe.EMULATOR_HOST)
        assertEquals(1080, SocksRelayProbe.DEFAULT_PORT)
    }
}
