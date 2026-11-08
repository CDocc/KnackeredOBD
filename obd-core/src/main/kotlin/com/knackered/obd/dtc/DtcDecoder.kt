package com.knackered.obd.dtc

/**
 * SAE J2012 2-byte DTC layout:
 * bits 15-14 type (P/C/B/U), bits 13-12 first digit, remaining 12 bits as hex.
 *
 * Frame cleanup follows kotlin-obd-api's Mode 03 cases: a CAN count byte on a
 * short frame, ELM `N:` multi-frames, a repeated `43` at the start of each
 * ISO line, and stop at the first `P0000` pad.
 */
object DtcDecoder {
    private const val SHORT_CAN_MAX_HEX = 16
    private val searching = Regex("""SEARCHING\.*""", RegexOption.IGNORE_CASE)
    private val busInit = Regex("""BUS\s*INIT:?\.*""", RegexOption.IGNORE_CASE)
    private val frameIndex = Regex("""^[0-9A-F]:\s*""", RegexOption.IGNORE_CASE)

    fun format(high: Int, low: Int): String {
        val type = when ((high shr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val digit = (high shr 4) and 0x03
        val rest = ((high and 0x0F) shl 8) or (low and 0xFF)
        return "%c%d%03X".format(type, digit, rest)
    }

    /** Decode a prompt-terminated ELM reply, including multi-line frames. */
    fun decodeRaw(raw: String): List<Dtc> {
        val prepared = prepare(raw)
        if (prepared.isEmpty()) return emptyList()
        val body = when {
            prepared.any { it.contains(':') } -> canMultiFrameBody(prepared)
            isShortCanFrame(prepared) -> compactHex(prepared.joinToString("")).drop(4)
            else -> isoBody(prepared)
        }
        return codesFromHex(body)
    }

    /**
     * Flat byte body, usually starting at `43`. Equivalent to [decodeRaw] on the
     * hex of [payload], so a short CAN count byte is still recognised.
     */
    fun decode(payload: List<Int>): List<Dtc> {
        if (payload.isEmpty()) return emptyList()
        val hex = payload.joinToString("") { "%02X".format(it and 0xFF) }
        return decodeRaw(hex)
    }

    private fun prepare(raw: String): List<String> =
        raw.replace("\u0000", "")
            .replace(">", "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "." && !it.all { ch -> ch == '.' } }
            .filterNot { it.equals("03", ignoreCase = true) || it.equals("OK", ignoreCase = true) }
            .map { busInit.replace(searching.replace(it, ""), "").trim() }
            .filter { it.isNotEmpty() }

    /**
     * ELM CAN single frame: `43` + count + codes, and the compact hex length is
     * a multiple of 4 and at most 16. `430201200121` is count 2, not `P0201`.
     */
    private fun isShortCanFrame(lines: List<String>): Boolean {
        if (lines.size != 1 || lines[0].contains(':')) return false
        val hex = compactHex(lines[0])
        return hex.startsWith("43") && hex.length <= SHORT_CAN_MAX_HEX && hex.length % 4 == 0
    }

    /**
     * `00A / 0:43yy... / 1:...` — drop the ISO-TP length line and the `43yy` header.
     */
    private fun canMultiFrameBody(lines: List<String>): String {
        val hex = lines.filter { frameIndex.containsMatchIn(it) }
            .joinToString("") { compactHex(it.substringAfter(':')) }
        return if (hex.startsWith("43") && hex.length >= 4) hex.drop(4) else hex
    }

    /** Strip a leading `43` from each line so a second frame header is not a code. */
    private fun isoBody(lines: List<String>): String =
        lines.joinToString("") { line ->
            val hex = compactHex(line)
            if (hex.startsWith("43")) hex.drop(2) else hex
        }

    private fun codesFromHex(hex: String): List<Dtc> {
        val codes = hex.chunked(4).mapNotNull { chunk ->
            if (chunk.length < 4) return@mapNotNull null
            format(chunk.substring(0, 2).toInt(16), chunk.substring(2, 4).toInt(16))
        }
        val padding = codes.indexOf("P0000")
        val kept = if (padding < 0) codes else codes.take(padding)
        return kept.map { code -> Dtc(code, GenericDtcDescriptions.lookup(code)) }
    }

    private fun compactHex(text: String): String =
        text.uppercase().filter { it.isDigit() || it in 'A'..'F' }
}
