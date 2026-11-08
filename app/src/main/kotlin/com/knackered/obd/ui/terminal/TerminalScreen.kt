package com.knackered.obd.ui.terminal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TerminalScreen(viewModel: TerminalViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(state.history.size) {
        if (state.history.isNotEmpty()) {
            listState.animateScrollToItem(state.history.lastIndex)
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Terminal", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenSettings) { Text("Settings") }
            }
            Text(state.status, style = MaterialTheme.typography.bodyMedium)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.history.isEmpty()) {
                    item {
                        Text(
                            "Raw replies show up here. AT commands and Mode 01, 02, 03, and 09 are sent. " +
                                "Mode 04 and every other request are refused, with the reason, and are not written to the adapter.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(state.history) { line ->
                    TerminalEntry(line)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Command") },
                    placeholder = { Text("010C") },
                    singleLine = true,
                    enabled = state.connected && !state.sending,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
                )
                Button(
                    onClick = viewModel::send,
                    enabled = state.connected && !state.sending && state.draft.isNotBlank(),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(if (state.sending) "Sending" else "Send")
                }
            }
        }
    }
}

@Composable
private fun TerminalEntry(line: TerminalLine) {
    val bodyColor = when (line.outcome) {
        TerminalOutcome.REPLY -> MaterialTheme.colorScheme.onSurface
        TerminalOutcome.BLOCKED, TerminalOutcome.FAILED -> MaterialTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "> ${line.command.ifBlank { "(blank)" }}",
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = line.body,
            fontFamily = FontFamily.Monospace,
            color = bodyColor,
        )
    }
}
