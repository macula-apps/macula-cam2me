package io.macula.cam2me.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.macula.cam2me.presence.PhonePresence
import io.macula.cam2me.reachability.KnownStation
import io.macula.cam2me.reachability.countryFlagEmoji

/** A contact is shown as online if a presence heartbeat matched it more
 * recently than this -- three heartbeat intervals (see PresenceHeartbeat's
 * own 30s interval), so one or two missed/delayed ticks don't flap the
 * indicator. */
private const val ONLINE_WINDOW_MS = 90_000L

data class ContactRow(val contact: Contact, val presence: PhonePresence?)

@Composable
fun ContactListScreen(
    contacts: List<ContactRow>,
    nowMs: Long,
    stationsUp: List<KnownStation>,
    onAddContact: (phoneNumber: String, displayName: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        // safeDrawingPadding: without it, the Settings button (right up
        // against the top edge) sits under the status bar's own
        // gesture-priority zone -- confirmed live, taps near the top of
        // that button were silently swallowed by the system rather than
        // reaching Compose at all.
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Contacts", style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StationIndicator(stationsUp)
                    TextButton(onClick = onOpenSettings) { Text("Settings") }
                }
            }
            if (contacts.isEmpty()) {
                Text(
                    "No contacts yet. Add one below.",
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(contacts, key = { it.contact.phoneNumber }) { row ->
                    ContactRowView(row, nowMs)
                }
            }
            Button(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                onClick = { showAddDialog = true },
            ) {
                Text("Add Contact")
            }
        }
    }

    if (showAddDialog) {
        AddContactDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { phoneNumber, displayName ->
                onAddContact(phoneNumber, displayName)
                showAddDialog = false
            },
        )
    }
}

/** Green when at least one of the pool's links is up, red otherwise: the
 * links [io.macula.cam2me.presence.PresenceHeartbeat] publishes on, so
 * "led is green" and "contacts can see me online" are the same fact.
 * Labeled with the nearest station that is up (the pool keeps the order
 * discovery returned, nearest first; see
 * [io.macula.cam2me.reachability.MeshPool.stationsUp]); a "+N" suffix
 * hints at the redundant links without spelling out all three in a
 * header that has to stay one line. */
@Composable
private fun StationIndicator(stationsUp: List<KnownStation>) {
    val primary = stationsUp.firstOrNull()
    // A manually-entered fallback station carries no city/country -- see
    // MainActivity's manualTarget -- so falls back to its raw host rather
    // than rendering an empty label between the dot and Settings.
    val label = primary?.let {
        if (it.city.isNotBlank()) "${countryFlagEmoji(it.country)} ${it.city}" else it.host
    } ?: "Offline"
    val extra = if (stationsUp.size > 1) " +${stationsUp.size - 1}" else ""

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(if (primary != null) Color(0xFF2E7D32) else Color(0xFFC62828), CircleShape),
        )
        Text(
            "$label$extra",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Bounded so a long fallback hostname (see the comment above)
            // truncates instead of squeezing Settings into a two-line wrap
            // -- found live: station-de-frankfurt.macula.io at full width
            // did exactly that.
            modifier = Modifier.padding(start = 6.dp).widthIn(max = 96.dp),
        )
    }
}

@Composable
private fun ContactRowView(row: ContactRow, nowMs: Long) {
    val online = row.presence != null && (nowMs - row.presence.lastSeenOnlineAtMs) < ONLINE_WINDOW_MS
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(row.contact.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(row.contact.phoneNumber, style = MaterialTheme.typography.bodySmall)
        }
        Text(if (online) "online" else "offline")
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onConfirm: (phoneNumber: String, displayName: String) -> Unit,
) {
    var phoneNumber by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Contact") },
        text = {
            Column {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone number") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = phoneNumber.isNotBlank() && displayName.isNotBlank(),
                onClick = { onConfirm(phoneNumber.trim(), displayName.trim()) },
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
