package com.knackered.obd.pid

/**
 * One Mode 01 PID. [decode] receives only the data bytes (A, B, ...), not `41` or the PID echo.
 * Adding a PID is one object plus a line in [StandardPids.live].
 */
data class Pid(
    val id: Int,
    val name: String,
    val unit: String,
    val dataLength: Int,
    val decode: (List<Int>) -> Double,
) {
    val command: String = "01%02X".format(id)

    init {
        require(id in 0x00..0xFF) { "PID must be a single byte" }
        require(dataLength > 0)
    }
}

data class PidValue(
    val pid: Pid,
    val value: Double,
    val raw: String,
)

enum class PidUnavailableReason {
    NO_DATA,
    UNABLE_TO_CONNECT,
    STOPPED,
    BUS_ERROR,
    MISUNDERSTOOD,
    EMPTY,
    BAD_PAYLOAD,
    NOT_SUPPORTED,
    ERROR,
}

data class PidUnavailable(
    val pid: Pid,
    val reason: PidUnavailableReason,
    val raw: String,
)

sealed interface PidReading {
    val pid: Pid

    data class Value(val reading: PidValue) : PidReading {
        override val pid: Pid get() = reading.pid
    }

    data class Unavailable(val reading: PidUnavailable) : PidReading {
        override val pid: Pid get() = reading.pid
    }
}
