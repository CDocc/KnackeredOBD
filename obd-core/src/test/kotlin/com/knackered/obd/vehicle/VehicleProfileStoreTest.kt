package com.knackered.obd.vehicle

import com.knackered.obd.pid.StandardPids
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleProfileStoreTest {
    @Test
    fun missingFileLoadsGenericDefaults() {
        val file = Files.createTempDirectory("profiles").resolve("vehicle-profiles.txt").toFile()
        val loaded = FileVehicleProfileStore(file).load()
        assertEquals(VehicleProfile.generic(), loaded.selected())
        assertNull(loaded.selected().year)
        assertEquals(FuelType.UNKNOWN, loaded.selected().fuel)
        assertEquals(listOf(0x0C, 0x0D, 0x05, 0x11, 0x0F, 0x04), loaded.selected().pollPidIds)
        assertEquals(StandardPids.live.map { it.id }, loaded.selected().pollPids().map { it.id })
        val ranges = loaded.selected().gaugeRanges()
        assertEquals(7000, ranges.rpmMax)
        assertEquals(6000, ranges.redlineRpm)
        assertEquals(200, ranges.speedMaxKmh)
        assertEquals(100, ranges.coolantWarningC)
        assertEquals(110, ranges.coolantCriticalC)
    }

    @Test
    fun roundTripKeepsSelectionAndEdits() {
        val file = Files.createTempDirectory("profiles").resolve("vehicle-profiles.txt").toFile()
        val store = FileVehicleProfileStore(file)
        val custom = VehicleProfile.generic().copy(
            id = "daily",
            name = "Daily",
            year = 2008,
            fuel = FuelType.DIESEL,
            revLimitRpm = 5000,
            redlineRpm = 4500,
            coolantWarningC = 95,
            coolantCriticalC = 105,
            speedMaxKmh = 180,
            pollPidIds = listOf(0x0C, 0x0D),
        )
        store.save(ProfileLibrary("daily", listOf(VehicleProfile.generic(), custom)))
        val loaded = FileVehicleProfileStore(file).load()
        assertEquals("daily", loaded.selectedId)
        assertEquals(custom, loaded.selected())
        assertEquals(4500, loaded.selected().gaugeRanges().redlineRpm)
        assertEquals(listOf(StandardPids.rpm, StandardPids.speed), loaded.selected().pollPids())
        assertTrue(loaded.profiles.any { it.id == VehicleProfile.GENERIC_ID })
    }

    @Test
    fun corruptFileFallsBackToGeneric() {
        val file = Files.createTempDirectory("profiles").resolve("vehicle-profiles.txt").toFile()
        file.writeText("this is not a profile file")
        assertEquals(ProfileLibrary.genericOnly(), FileVehicleProfileStore(file).load())
    }

    @Test
    fun saveRestoresGenericIfMissingAndRejectsInvalidRanges() {
        val file = Files.createTempDirectory("profiles").resolve("vehicle-profiles.txt").toFile()
        val store = FileVehicleProfileStore(file)
        val onlyCustom = VehicleProfile.generic().copy(id = "other", name = "Other")
        store.save(ProfileLibrary("missing", listOf(onlyCustom)))
        val loaded = store.load()
        assertEquals(VehicleProfile.GENERIC_ID, loaded.selectedId)
        assertTrue(loaded.profiles.any { it.id == VehicleProfile.GENERIC_ID })

        val invalid = VehicleProfile.generic().copy(redlineRpm = 9000)
        assertTrue(invalid.validate()!!.contains("Redline"))
        assertFailsWith<IllegalArgumentException> {
            store.save(ProfileLibrary(VehicleProfile.GENERIC_ID, listOf(invalid)))
        }
    }
}
