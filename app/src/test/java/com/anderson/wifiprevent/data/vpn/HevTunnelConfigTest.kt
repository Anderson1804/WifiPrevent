package com.anderson.wifiprevent.data.vpn

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class HevTunnelConfigTest {
    @Test
    fun configUsesSameTunnelAndDevelopmentRelayAddresses() {
        val content = HevTunnelConfig().serialize()

        assertTrue(content.contains("ipv4: '10.77.0.2'"))
        assertTrue(content.contains("ipv6: 'fd00:77::2'"))
        assertTrue(content.contains("address: '10.0.2.2'"))
        assertTrue(content.contains("port: 1080"))
        assertTrue(content.contains("udp: 'udp'"))
        assertTrue(content.contains("udp-address: '10.0.2.2'"))
    }

    @Test
    fun configDisablesNativeTrafficLogs() {
        val content = HevTunnelConfig().serialize()

        assertTrue(content.contains("log-file: null"))
        assertFalse(content.contains("username"))
        assertFalse(content.contains("password"))
    }

    @Test
    fun configIsWrittenInsideProvidedPrivateDirectory() {
        val directory = Files.createTempDirectory("wifiprevent-hev").toFile()
        try {
            val file = HevTunnelConfig().writeTo(directory)

            assertTrue(file.parentFile == directory)
            assertTrue(file.readText().contains("socks5:"))
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun unsafeHostCannotBeInsertedIntoYaml() {
        HevTunnelConfig(socksHost = "10.0.2.2\npassword: exposed")
    }
}
