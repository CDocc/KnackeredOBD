package com.knackered.obd.protocol

import com.knackered.obd.transport.ObdNotAllowedException
import com.knackered.obd.transport.normalizeCommand

/**
 * v1 talks only to the adapter and to standard OBD modes 01, 03, and 04.
 * No UDS (`22`/`2E`/`31`), no Mode 08 actuator tests, no header injection
 * (`ATSH`), and no writes aimed at a body module or other ECU.
 *
 * Mode 04 is allowed only when the caller has already confirmed a clear.
 * `ATSP0`–`ATSPC` only selects the ELM protocol; it does not code a module.
 */
object ReadOnlyPolicy {
    private val mode01 = Regex("""01[0-9A-F]{2}""")
    private val protocolSelect = Regex("""ATSP[0-9A-C]""")
    private val adapterSetup = Regex("""AT(E[01]|L[01]|S[01]|H[01])""")
    private val adapterQuery = setOf(
        "ATZ",
        "ATWS",
        "ATD",
        "ATI",
        "AT@1",
        "ATRV",
        "ATDP",
        "ATDPN",
    )

    fun check(command: String, allowClear: Boolean = false) {
        val key = normalizeCommand(command)
        if (key.isEmpty()) {
            throw ObdNotAllowedException("Empty command")
        }
        if (key == "04") {
            if (!allowClear) {
                throw ObdNotAllowedException("Mode 04 is only sent by clearStoredDtcs() after confirmation")
            }
            return
        }
        val allowed = key == "03" || mode01.matches(key) || key in adapterQuery ||
            adapterSetup.matches(key) || protocolSelect.matches(key)
        if (!allowed) {
            throw ObdNotAllowedException("Command is outside the v1 read-only set: $command")
        }
    }
}
