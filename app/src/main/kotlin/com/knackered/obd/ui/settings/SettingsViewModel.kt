package com.knackered.obd.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.knackered.obd.pid.StandardPids
import com.knackered.obd.vehicle.FileVehicleProfileStore
import com.knackered.obd.vehicle.FuelType
import com.knackered.obd.vehicle.ProfileLibrary
import com.knackered.obd.vehicle.VehicleProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProfileForm(
    val id: String,
    val name: String,
    val year: String,
    val fuel: FuelType,
    val revLimit: String,
    val redline: String,
    val coolantWarning: String,
    val coolantCritical: String,
    val speedMax: String,
    val pidIds: Set<Int>,
)

data class SettingsUiState(
    val profiles: List<VehicleProfile>,
    val selectedId: String,
    val form: ProfileForm,
    val dirty: Boolean,
    val message: String?,
    val gaugeSummary: String,
)

class SettingsViewModel(
    private val store: FileVehicleProfileStore,
) : ViewModel() {
    private val _state = MutableStateFlow(fromLibrary(store.load(), dirty = false, message = null))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun select(id: String) {
        val current = _state.value
        if (current.profiles.none { it.id == id }) return
        val note = if (current.dirty) "Unsaved edits were discarded." else null
        val library = ProfileLibrary(id, current.profiles).ensuringGeneric()
        store.save(library)
        _state.value = fromLibrary(library, dirty = false, message = note)
    }

    fun update(form: ProfileForm) {
        _state.update { it.copy(form = form, dirty = true, message = null, gaugeSummary = form.gaugeSummary()) }
    }

    fun togglePid(pidId: Int) {
        val form = _state.value.form
        val next = if (pidId in form.pidIds) form.pidIds - pidId else form.pidIds + pidId
        update(form.copy(pidIds = next))
    }

    fun save() {
        val current = _state.value
        val parsed = current.form.toProfile()
        val error = parsed?.validate() ?: "Could not read the form. Use whole numbers."
        if (parsed == null || parsed.validate() != null) {
            _state.update { it.copy(message = error) }
            return
        }
        val others = current.profiles.filter { it.id != parsed.id }
        val library = ProfileLibrary(parsed.id, others + parsed).ensuringGeneric()
        store.save(library)
        _state.value = fromLibrary(library, dirty = false, message = "Saved.")
    }

    fun create() {
        val id = "p${System.currentTimeMillis()}"
        val created = VehicleProfile.generic().copy(id = id, name = "New profile")
        val library = ProfileLibrary(id, _state.value.profiles + created).ensuringGeneric()
        store.save(library)
        _state.value = fromLibrary(library, dirty = false, message = "Created. Edit it, then save.")
    }

    fun deleteSelected() {
        val current = _state.value
        if (current.selectedId == VehicleProfile.GENERIC_ID) {
            _state.update { it.copy(message = "The Generic profile cannot be deleted.") }
            return
        }
        val library = ProfileLibrary(
            VehicleProfile.GENERIC_ID,
            current.profiles.filter { it.id != current.selectedId },
        ).ensuringGeneric()
        store.save(library)
        _state.value = fromLibrary(library, dirty = false, message = "Deleted.")
    }

    companion object {
        fun factory(store: FileVehicleProfileStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(store) as T
            }
    }
}

private fun fromLibrary(library: ProfileLibrary, dirty: Boolean, message: String?): SettingsUiState {
    val safe = library.ensuringGeneric()
    val selected = safe.selected()
    return SettingsUiState(
        profiles = safe.profiles,
        selectedId = safe.selectedId,
        form = selected.toForm(),
        dirty = dirty,
        message = message,
        gaugeSummary = selected.gaugeRanges().summary(),
    )
}

private fun VehicleProfile.toForm(): ProfileForm = ProfileForm(
    id = id,
    name = name,
    year = year?.toString().orEmpty(),
    fuel = fuel,
    revLimit = revLimitRpm.toString(),
    redline = redlineRpm.toString(),
    coolantWarning = coolantWarningC.toString(),
    coolantCritical = coolantCriticalC.toString(),
    speedMax = speedMaxKmh.toString(),
    pidIds = pollPidIds.toSet(),
)

private fun ProfileForm.toProfile(): VehicleProfile? {
    val yearText = year.trim()
    val yearValue = if (yearText.isEmpty()) null else yearText.toIntOrNull() ?: return null
    return VehicleProfile(
        id = id,
        name = name.trim(),
        year = yearValue,
        fuel = fuel,
        revLimitRpm = revLimit.trim().toIntOrNull() ?: return null,
        redlineRpm = redline.trim().toIntOrNull() ?: return null,
        coolantWarningC = coolantWarning.trim().toIntOrNull() ?: return null,
        coolantCriticalC = coolantCritical.trim().toIntOrNull() ?: return null,
        speedMaxKmh = speedMax.trim().toIntOrNull() ?: return null,
        pollPidIds = StandardPids.live.map { it.id }.filter { it in pidIds },
    )
}

private fun ProfileForm.gaugeSummary(): String = toProfile()?.gaugeRanges()?.summary()
    ?: "Gauge ranges will update when the numbers are valid."

private fun com.knackered.obd.vehicle.GaugeRanges.summary(): String =
    "Gauges use this profile: RPM 0–$rpmMax, redline $redlineRpm, speed 0–$speedMaxKmh km/h, " +
        "coolant warning $coolantWarningC °C, critical $coolantCriticalC °C."
