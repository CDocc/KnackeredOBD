package com.knackered.obd.protocol

import com.knackered.obd.transport.normalizeCommand

enum class ElmKind {
    DATA,
    OK,
    TEXT,
    NO_DATA,
    UNABLE_TO_CONNECT,
    BUS_ERROR,
    STOPPED,
    MISUNDERSTOOD,
    /** ISO 15765 negative response: `7F <mode> 11` or `7F <mode> 12`. */
    UNSUPPORTED,
    ERROR,
    EMPTY,
}

data class ElmParseResult(
    val kind: ElmKind,
    /** Bytes recovered from the payload, including the mode-response byte when present. */
    val bytes: List<Int>,
    val raw: String,
    val lines: List<String>,
)

/**
 * Turns an ELM327/STN prompt-terminated reply into either an error or bytes.
 *
 * Handles echoed commands, `SEARCHING...`, `BUS INIT`, CR-only line endings,
 * spaced and unspaced hex, CAN 11-bit headers, and ELM numbered multi-frames
 * (`0:`, `1:`). `SEARCHING...` including its dots is removed before classification.
 * Negative responses follow the ELM/ISO shape used by kotlin-obd-api (`7F` + mode + `11`/`12`).
 */
object ElmResponseParser {
    private val searching = Regex("""SEARCHING\.*""", RegexOption.IGNORE_CASE)
    private val busInit = Regex("""BUS\s*INIT:?\.*""", RegexOption.IGNORE_CASE)
    private val dots = Regex("""^\.+$""")
    private val frameIndex = Regex("""^[0-9A-F]:\s*""", RegexOption.IGNORE_CASE)
    private val hexRun = Regex("""[0-9A-Fa-f]+""")
    private val whitespace = Regex("""\s""")
    private val negativeResponse = Regex("""7F[0-9A-F]{2}(?:11|12)""")

    fun parse(raw: String, sentCommand: String? = null): ElmParseResult {
        val commandKey = sentCommand?.let(::normalizeCommand)?.takeIf { it.isNotEmpty() }
        val lines = raw
            .replace("\u0000", "")
            .replace(">", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && !dots.matches(it) }

        val withoutEcho = lines.filterNot { isEcho(it, commandKey) }
        val upper = withoutEcho.joinToString("\n").uppercase()
        val compact = whitespace.replace(upper, "")

        fun finish(kind: ElmKind, bytes: List<Int> = emptyList(), kept: List<String> = withoutEcho) =
            ElmParseResult(kind, bytes, raw, kept)

        when {
            upper.contains("UNABLE TO CONNECT") || compact.contains("UNABLETOCONNECT") ->
                return finish(ElmKind.UNABLE_TO_CONNECT)
            upper.contains("CAN ERROR") || upper.contains("BUS ERROR") || upper.contains("BUS BUSY") ||
                upper.contains("FB ERROR") || upper.contains("DATA ERROR") || upper.contains("LV RESET") ||
                upper.contains("BUS INIT: ERROR") || upper.contains("BUSINIT: ERROR") ||
                upper.contains("BUS INIT...ERROR") || upper.contains("BUSINIT...ERROR") ->
                return finish(ElmKind.BUS_ERROR)
            compact.contains("NODATA") -> return finish(ElmKind.NO_DATA)
            compact.contains("STOPPED") -> return finish(ElmKind.STOPPED)
            withoutEcho.any { it == "?" } || compact == "?" -> return finish(ElmKind.MISUNDERSTOOD)
            negativeResponse.containsMatchIn(compact) -> return finish(ElmKind.UNSUPPORTED)
            upper.contains("ERROR") -> return finish(ElmKind.ERROR)
        }

        if (withoutEcho.isEmpty()) return finish(ElmKind.EMPTY)

        if (withoutEcho.all { isOkLine(it, commandKey) }) {
            return finish(ElmKind.OK)
        }

        val cleaned = withoutEcho.mapNotNull { stripStatus(it) }.filter { it.isNotEmpty() && !isOkLine(it, commandKey) }
        if (cleaned.isEmpty()) {
            return if (withoutEcho.any { isOkLine(it, commandKey) }) finish(ElmKind.OK) else finish(ElmKind.EMPTY)
        }

        val bytes = extractBytes(cleaned.filter(::isHexPayloadLine))
        return if (bytes.isEmpty()) finish(ElmKind.TEXT, kept = cleaned) else finish(ElmKind.DATA, bytes, cleaned)
    }

    /** A data line is only hex digits, spaces, and an optional ELM frame index. */
    private fun isHexPayloadLine(line: String): Boolean {
        val stripped = frameIndex.replace(line, "").replace(" ", "")
        return stripped.isNotEmpty() && stripped.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    }

    /**
     * Bytes starting at the positive response for [request] (`010C` -> `41 0C ...`).
     * Drops a leading CAN id / ISO-TP length byte that survived header stripping.
     */
    fun payloadForRequest(bytes: List<Int>, request: String?): List<Int> {
        val mode = responseMode(request) ?: return bytes
        val pid = requestPid(request)
        if (pid != null) {
            for (i in 0 until bytes.size - 1) {
                if (bytes[i] == mode && bytes[i + 1] == pid) return bytes.drop(i)
            }
        }
        val index = bytes.indexOf(mode)
        return if (index >= 0) bytes.drop(index) else bytes
    }

    fun responseMode(request: String?): Int? {
        val key = request?.let(::normalizeCommand) ?: return null
        return when {
            key.startsWith("01") && key.length == 4 -> 0x41
            key == "03" -> 0x43
            key == "04" -> 0x44
            else -> null
        }
    }

    fun requestPid(request: String?): Int? {
        val key = request?.let(::normalizeCommand) ?: return null
        if (!key.startsWith("01") || key.length != 4) return null
        return key.substring(2).toInt(16)
    }

    private fun isEcho(line: String, commandKey: String?): Boolean {
        if (commandKey == null) return false
        return normalizeCommand(line) == commandKey
    }

    private fun isOkLine(line: String, commandKey: String?): Boolean {
        val compact = normalizeCommand(line)
        return compact == "OK" || (commandKey != null && compact == commandKey + "OK")
    }

    private fun stripStatus(line: String): String? {
        val stripped = busInit.replace(searching.replace(line, ""), "").trim()
        return stripped.takeIf { it.isNotEmpty() }
    }

    private fun extractBytes(lines: List<String>): List<Int> {
        val bytes = mutableListOf<Int>()
        for (line in lines) {
            val stripped = frameIndex.replace(line.trim(), "")
            val tokens = hexRun.findAll(stripped).map { it.value }.toList()
            if (tokens.isEmpty()) continue
            if (tokens.size == 1 && tokens[0].length >= 4 && tokens[0].length % 2 == 0) {
                bytes += chunk(tokens[0])
                continue
            }
            for (token in tokens) {
                when {
                    token.length == 2 -> bytes += token.toInt(16)
                    token.length == 3 -> Unit
                    token.length % 2 == 0 && token.length >= 4 -> bytes += chunk(token)
                }
            }
        }
        return bytes
    }

    private fun chunk(hex: String): List<Int> =
        hex.chunked(2).map { it.toInt(16) }
}
