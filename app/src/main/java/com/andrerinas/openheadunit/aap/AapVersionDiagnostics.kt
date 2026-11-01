package com.andrerinas.openheadunit.aap

/** A stable, single-line record of version negotiation without changing protocol behaviour. */
object AapVersionDiagnostics {
    fun summarize(
        result: AapVersionNegotiation.Result,
        transport: String,
        responseFrameBytes: Int
    ): String {
        val config = result.requestedConfig
        val options = if (config == null) {
            "none"
        } else {
            val values = ArrayList<String>(5)
            if (config.hasPingConfiguration()) {
                values += "pingTimeoutMs=${config.pingConfiguration.timeoutMs}"
            }
            if (config.hasWirelessTcpConfiguration()) {
                val tcp = config.wirelessTcpConfiguration
                values += "tcpReadTimeoutMs=${tcp.socketReadTimeoutMs}"
                values += "tcpReceiveBuffer=${tcp.socketReceiveBufferSize}"
                values += "tcpSendBuffer=${tcp.socketSendBufferSize}"
            }
            if (values.isEmpty()) "empty" else values.joinToString(",")
        }

        return "AAP version: transport=${transport.uppercase()} " +
            "announced=${AapVersionNegotiation.ANNOUNCED_MAJOR}.${AapVersionNegotiation.ANNOUNCED_MINOR} " +
            "selected=${result.major}.${result.minor} status=${result.statusName} " +
            "accepted=${result.accepted} feature16=${result.supports16} " +
            "responseBytes=$responseFrameBytes options={$options}"
    }
}
