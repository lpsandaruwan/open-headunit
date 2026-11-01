package com.andrerinas.openheadunit.aap

import com.andrerinas.openheadunit.aap.protocol.proto.Control
import org.junit.Assert.assertEquals
import org.junit.Test

class AapVersionDiagnosticsTest {
    @Test fun `summary records selection gates transport and options`() {
        val config = Control.ConnectionConfiguration.newBuilder()
            .setPingConfiguration(Control.PingConfiguration.newBuilder().setTimeoutMs(9000))
            .setWirelessTcpConfiguration(
                Control.WirelessTcpConfiguration.newBuilder()
                    .setSocketReadTimeoutMs(5000)
                    .setSocketReceiveBufferSize(65536)
                    .setSocketSendBufferSize(16384)
            )
            .build()
        val result = AapVersionNegotiation.Result(1, 6, 0, config)

        assertEquals(
            "AAP version: transport=USB announced=1.2 selected=1.6 " +
                "status=STATUS_SUCCESS accepted=true feature16=true responseBytes=31 " +
                "options={pingTimeoutMs=9000,tcpReadTimeoutMs=5000," +
                "tcpReceiveBuffer=65536,tcpSendBuffer=16384}",
            AapVersionDiagnostics.summarize(result, "usb", 31)
        )
    }

    @Test fun `summary says when the phone sent no connection options`() {
        val result = AapVersionNegotiation.Result(1, 2, 0, null)
        assertEquals(
            "AAP version: transport=WIFI announced=1.2 selected=1.2 " +
                "status=STATUS_SUCCESS accepted=true feature16=false responseBytes=12 options={none}",
            AapVersionDiagnostics.summarize(result, "wifi", 12)
        )
    }
}
