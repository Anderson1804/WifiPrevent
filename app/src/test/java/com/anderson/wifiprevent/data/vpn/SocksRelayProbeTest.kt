package com.anderson.wifiprevent.data.vpn

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun fullCaptureProbeRequestsUdpAssociation() {
        assertArrayEquals(
            byteArrayOf(0x05, 0x03, 0x00, 0x01, 0, 0, 0, 0, 0, 0),
            SocksRelayProbe.UDP_ASSOCIATE_REQUEST
        )
    }

    @Test
    fun acceptsSuccessfulIpv4UdpAssociationReply() {
        assertTrue(
            validateUdpAssociateReply(
                byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0x20, 0x00)
            )
        )
    }

    @Test
    fun rejectsFailedOrIncompleteUdpAssociationReply() {
        assertFalse(
            validateUdpAssociateReply(
                byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0)
            )
        )
        assertFalse(validateUdpAssociateReply(byteArrayOf(0x05, 0x00, 0x00, 0x01)))
    }
}
