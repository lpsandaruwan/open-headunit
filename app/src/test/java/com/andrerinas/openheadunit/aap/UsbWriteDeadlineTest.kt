package com.andrerinas.openheadunit.aap

import android.os.SystemClock
import com.andrerinas.openheadunit.connection.projection.ProjectionConnection
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.*

class UsbWriteDeadlineTest {
    private fun exercise(type: ProjectionConnection.Type, delayMs: Int, partial: Boolean = false): Int =
        Mockito.mockStatic(SystemClock::class.java).use {
            val transport = mock<AapTransport>(defaultAnswer = Mockito.CALLS_REAL_METHODS)
            val connection = mock<ProjectionConnection>()
            whenever(connection.type).thenReturn(type)
            // Simulate a peer that resumes servicing bulk OUT after delayMs, without wall-clock sleeps.
            whenever(connection.sendBlocking(any(), any(), any())).thenAnswer { call ->
                val budget = call.getArgument<Int>(2)
                if (delayMs > budget) -7 else if (partial) 12 else call.getArgument<Int>(1)
            }
            AapTransport::class.java.getDeclaredField("connection").apply { isAccessible = true }
                .set(transport, connection)
            val bytes = ByteArray(39)
            val result = transport.writeTlsFrame(bytes, bytes.size)
            verify(connection, times(1)).sendBlocking(same(bytes), eq(39), any())
            result
        }

    @Test fun usbCanCompleteAfterTheFormerQuarterSecondDeadline() {
        assertEquals(39, exercise(ProjectionConnection.Type.USB, 700))
        assertEquals(39, exercise(ProjectionConnection.Type.USB, 2000))
    }

    @Test fun unresponsiveUsbStillFailsWithinABoundedBudgetWithoutRetry() {
        assertEquals(-7, exercise(ProjectionConnection.Type.USB, 3000))
    }

    @Test fun partialCiphertextIsNotReplayedOrReportedAsSuccess() {
        assertEquals(12, exercise(ProjectionConnection.Type.USB, 400, partial = true))
    }

    @Test fun wifiKeepsItsExistingDeadline() {
        assertEquals(-7, exercise(ProjectionConnection.Type.WIFI, 700))
    }
}
