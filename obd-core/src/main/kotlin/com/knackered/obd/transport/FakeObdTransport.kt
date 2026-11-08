package com.knackered.obd.transport

import kotlin.time.Duration

/**
 * Scripted adapter for UI and parser work without a car or emulator.
 *
 * Keys are commands with spaces removed (`010C`, `ATZ`). A one-entry list is
 * repeated, so live polling keeps returning the same frame. A longer list is
 * consumed in order and then holds the last response.
 */
class FakeObdTransport(
    responses: Map<String, List<String>> = demoScript(),
    private val unknownResponse: String = "?",
) : ObdTransport {
    private val script: Map<String, List<String>> = responses.mapKeys { normalizeCommand(it.key) }
    private val cursors = mutableMapOf<String, Int>()
    private val sentCommands = mutableListOf<String>()

    override var isConnected: Boolean = false
        private set

    override suspend fun connect() {
        isConnected = true
    }

    override suspend fun disconnect() {
        isConnected = false
        pending = null
    }

    private var pending: String? = null

    override suspend fun write(text: String) {
        check(isConnected) { "Fake transport is not connected" }
        val command = text.trim('\r', '\n', ' ')
        sentCommands += command
        if (command.isEmpty()) {
            pending = ""
            return
        }
        val key = normalizeCommand(command)
        val frames = script[key]
        pending = if (frames.isNullOrEmpty()) {
            unknownResponse
        } else {
            val index = cursors.getOrDefault(key, 0)
            cursors[key] = index + 1
            frames[index.coerceAtMost(frames.lastIndex)]
        }
    }

    override suspend fun readUntilPrompt(timeout: Duration): String {
        check(isConnected) { "Fake transport is not connected" }
        return pending ?: error("readUntilPrompt called before write")
    }

    fun sent(): List<String> = sentCommands.toList()

    companion object {
        /**
         * A generic demo ECU: CAN 11/500, a few stored codes, idle-ish PIDs.
         * RPM frame is the usual worked example: `41 0C 1A F8` = 1726 rpm.
         * These frames are not a recording from any particular vehicle.
         */
        fun demoScript(): Map<String, List<String>> = mapOf(
            "ATZ" to listOf("ELM327 v1.5"),
            "ATE0" to listOf("OK"),
            "ATL0" to listOf("OK"),
            "ATS0" to listOf("OK"),
            "ATH0" to listOf("OK"),
            "ATSP0" to listOf("OK"),
            "ATDP" to listOf("ISO 15765-4 (CAN 11/500)"),
            "ATI" to listOf("ELM327 v1.5"),
            "0100" to listOf("41 00 BE 3F A8 13"),
            "010C" to listOf("41 0C 1A F8"),
            "010D" to listOf("41 0D 3C"),
            "0105" to listOf("41 05 5F"),
            "0111" to listOf("41 11 80"),
            "010F" to listOf("41 0F 46"),
            "0104" to listOf("41 04 80"),
            "03" to listOf("43 01 33 03 01 00 00"),
            "04" to listOf("44"),
        )
    }
}

internal fun normalizeCommand(command: String): String =
    command.uppercase().replace(" ", "").replace("\r", "").replace("\n", "").trim()
