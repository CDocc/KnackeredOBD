package com.knackered.obd.dtc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DtcDecoderTest {
    @Test
    fun formatsPowertrainChassisBodyAndNetwork() {
        assertEquals("P0133", DtcDecoder.format(0x01, 0x33))
        assertEquals("P0301", DtcDecoder.format(0x03, 0x01))
        assertEquals("P2135", DtcDecoder.format(0x21, 0x35))
        assertEquals("C0133", DtcDecoder.format(0x41, 0x33))
        assertEquals("B0011", DtcDecoder.format(0x80, 0x11))
        assertEquals("U0100", DtcDecoder.format(0xC1, 0x00))
    }

    @Test
    fun decodesStoredCodesAndSkipsPadding() {
        val codes = DtcDecoder.decode(listOf(0x43, 0x01, 0x33, 0x03, 0x01, 0x00, 0x00))
        assertEquals(listOf("P0133", "P0301"), codes.map { it.code })
        assertEquals("O2 sensor circuit slow response (bank 1 sensor 1)", codes[0].description)
        assertEquals("Cylinder 1 misfire detected", codes[1].description)
    }

    @Test
    fun emptyAndUnknown() {
        assertEquals(emptyList(), DtcDecoder.decode(listOf(0x43, 0x00, 0x00)))
        val unknown = DtcDecoder.decode(listOf(0x43, 0x15, 0x55))
        assertEquals("P1555", unknown.single().code)
        assertNull(unknown.single().description)
    }

    @Test
    fun matchesKotlinObdApiFrameCases() {
        assertEquals(
            listOf("P0003", "C1104", "B21AB", "U3106"),
            DtcDecoder.decodeRaw("4300035104A1AB\r43F10600000000").map { it.code },
        )
        assertEquals(
            listOf("P0103", "P0104", "P0105"),
            DtcDecoder.decodeRaw("43010301040105").map { it.code },
        )
        assertEquals(
            listOf("P0103", "P0104"),
            DtcDecoder.decodeRaw("43010301040000").map { it.code },
        )
        assertEquals(
            listOf("P0108", "P0118", "P0119", "P0120"),
            DtcDecoder.decodeRaw("00A\r0:430401080118\r1:011901200000").map { it.code },
        )
        assertEquals(
            listOf("P0108", "P0118", "P0119", "P0120"),
            DtcDecoder.decodeRaw("0: 43 04 01 08 01 18\r1: 01 19 01 20 00 00").map { it.code },
        )
        assertEquals(listOf("P0120", "P0121"), DtcDecoder.decodeRaw("430201200121").map { it.code })
        assertEquals(emptyList(), DtcDecoder.decodeRaw("4300"))
        assertEquals(emptyList(), DtcDecoder.decodeRaw("SEARCHING...\r03\r43 00"))
    }
}
