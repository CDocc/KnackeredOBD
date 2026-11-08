package com.knackered.obd.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TerminalPolicyTest {
    @Test
    fun allowsAtCommandsAndReadModes() {
        assertEquals("ATZ", allowed("atz"))
        assertEquals("ATSH7E0", allowed("ATSH 7E0"))
        assertEquals("AT@1", allowed("AT@1"))
        assertEquals("ATMA", allowed("ATMA"))
        assertEquals("010C", allowed("01 0C"))
        assertEquals("010C0D", allowed("010C0D"))
        assertEquals("020C00", allowed("02 0C 00"))
        assertEquals("03", allowed("03"))
        assertEquals("0902", allowed("09 02"))
    }

    @Test
    fun blocksMode04WithAClearExplanation() {
        val blocked = assertIs<TerminalDecision.Blocked>(TerminalPolicy.evaluate("04"))
        assertEquals("04", blocked.command)
        assertTrue(blocked.reason.contains("Mode 04"))
        assertTrue(blocked.reason.contains("clears stored fault codes"))
        assertTrue(blocked.reason.contains("confirmation"))

        val withExtra = assertIs<TerminalDecision.Blocked>(TerminalPolicy.evaluate("04 00"))
        assertEquals("0400", withExtra.command)
        assertTrue(withExtra.reason.contains("Mode 04"))
    }

    @Test
    fun blocksOtherServicesAndIncompleteRequests() {
        assertTrue(reason("22F190").contains("Service 22"))
        assertTrue(reason("2E F190").contains("Service 2E"))
        assertTrue(reason("08").contains("Service 08"))
        assertTrue(reason("010C1").contains("not a complete Mode 01"))
        assertTrue(reason("AT").contains("not a valid AT command"))
        assertTrue(reason("").contains("Enter a command"))
        assertTrue(reason("ATI\rATZ").contains("one command"))
        assertTrue(reason("STDI").contains("outside that set"))
    }

    private fun allowed(raw: String): String =
        assertIs<TerminalDecision.Allowed>(TerminalPolicy.evaluate(raw)).command

    private fun reason(raw: String): String =
        assertIs<TerminalDecision.Blocked>(TerminalPolicy.evaluate(raw)).reason
}
