package com.anderson.wifiprevent.data.vpn

import java.io.File

data class HevTunnelConfig(
    val tunnelIpv4: String = "10.77.0.2",
    val tunnelIpv6: String = "fd00:77::2",
    val mtu: Int = 1500,
    val socksHost: String = SocksRelayProbe.EMULATOR_HOST,
    val socksPort: Int = SocksRelayProbe.DEFAULT_PORT
) {
    init {
        require(mtu in 1280..9000)
        require(socksPort in 1..65535)
        require(socksHost.matches(HOST_PATTERN))
    }

    fun serialize(): String = """
        tunnel:
          mtu: $mtu
          ipv4: '$tunnelIpv4'
          ipv6: '$tunnelIpv6'
          icmp: 'reply'
        socks5:
          address: '$socksHost'
          port: $socksPort
          udp: 'udp'
          udp-address: '$socksHost'
        misc:
          connect-timeout: 10000
          tcp-read-write-timeout: 300000
          udp-read-write-timeout: 60000
          log-file: null
          log-level: warn
    """.trimIndent() + "\n"

    fun writeTo(directory: File): File {
        require(directory.exists() || directory.mkdirs()) {
            "No se pudo crear el directorio privado del túnel."
        }
        return File(directory, FILE_NAME).apply {
            writeText(serialize(), Charsets.UTF_8)
        }
    }

    companion object {
        const val FILE_NAME = "hev-tunnel.yml"
        private val HOST_PATTERN = Regex("^[A-Za-z0-9.:-]+$")
    }
}
