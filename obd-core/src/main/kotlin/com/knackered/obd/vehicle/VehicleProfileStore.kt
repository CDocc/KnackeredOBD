package com.knackered.obd.vehicle

import com.knackered.obd.pid.StandardPids
import java.io.File

object VehicleProfileCodec {
    fun encode(library: ProfileLibrary): String {
        val safe = library.ensuringGeneric()
        return buildString {
            appendLine("v1")
            appendLine("selected ${safe.selectedId}")
            for (profile in safe.profiles) {
                appendLine("profile ${profile.id}")
                appendLine("name ${profile.name}")
                appendLine("year ${profile.year?.toString().orEmpty()}")
                appendLine("fuel ${profile.fuel.name}")
                appendLine("revLimit ${profile.revLimitRpm}")
                appendLine("redline ${profile.redlineRpm}")
                appendLine("coolantWarning ${profile.coolantWarningC}")
                appendLine("coolantCritical ${profile.coolantCriticalC}")
                appendLine("speedMax ${profile.speedMaxKmh}")
                appendLine("pids ${profile.pollPidIds.joinToString(",") { "%02X".format(it) }}")
            }
        }
    }

    fun decode(text: String): ProfileLibrary {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull() != "v1") return ProfileLibrary.genericOnly()
        val selected = lines.firstOrNull { it.startsWith("selected ") }
            ?.removePrefix("selected ")
            ?.trim()
            ?: VehicleProfile.GENERIC_ID
        val profiles = mutableListOf<VehicleProfile>()
        var current: MutableMap<String, String>? = null
        fun finish() {
            current?.let(::parseProfile)?.let { profiles += it }
            current = null
        }
        for (line in lines.drop(1)) {
            if (line.startsWith("profile ")) {
                finish()
                current = mutableMapOf("id" to line.removePrefix("profile ").trim())
            } else {
                val fields = current
                if (fields != null) {
                    val space = line.indexOf(' ')
                    if (space > 0) fields[line.substring(0, space)] = line.substring(space + 1)
                }
            }
        }
        finish()
        if (profiles.isEmpty()) return ProfileLibrary.genericOnly()
        return ProfileLibrary(selected, profiles).ensuringGeneric()
    }

    private fun parseProfile(fields: Map<String, String>): VehicleProfile? {
        val id = fields["id"] ?: return null
        val fuel = fields["fuel"]?.let { name -> FuelType.entries.find { it.name == name } } ?: FuelType.UNKNOWN
        val pids = fields["pids"].orEmpty()
            .split(',')
            .mapNotNull { token -> token.trim().toIntOrNull(16) }
            .filter { pidId -> StandardPids.live.any { it.id == pidId } }
            .distinct()
        if (pids.isEmpty()) return null
        val draft = VehicleProfile(
            id = id,
            name = fields["name"].orEmpty().ifBlank { id },
            year = fields["year"]?.toIntOrNull(),
            fuel = fuel,
            revLimitRpm = fields["revLimit"]?.toIntOrNull() ?: return null,
            redlineRpm = fields["redline"]?.toIntOrNull() ?: return null,
            coolantWarningC = fields["coolantWarning"]?.toIntOrNull() ?: return null,
            coolantCriticalC = fields["coolantCritical"]?.toIntOrNull() ?: return null,
            speedMaxKmh = fields["speedMax"]?.toIntOrNull() ?: return null,
            pollPidIds = pids,
        )
        return draft.takeIf { it.validate() == null }
    }
}

class FileVehicleProfileStore(private val file: File) {
    private val lock = Any()

    fun load(): ProfileLibrary = synchronized(lock) {
        if (!file.isFile) return ProfileLibrary.genericOnly()
        runCatching { VehicleProfileCodec.decode(file.readText()) }.getOrElse { ProfileLibrary.genericOnly() }
    }

    fun save(library: ProfileLibrary) {
        val safe = library.ensuringGeneric()
        val error = safe.profiles.firstNotNullOfOrNull { it.validate() }
        require(error == null) { error ?: "Invalid profile" }
        synchronized(lock) {
            file.parentFile?.mkdirs()
            file.writeText(VehicleProfileCodec.encode(safe))
        }
    }
}
