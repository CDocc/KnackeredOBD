package com.knackered.obd.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElmResponseParserTest {
    @Test
    fun rpmFrameBytes() {
        val parsed = ElmResponseParser.parse("41 0C 1A F8", "010C")
        assertEquals(ElmKind.DATA, parsed.kind)
        assertEquals(listOf(0x41, 0x0C, 0x1A, 0xF8), parsed.bytes)
    }

    @Test
    fun stripsEchoSearchingAndPrompt() {
        val raw = "010C\rSEARCHING...\r41 0C 1A F8\r>"
        val parsed = ElmResponseParser.parse(raw, "010C")
        assertEquals(listOf(0x41, 0x0C, 0x1A, 0xF8), ElmResponseParser.payloadForRequest(parsed.bytes, "010C"))
    }

    @Test
    fun spacedEchoAndBusInit() {
        val raw = "01 0C\rBUS INIT...\r41 0C 1A F8"
        val parsed = ElmResponseParser.parse(raw, "010C")
        assertEquals(ElmKind.DATA, parsed.kind)
        assertEquals(listOf(0x41, 0x0C, 0x1A, 0xF8), parsed.bytes)
    }

    @Test
    fun unspacedHexAndCanHeader() {
        assertEquals(
            listOf(0x41, 0x0C, 0x1A, 0xF8),
            ElmResponseParser.payloadForRequest(ElmResponseParser.parse("410C1AF8", "010C").bytes, "010C"),
        )
        val headed = ElmResponseParser.parse("7E8 04 41 0C 1A F8", "010C")
        assertEquals(
            listOf(0x41, 0x0C, 0x1A, 0xF8),
            ElmResponseParser.payloadForRequest(headed.bytes, "010C"),
        )
    }

    @Test
    fun numberedMultiFrameKeepsOrder() {
        val raw = "0: 43 01 33 03 01\r1: 00 00 00 00 00"
        val parsed = ElmResponseParser.parse(raw, "03")
        assertEquals(
            listOf(0x43, 0x01, 0x33, 0x03, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00),
            parsed.bytes,
        )
    }

    @Test
    fun errorTokensWinOverTrailingNoise() {
        assertEquals(ElmKind.NO_DATA, ElmResponseParser.parse("SEARCHING...\rNO DATA", "010C").kind)
        assertEquals(ElmKind.UNABLE_TO_CONNECT, ElmResponseParser.parse("UNABLE TO CONNECT").kind)
        assertEquals(ElmKind.BUS_ERROR, ElmResponseParser.parse("BUS INIT: ERROR").kind)
        assertEquals(ElmKind.STOPPED, ElmResponseParser.parse("STOPPED").kind)
        assertEquals(ElmKind.MISUNDERSTOOD, ElmResponseParser.parse("?").kind)
        assertEquals(ElmKind.OK, ElmResponseParser.parse("ATE0\rOK", "ATE0").kind)
        assertEquals(ElmKind.OK, ElmResponseParser.parse("ATE0OK", "ATE0").kind)
        assertEquals(ElmKind.TEXT, ElmResponseParser.parse("ELM327 v1.5", "ATZ").kind)
    }

    @Test
    fun protocolTextIsNotParsedAsData() {
        val parsed = ElmResponseParser.parse("ISO 15765-4 (CAN 11/500)", "ATDP")
        assertEquals(ElmKind.TEXT, parsed.kind)
        assertTrue(parsed.bytes.isEmpty())
    }

    @Test
    fun noDataWithoutASpaceAndNegativeResponse() {
        assertEquals(ElmKind.NO_DATA, ElmResponseParser.parse("43NODATA", "03").kind)
        assertEquals(ElmKind.NO_DATA, ElmResponseParser.parse("SEARCHING...\rNODATA", "010C").kind)
        assertEquals(ElmKind.UNSUPPORTED, ElmResponseParser.parse("7F 01 11", "010C").kind)
        assertEquals(ElmKind.UNSUPPORTED, ElmResponseParser.parse("7F0112", "010C").kind)
        assertEquals(ElmKind.DATA, ElmResponseParser.parse("SEARCHING...\r41 0C 1A F8", "010C").kind)
    }
}
