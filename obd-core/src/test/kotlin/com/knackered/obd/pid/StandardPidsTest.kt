package com.knackered.obd.pid

import com.knackered.obd.protocol.ElmResponseParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StandardPidsTest {
    @Test
    fun rpmFromWorkedExample() {
        val value = decode(StandardPids.rpm, "41 0C 1A F8")
        assertEquals(1726.0, value)
    }

    @Test
    fun fractionalRpmUsesFloatingDivision() {
        assertEquals(1726.25, decode(StandardPids.rpm, "41 0C 1A F9"))
    }

    @Test
    fun dashboardFormulas() {
        assertEquals(60.0, decode(StandardPids.speed, "41 0D 3C"))
        assertEquals(55.0, decode(StandardPids.coolantTemp, "41 05 5F"))
        assertEquals(30.0, decode(StandardPids.intakeAirTemp, "41 0F 46"))
        assertEquals(-40.0, decode(StandardPids.coolantTemp, "41 05 00"))
        assertEquals(128.0 * 100.0 / 255.0, decode(StandardPids.throttlePosition, "41 11 80"))
        assertEquals(128.0 * 100.0 / 255.0, decode(StandardPids.engineLoad, "41 04 80"))
    }

    @Test
    fun supportedPidBitmaskIncludesRpmAndNextRange() {
        val supported = StandardPids.supportedPids(0xBE, 0x3F, 0xA8, 0x13)
        assertTrue(StandardPids.rpm.id in supported)
        assertTrue(0x20 in supported)
        assertTrue(0x02 !in supported)
    }

    private fun decode(pid: Pid, raw: String): Double {
        val bytes = ElmResponseParser.payloadForRequest(ElmResponseParser.parse(raw, pid.command).bytes, pid.command)
        return pid.decode(bytes.drop(2).take(pid.dataLength))
    }
}
