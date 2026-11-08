package com.knackered.obd.session

import com.knackered.obd.dtc.Dtc
import com.knackered.obd.dtc.DtcDecoder
import com.knackered.obd.pid.Pid
import com.knackered.obd.pid.PidReading
import com.knackered.obd.pid.PidUnavailable
import com.knackered.obd.pid.PidUnavailableReason
import com.knackered.obd.pid.PidValue
import com.knackered.obd.pid.StandardPids
import com.knackered.obd.protocol.ElmKind
import com.knackered.obd.protocol.ElmParseResult
import com.knackered.obd.protocol.ElmResponseParser
import com.knackered.obd.protocol.ReadOnlyPolicy
import com.knackered.obd.protocol.TerminalDecision
import com.knackered.obd.protocol.TerminalPolicy
import com.knackered.obd.transport.ObdDirection
import com.knackered.obd.transport.ObdLogger
import com.knackered.obd.transport.ObdProtocolException
import com.knackered.obd.transport.ObdTimeoutException
import com.knackered.obd.transport.ObdTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class InitResult(
    val adapterIdentity: String,
    val protocol: String?,
    val ecuResponding: Boolean,
    val supportedPids: Set<Int>,
)

data class ClearResult(
    val raw: String,
    val confirmed: Boolean,
)

data class TerminalReply(
    val command: String,
    val raw: String,
    val sent: Boolean,
    val message: String?,
)

/**
 * One in-flight ELM conversation: init, Mode 01, stored DTCs, and a confirmed clear.
 * Commands are serialised. Vehicle I/O runs on [Dispatchers.IO].
 */
