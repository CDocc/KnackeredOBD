package com.knackered.obd.pid

/**
 * SAE J1979 Mode 01 formulas. A vehicle may return `NO DATA` for a PID it does
 * not implement. That is a missing PID, not a parser failure.
 */
object StandardPids {
    val engineLoad: Pid = Pid(0x04, "Engine load", "%", 1) { data ->
        data[0] * 100.0 / 255.0
    }

    val coolantTemp: Pid = Pid(0x05, "Coolant temperature", "°C", 1) { data ->
        data[0] - 40.0
    }

    val rpm: Pid = Pid(0x0C, "Engine RPM", "rpm", 2) { data ->
        ((data[0] * 256.0) + data[1]) / 4.0
    }

    val speed: Pid = Pid(0x0D, "Vehicle speed", "km/h", 1) { data ->
        data[0].toDouble()
    }

    val intakeAirTemp: Pid = Pid(0x0F, "Intake air temperature", "°C", 1) { data ->
        data[0] - 40.0
    }

    val throttlePosition: Pid = Pid(0x11, "Throttle position", "%", 1) { data ->
        data[0] * 100.0 / 255.0
    }

    val live: List<Pid> = listOf(
        rpm,
        speed,
        coolantTemp,
        throttlePosition,
        intakeAirTemp,
        engineLoad,
    )

    /**
     * Bit 31 of the 0100 bitmask is PID 01. Bit 0 is PID 20, which means the next range exists.
     */
    fun supportedPids(a: Int, b: Int, c: Int, d: Int, base: Int = 0x00): Set<Int> {
        val mask = (a shl 24) or (b shl 16) or (c shl 8) or d
        return buildSet {
            for (bit in 0 until 32) {
                if ((mask and (1 shl (31 - bit))) != 0) {
                    add(base + bit + 1)
                }
            }
        }
    }
}
