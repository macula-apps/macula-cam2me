package io.macula.cam2me.reachability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val CUSTOM_HOST_KEY = ""
private const val AUTO_KEY = "auto"

@Composable
fun SettingsScreen(
    currentAutoDiscovery: Boolean,
    currentHost: String,
    currentPort: Int,
    currentNodeIdHex: String,
    onBack: () -> Unit,
    onSaveAuto: () -> Unit,
    onSaveManual: (station: KnownStation) -> Unit,
) {
    val matchedKnown = KNOWN_STATIONS.find { it.host == currentHost && it.port == currentPort }
    var selectedHost by remember {
        mutableStateOf(
            when {
                currentAutoDiscovery -> AUTO_KEY
                matchedKnown != null -> matchedKnown.host
                else -> CUSTOM_HOST_KEY
            }
        )
    }
    var customHost by remember { mutableStateOf(if (matchedKnown == null && !currentAutoDiscovery) currentHost else "") }
    var customPort by remember {
        mutableStateOf(if (matchedKnown == null && !currentAutoDiscovery) currentPort.toString() else "4433")
    }
    var customNodeId by remember {
        mutableStateOf(if (matchedKnown == null && !currentAutoDiscovery) currentNodeIdHex else "")
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Settings", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onBack) { Text("Back") }
            }

            Text(
                "Station",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Column(modifier = Modifier.selectableGroup()) {
                StationOptionRow(
                    label = "Automatic (nearest 3)",
                    sublabel = "Uses your location — no station to pick",
                    selected = selectedHost == AUTO_KEY,
                    onSelect = { selectedHost = AUTO_KEY },
                )
                KNOWN_STATIONS.forEach { station ->
                    StationOptionRow(
                        label = "${countryFlagEmoji(station.country)} ${station.city}, ${station.country}",
                        sublabel = station.host,
                        selected = selectedHost == station.host,
                        onSelect = { selectedHost = station.host },
                    )
                }
                StationOptionRow(
                    label = "Custom",
                    sublabel = null,
                    selected = selectedHost == CUSTOM_HOST_KEY,
                    onSelect = { selectedHost = CUSTOM_HOST_KEY },
                )
            }

            if (selectedHost == CUSTOM_HOST_KEY) {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    OutlinedTextField(
                        value = customHost,
                        onValueChange = { customHost = it },
                        label = { Text("Host") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = customPort,
                        onValueChange = { customPort = it },
                        label = { Text("Port") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    // The node id the station proves on the handshake: the
                    // mesh refuses a station dialed without one, so a
                    // custom station needs it as much as its host.
                    OutlinedTextField(
                        value = customNodeId,
                        onValueChange = { customNodeId = it.trim() },
                        label = { Text("Node id (64 hex characters)") },
                        isError = customNodeId.isNotEmpty() && !isNodeIdHex(customNodeId),
                        supportingText = { Text("Shown in the station directory as its id") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                enabled = selectedHost != CUSTOM_HOST_KEY ||
                    (customHost.isNotBlank() && customPort.toIntOrNull() != null && isNodeIdHex(customNodeId)),
                onClick = {
                    if (selectedHost == AUTO_KEY) {
                        onSaveAuto()
                    } else {
                        onSaveManual(resolvedStation(selectedHost, customHost, customPort, customNodeId))
                    }
                },
            ) {
                Text("Save")
            }
        }
    }
}

private fun resolvedStation(
    selectedHost: String,
    customHost: String,
    customPort: String,
    customNodeId: String,
): KnownStation {
    if (selectedHost != CUSTOM_HOST_KEY) {
        return KNOWN_STATIONS.first { it.host == selectedHost }
    }
    return KnownStation(customHost.trim(), customPort.toIntOrNull() ?: 4433, "", "", customNodeId.lowercase())
}

private val NODE_ID_HEX = Regex("^[0-9a-fA-F]{64}$")

private fun isNodeIdHex(text: String): Boolean = NODE_ID_HEX.matches(text)

@Composable
private fun StationOptionRow(label: String, sublabel: String?, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (sublabel != null) {
                Text(sublabel, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