class ObdClient(
    private val transport: ObdTransport,
    private val logger: ObdLogger = ObdLogger.NONE,
    private val atTimeout: Duration = 3.seconds,
    private val resetTimeout: Duration = 8.seconds,
    private val vehicleTimeout: Duration = 15.seconds,
    private val resetDelay: Duration = 1.seconds,
    private val commandGap: Duration = 50.milliseconds,
) {
    private val mutex = Mutex()

    suspend fun initialize(): InitResult = mutex.withLock {
        check(transport.isConnected) { "Transport is not connected" }
        withContext(Dispatchers.IO) {
            syncPrompt()
            val banner = transactUnlocked("ATZ", resetTimeout)
            if (banner.kind == ElmKind.UNABLE_TO_CONNECT || banner.kind == ElmKind.EMPTY) {
                throw ObdProtocolException("Adapter did not respond to ATZ", banner.raw)
            }
            delay(resetDelay)
            for (command in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) {
                val parsed = transactUnlocked(command, atTimeout)
                if (parsed.kind != ElmKind.OK && !parsed.raw.contains("OK", ignoreCase = true)) {
                    throw ObdProtocolException("$command failed", parsed.raw)
                }
            }
            val probe = transactUnlocked("0100", vehicleTimeout)
            val payload = ElmResponseParser.payloadForRequest(probe.bytes, "0100")
            val ecuResponding = probe.kind == ElmKind.DATA &&
                payload.size >= 2 && payload[0] == 0x41 && payload[1] == 0x00
            val supported = if (ecuResponding && payload.size >= 6) {
                StandardPids.supportedPids(payload[2], payload[3], payload[4], payload[5])
            } else {
                emptySet()
            }
            val protocol = if (ecuResponding) describeProtocol() else null
            InitResult(
                adapterIdentity = banner.lines.firstOrNull { it.isNotBlank() } ?: banner.raw.trim(),
                protocol = protocol,
                ecuResponding = ecuResponding,
                supportedPids = supported,
            )
        }
    }

    suspend fun readPid(pid: Pid): PidReading = mutex.withLock {
        withContext(Dispatchers.IO) {
            val parsed = transactUnlocked(pid.command, vehicleTimeout)
            when (parsed.kind) {
                ElmKind.DATA -> decodePid(pid, parsed)
                ElmKind.NO_DATA -> unavailable(pid, PidUnavailableReason.NO_DATA, parsed.raw)
                ElmKind.UNABLE_TO_CONNECT -> unavailable(pid, PidUnavailableReason.UNABLE_TO_CONNECT, parsed.raw)
                ElmKind.STOPPED -> unavailable(pid, PidUnavailableReason.STOPPED, parsed.raw)
                ElmKind.BUS_ERROR -> unavailable(pid, PidUnavailableReason.BUS_ERROR, parsed.raw)
                ElmKind.MISUNDERSTOOD -> unavailable(pid, PidUnavailableReason.MISUNDERSTOOD, parsed.raw)
                ElmKind.EMPTY -> unavailable(pid, PidUnavailableReason.EMPTY, parsed.raw)
                ElmKind.UNSUPPORTED -> unavailable(pid, PidUnavailableReason.NOT_SUPPORTED, parsed.raw)
                else -> unavailable(pid, PidUnavailableReason.ERROR, parsed.raw)
            }
        }
    }

    fun poll(
        pids: List<Pid> = StandardPids.live,
        gap: Duration = commandGap,
    ): Flow<PidReading> = flow {
        while (currentCoroutineContext().isActive) {
            for (pid in pids) {
                emit(readPid(pid))
                if (gap > Duration.ZERO) delay(gap)
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun readStoredDtcs(): List<Dtc> = mutex.withLock {
        withContext(Dispatchers.IO) {
            val parsed = transactUnlocked("03", vehicleTimeout)
            when (parsed.kind) {
                ElmKind.NO_DATA -> emptyList()
                ElmKind.DATA -> DtcDecoder.decodeRaw(parsed.raw)
                ElmKind.UNSUPPORTED ->
                    throw ObdProtocolException("ECU rejected Mode 03", parsed.raw)
                ElmKind.UNABLE_TO_CONNECT ->
                    throw ObdProtocolException("Unable to connect while reading DTCs", parsed.raw)
                else -> throw ObdProtocolException("Unexpected Mode 03 response (${parsed.kind})", parsed.raw)
            }
        }
    }

    /**
     * Sends one terminal line, or refuses it before anything is written.
     * Allowed lines are an AT command or a Mode 01, 02, 03, or 09 request.
     * Mode 04 is refused here even though [clearStoredDtcs] can send it after confirmation.
     */
    suspend fun submitTerminal(line: String): TerminalReply {
        when (val decision = TerminalPolicy.evaluate(line)) {
            is TerminalDecision.Blocked -> {
                logger.log(ObdDirection.INFO, "Blocked terminal command: ${decision.command}. ${decision.reason}")
                return TerminalReply(decision.command, raw = "", sent = false, message = decision.reason)
            }
            is TerminalDecision.Allowed -> {
                val timeout = if (decision.command.startsWith("AT")) atTimeout else vehicleTimeout
                return mutex.withLock {
                    check(transport.isConnected) { "Transport is not connected" }
                    withContext(Dispatchers.IO) {
                        val parsed = exchange(decision.command, timeout)
                        TerminalReply(decision.command, parsed.raw, sent = true, message = null)
                    }
                }
            }
        }
    }

    /**
     * Sends Mode 04. The UI must confirm first; this method does not prompt.
     */
    suspend fun clearStoredDtcs(): ClearResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val parsed = transactUnlocked("04", vehicleTimeout, allowClear = true)
            val payload = ElmResponseParser.payloadForRequest(parsed.bytes, "04")
            val confirmed = parsed.kind == ElmKind.OK || (parsed.kind == ElmKind.DATA && payload.firstOrNull() == 0x44)
            ClearResult(parsed.raw, confirmed)
        }
    }

    private suspend fun describeProtocol(): String? {
        val parsed = transactUnlocked("ATDP", atTimeout)
        val text = parsed.lines.joinToString(" ").trim()
        return text.takeIf { parsed.kind == ElmKind.TEXT || parsed.kind == ElmKind.OK || parsed.kind == ElmKind.DATA }
    }

    private suspend fun syncPrompt() {
        try {
            transport.write("\r")
            logger.log(ObdDirection.SENT, "")
            val raw = transport.readUntilPrompt(1.seconds)
            logger.log(ObdDirection.RECEIVED, raw)
        } catch (_: ObdTimeoutException) {
            logger.log(ObdDirection.INFO, "No prompt before init; continuing")
        }
    }

    private suspend fun transactUnlocked(
        command: String,
        timeout: Duration,
        allowClear: Boolean = false,
    ): ElmParseResult {
        ReadOnlyPolicy.check(command, allowClear)
        return exchange(command, timeout)
    }

    private suspend fun exchange(command: String, timeout: Duration): ElmParseResult {
        if (commandGap > Duration.ZERO) delay(commandGap)
        transport.write(command + "\r")
        logger.log(ObdDirection.SENT, command)
        val raw = transport.readUntilPrompt(timeout)
        logger.log(ObdDirection.RECEIVED, raw)
        return ElmResponseParser.parse(raw, command)
    }

    private fun decodePid(pid: Pid, parsed: ElmParseResult): PidReading {
        val payload = ElmResponseParser.payloadForRequest(parsed.bytes, pid.command)
        if (payload.size < 2 + pid.dataLength || payload[0] != 0x41 || payload[1] != pid.id) {
            return unavailable(pid, PidUnavailableReason.BAD_PAYLOAD, parsed.raw)
        }
        val data = payload.drop(2).take(pid.dataLength)
        return PidReading.Value(PidValue(pid, pid.decode(data), parsed.raw))
    }

    private fun unavailable(pid: Pid, reason: PidUnavailableReason, raw: String): PidReading =
        PidReading.Unavailable(PidUnavailable(pid, reason, raw))
}
