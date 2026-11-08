package com.knackered.obd.vehicle

import com.knackered.obd.pid.Pid
import com.knackered.obd.pid.StandardPids

enum class FuelType {
    UNKNOWN,
    PETROL,
    DIESEL,
    HYBRID,
    OTHER,
}

/**
 * Display and poll settings for one vehicle. The numbers are chosen by the user.
 * They are not measurements, and they do not change what the ECU reports.
 *
 * Gauge screens, when added, must take [gaugeRanges] from the selected profile
 * rather than hard-coding a rev limit, redline, speed scale, or coolant zone.
 */
data class VehicleProfile(
    val id: String,
    val name: String,
    val year: Int?,
    val fuel: FuelType,
    val revLimitRpm: Int,
    val redlineRpm: Int,
    val coolantWarningC: Int,
    val coolantCriticalC: Int,
    val speedMaxKmh: Int,
    val pollPidIds: List<Int>,
) {
    fun pollPids(): List<Pid> = pollPidIds.mapNotNull { pidId -> StandardPids.live.find { it.id == pidId } }

    fun gaugeRanges(): GaugeRanges = GaugeRanges(
        rpmMax = revLimitRpm,
        redlineRpm = redlineRpm,
        speedMaxKmh = speedMaxKmh,
        coolantWarningC = coolantWarningC,
        coolantCriticalC = coolantCriticalC,
        coolantMaxC = maxOf(coolantCriticalC + GaugeRanges.HEADROOM_C, GaugeRanges.MIN_DISPLAY_MAX_C),
    )

    fun validate(): String? {
        if (id.isBlank() || !id.matches(ID)) return "Profile id is invalid."
        if (name.isBlank() || name.contains('\n') || name.contains('\r')) return "Name cannot be blank or contain a line break."
        if (year != null && year !in YEAR_MIN..YEAR_MAX) return "Year must be between $YEAR_MIN and $YEAR_MAX, or blank."
        if (revLimitRpm !in REV_MIN..REV_MAX) return "Rev limit must be between $REV_MIN and $REV_MAX rpm."
        if (redlineRpm !in 0..revLimitRpm) return "Redline must be between 0 and the rev limit."
        if (coolantWarningC !in TEMP_MIN..TEMP_MAX) return "Coolant warning must be between $TEMP_MIN and $TEMP_MAX °C."
        if (coolantCriticalC !in (coolantWarningC + 1)..TEMP_MAX) {
            return "Coolant critical must be above the warning and at most $TEMP_MAX °C."
        }
        if (speedMaxKmh !in SPEED_MIN..SPEED_MAX) return "Speed maximum must be between $SPEED_MIN and $SPEED_MAX km/h."
        if (pollPidIds.isEmpty() || pollPidIds.any { pidId -> StandardPids.live.none { it.id == pidId } }) {
            return "Choose at least one known live PID."
        }
        return null
    }

    companion object {
        const val GENERIC_ID = "generic"
        private val ID = Regex("[a-z0-9][a-z0-9-]{0,40}")
        private const val YEAR_MIN = 1980
        private const val YEAR_MAX = 2035
        private const val REV_MIN = 1000
        private const val REV_MAX = 20000
        private const val TEMP_MIN = -20
        private const val TEMP_MAX = 150
        private const val SPEED_MIN = 40
        private const val SPEED_MAX = 400

        /**
         * Display defaults only. Not taken from any car.
         * RPM scale 0–7000, redline 6000, coolant warning 100 °C, critical 110 °C, speed 200 km/h.
         */
        fun generic(): VehicleProfile = VehicleProfile(
            id = GENERIC_ID,
            name = "Generic",
            year = null,
            fuel = FuelType.UNKNOWN,
            revLimitRpm = 7000,
            redlineRpm = 6000,
            coolantWarningC = 100,
            coolantCriticalC = 110,
            speedMaxKmh = 200,
            pollPidIds = StandardPids.live.map { it.id },
        )
    }
}

data class GaugeRanges(
    val rpmMax: Int,
    val redlineRpm: Int,
    val speedMaxKmh: Int,
    val coolantMinC: Int = -40,
    val coolantWarningC: Int,
    val coolantCriticalC: Int,
    val coolantMaxC: Int,
) {
    companion object {
        const val HEADROOM_C = 10
        const val MIN_DISPLAY_MAX_C = 120
    }
}

data class ProfileLibrary(
    val selectedId: String,
    val profiles: List<VehicleProfile>,
) {
    fun selected(): VehicleProfile =
        profiles.find { it.id == selectedId } ?: profiles.first { it.id == VehicleProfile.GENERIC_ID }

    fun ensuringGeneric(): ProfileLibrary {
        val withGeneric = if (profiles.any { it.id == VehicleProfile.GENERIC_ID }) {
            profiles
        } else {
            listOf(VehicleProfile.generic()) + profiles
        }
        val selected = selectedId.takeIf { id -> withGeneric.any { it.id == id } } ?: VehicleProfile.GENERIC_ID
        return copy(selectedId = selected, profiles = withGeneric)
    }

    companion object {
        fun genericOnly(): ProfileLibrary = ProfileLibrary(VehicleProfile.GENERIC_ID, listOf(VehicleProfile.generic()))
    }
}
