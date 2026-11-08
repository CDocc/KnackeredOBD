package com.knackered.obd.transport

import kotlin.time.Duration

/**
 * Byte pipe to an ELM327/STN-style adapter.
 *
 * The session owns command framing (`\r`) and prompt parsing. Implementations
 * only connect and move text. Classic Bluetooth SPP (RFCOMM UUID
 * `00001101-0000-1000-8000-00805F9B34FB`) and BLE GATT are app-module
 * implementations of this interface so they can be swapped without touching
 * PID or DTC code.
 *
 * Blocking socket reads belong inside these suspend functions, on
 * [kotlinx.coroutines.Dispatchers.IO], never on the main thread.
 */
interface ObdTransport {
    val isConnected: Boolean

    suspend fun connect()

    suspend fun disconnect()

    /** Writes [text] exactly. The session includes the terminating CR. */
    suspend fun write(text: String)

    /**
     * Reads until the ELM `>` prompt.
     * The prompt itself is not part of the returned text.
     */
    suspend fun readUntilPrompt(timeout: Duration): String
}
