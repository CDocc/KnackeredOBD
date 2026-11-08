package com.knackered.obd.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.knackered.obd.pid.StandardPids
import com.knackered.obd.vehicle.FuelType
import com.knackered.obd.vehicle.VehicleProfile

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = state.form
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Back") }
                Text("Vehicle profiles", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "Ranges and poll PIDs are per vehicle. Generic is the default and is not based on a particular car.",
                style = MaterialTheme.typography.bodyMedium,
            )
            state.profiles.forEach { profile ->
                TextButton(onClick = { viewModel.select(profile.id) }) {
                    Text(if (profile.id == state.selectedId) "${profile.name} (selected)" else profile.name)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = viewModel::create) { Text("New") }
                Button(
                    onClick = viewModel::deleteSelected,
                    enabled = state.selectedId != VehicleProfile.GENERIC_ID,
                ) { Text("Delete") }
            }
            OutlinedTextField(form.name, { viewModel.update(form.copy(name = it)) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.year, { viewModel.update(form.copy(year = it)) }, label = { Text("Year (optional)") }, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = {
                val next = FuelType.entries[(form.fuel.ordinal + 1) % FuelType.entries.size]
                viewModel.update(form.copy(fuel = next))
            }) { Text("Fuel: ${form.fuel.name}") }
            OutlinedTextField(form.revLimit, { viewModel.update(form.copy(revLimit = it)) }, label = { Text("Rev limit (rpm)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.redline, { viewModel.update(form.copy(redline = it)) }, label = { Text("Redline (rpm)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.coolantWarning, { viewModel.update(form.copy(coolantWarning = it)) }, label = { Text("Coolant warning (°C)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.coolantCritical, { viewModel.update(form.copy(coolantCritical = it)) }, label = { Text("Coolant critical (°C)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(form.speedMax, { viewModel.update(form.copy(speedMax = it)) }, label = { Text("Speed maximum (km/h)") }, modifier = Modifier.fillMaxWidth())
            Text("PIDs to poll", style = MaterialTheme.typography.titleMedium)
            StandardPids.live.forEach { pid ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = pid.id in form.pidIds, onCheckedChange = { viewModel.togglePid(pid.id) })
                    Text("${pid.name} (${pid.command})")
                }
            }
            Text(state.gaugeSummary, style = MaterialTheme.typography.bodyMedium)
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(onClick = viewModel::save, enabled = state.dirty) { Text("Save") }
        }
    }
}
