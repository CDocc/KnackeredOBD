package com.knackered.obd.transport

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ObdDirection {
    SENT,
    RECEIVED,
    INFO,
}

data class ObdLogEntry(
    val timestampMillis: Long,
    val direction: ObdDirection,
    val text: String,
)

fun interface ObdLogger {
    suspend fun log(direction: ObdDirection, text: String)

    companion object {
        val NONE: ObdLogger = ObdLogger { _, _ -> }
    }
}

/**
 * Bounded raw traffic log for the debug screen. Oldest lines are dropped.
 */
class InMemoryObdLog(
    private val maxEntries: Int = 2_000,
    private val clock: () -> Long = System::currentTimeMillis,
) : ObdLogger {
    private val mutex = Mutex()
    private val entries = ArrayDeque<ObdLogEntry>()

    override suspend fun log(direction: ObdDirection, text: String) {
        mutex.withLock {
            entries.addLast(ObdLogEntry(clock(), direction, text))
            while (entries.size > maxEntries) {
                entries.removeFirst()
            }
        }
    }

    suspend fun snapshot(): List<ObdLogEntry> = mutex.withLock { entries.toList() }

    suspend fun clear() {
        mutex.withLock { entries.clear() }
    }
}
