package com.knackered.obd.session

import com.knackered.obd.pid.PidReading
import com.knackered.obd.protocol.ReadOnlyPolicy
import com.knackered.obd.pid.PidUnavailableReason
import com.knackered.obd.pid.StandardPids
import com.knackered.obd.transport.FakeObdTransport
import com.knackered.obd.transport.InMemoryObdLog
import com.knackered.obd.transport.ObdDirection
import com.knackered.obd.transport.ObdNotAllowedException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.ZERO

class ObdClientTest {
    @Test
    fun initReadsLiveDataAndDecodesStoredCodes() = runBlocking {
        val log = InMemoryObdLog()
        val transport = FakeObdTransport()
        val client = client(transport, log)
        transport.connect()

        val init = client.initialize()
        assertEquals("ELM327 v1.5", init.adapterIdentity)
        assertTrue(init.ecuResponding)
        assertEquals("ISO 15765-4 (CAN 11/500)", init.protocol)
        assertTrue(StandardPids.rpm.id in init.supportedPids)

        val rpm = client.readPid(StandardPids.rpm) as PidReading.Value
        assertEquals(1726.0, rpm.reading.value)
        val speed = client.readPid(StandardPids.speed) as PidReading.Value
        assertEquals(60.0, speed.reading.value)

        val codes = client.readStoredDtcs()
        assertEquals(listOf("P0133", "P0301"), codes.map { it.code })

        val cleared = client.clearStoredDtcs()
        assertTrue(cleared.confirmed)

        val entries = log.snapshot()
        assertTrue(entries.any { it.direction == ObdDirection.SENT && it.text == "ATZ" })
        assertTrue(entries.any { it.direction == ObdDirection.RECEIVED && it.text.contains("1A F8") })
        assertEquals(
            listOf("", "ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "0100", "ATDP", "010C", "010D", "03", "04"),
            transport.sent(),
        )
    }

    @Test
    fun adapterUpButEcuSilentIsNotAHardFailure() = runBlocking {
        val transport = FakeObdTransport(
            FakeObdTransport.demoScript() + mapOf("0100" to listOf("SEARCHING...\rUNABLE TO CONNECT")),
        )
        transport.connect()
        val init = client(transport).initialize()
        assertFalse(init.ecuResponding)
        assertTrue(init.supportedPids.isEmpty())
        assertEquals(null, init.protocol)
    }

    @Test
    fun noDataPidDoesNotThrow() = runBlocking {
        val transport = FakeObdTransport(
            FakeObdTransport.demoScript() + mapOf("010C" to listOf("NO DATA")),
        )
        transport.connect()
        val reading = client(transport).readPid(StandardPids.rpm) as PidReading.Unavailable
        assertEquals(PidUnavailableReason.NO_DATA, reading.reading.reason)
    }

    @Test
    fun negativeResponseDoesNotThrowDuringPoll() = runBlocking {
        val transport = FakeObdTransport(
            FakeObdTransport.demoScript() + mapOf("010C" to listOf("7F 01 11")),
        )
        transport.connect()
        val reading = client(transport).readPid(StandardPids.rpm) as PidReading.Unavailable
        assertEquals(PidUnavailableReason.NOT_SUPPORTED, reading.reading.reason)
    }

    @Test
    fun pollEmitsAReading() = runBlocking {
        val transport = FakeObdTransport()
        transport.connect()
        val reading = client(transport).poll(listOf(StandardPids.coolantTemp)).first() as PidReading.Value
        assertEquals(55.0, reading.reading.value)
    }

    @Test
    fun terminalSendsAllowedRawCommandsAndRefusesTheRest() = runBlocking {
        val log = InMemoryObdLog()
        val transport = FakeObdTransport(
            FakeObdTransport.demoScript() + mapOf("0902" to listOf("49 02 01 31 32 33")),
        )
        transport.connect()
        val session = client(transport, log)

        val rpm = session.submitTerminal("01 0C")
        assertTrue(rpm.sent)
        assertEquals("010C", rpm.command)
        assertTrue(rpm.raw.contains("1A F8"))

        val vin = session.submitTerminal("0902")
        assertTrue(vin.sent)
        assertTrue(vin.raw.contains("49 02"))

        val header = session.submitTerminal("ATSH7E0")
        assertTrue(header.sent)
        assertEquals("?", header.raw)

        val clear = session.submitTerminal("04")
        assertFalse(clear.sent)
        assertTrue(clear.message!!.contains("Mode 04"))

        val uds = session.submitTerminal("22F190")
        assertFalse(uds.sent)
        assertTrue(uds.message!!.contains("Service 22"))

        assertEquals(listOf("010C", "0902", "ATSH7E0"), transport.sent())
        assertTrue(log.snapshot().any { it.direction == ObdDirection.INFO && it.text.contains("04") })
        assertFailsWith<ObdNotAllowedException> { ReadOnlyPolicy.check("ATSH7E0") }
    }

    @Test
    fun mode04IsNotAGeneralCommand() {
        assertFailsWith<ObdNotAllowedException> {
            com.knackered.obd.protocol.ReadOnlyPolicy.check("04")
        }
    }

    private fun client(transport: FakeObdTransport, log: InMemoryObdLog = InMemoryObdLog()) = ObdClient(
        transport = transport,
        logger = log,
        resetDelay = ZERO,
        commandGap = ZERO,
        vehicleTimeout = 2.seconds,
    )
}
