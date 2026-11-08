package com.knackered.obd.protocol

import com.knackered.obd.transport.normalizeCommand

/**
 * What the terminal may put on the wire.
 *
 * This is separate from [ReadOnlyPolicy]. The app's own init, poll, and confirmed
 * clear stay on that narrower list. The terminal may send any ELM `AT` command
 * and a read-only Mode 01, 02, 03, or 09 request. Mode 04 is never sent from here.
 */
object TerminalPolicy {
    private val hex = Regex("[0-9A-F]+")
    private val atCommand = Regex("AT[0-9A-Z@]+")
    private val mode01 = Regex("01(?:[0-9A-F]{2})+")
    private val mode02 = Regex("02(?:[0-9A-F]{2})+")
    private val mode09 = Regex("09(?:[0-9A-F]{2})+")

    fun evaluate(raw: String): TerminalDecision {
        val display = raw.trim()
        if (display.isEmpty()) {
            return TerminalDecision.Blocked("", "Enter a command to send.")
        }
        if (display.lineSequence().count { it.isNotBlank() } > 1) {
            return TerminalDecision.Blocked(
                display,
                "Send one command at a time. Nothing was sent.",
            )
        }
        val key = normalizeCommand(display)
        if (key.isEmpty()) {
            return TerminalDecision.Blocked(display, "Enter a command to send.")
        }
        if (key.startsWith("04")) {
            return TerminalDecision.Blocked(key, mode04Reason(key))
        }
        if (atCommand.matches(key)) {
            return TerminalDecision.Allowed(key)
        }
        if (key.startsWith("AT")) {
            return TerminalDecision.Blocked(key, "Not sent. $key is not a valid AT command.")
        }
        if (key == "03" || mode01.matches(key) || mode02.matches(key) || mode09.matches(key)) {
            return TerminalDecision.Allowed(key)
        }
        return TerminalDecision.Blocked(key, rejectedRequest(key))
    }

    private fun mode04Reason(key: String): String =
        "Not sent. Mode 04 ($key) clears stored fault codes. " +
            "The terminal cannot clear codes; that stays on the fault-code screen, behind a confirmation."

    private fun rejectedRequest(key: String): String {
        val service = key.take(2)
        val looksLikeService = service.length == 2 && hex.matches(service)
        return if (looksLikeService && service in setOf("01", "02", "09")) {
            "Not sent. $key is not a complete Mode $service request. " +
                "Use whole bytes, for example 010C, 020C00, or 0902. Mode 03 is just 03."
        } else if (looksLikeService) {
            "Not sent. The terminal only accepts adapter AT commands and read-only " +
                "Mode 01, 02, 03, and 09 requests. Service $service ($key) was not sent."
        } else {
            "Not sent. The terminal only accepts adapter AT commands and read-only " +
                "Mode 01, 02, 03, and 09 requests. $key is outside that set."
        }
    }
}

sealed class TerminalDecision {
    abstract val command: String

    data class Allowed(override val command: String) : TerminalDecision()

    data class Blocked(
        override val command: String,
        val reason: String,
    ) : TerminalDecision()
}
