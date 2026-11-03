package io.macula.cam2me

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import io.crates.keyring.ensureAndroidNativeKeyringStoreInitialized
import io.macula.sdk.FfiException
import io.macula.sdk.FfiNodeKey
import io.macula.sdk.FfiPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import io.macula.cam2me.contacts.Contact
import io.macula.cam2me.contacts.ContactListScreen
import io.macula.cam2me.contacts.ContactRow
import io.macula.cam2me.onboarding.OnboardingScreen
import io.macula.cam2me.onboarding.OwnerPhoneNumber
import io.macula.cam2me.presence.DialTarget
import io.macula.cam2me.presence.PresenceHeartbeat
import io.macula.cam2me.reachability.KnownStation
import io.macula.cam2me.reachability.LocationFix
import io.macula.cam2me.reachability.KNOWN_STATIONS
import io.macula.cam2me.reachability.MeshPool
import io.macula.cam2me.reachability.NodeKeyPair
import io.macula.cam2me.reachability.SettingsScreen
import io.macula.cam2me.reachability.StationDiscovery
import io.macula.cam2me.reachability.StationPreference

/**
 * Wires this device's node keypair, nearest-station discovery, mesh
 * connect, the presence heartbeat and the contact list together. Camera
 * capture, dial/pickup and a real call UI are the next feature pass, not
 * this one.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Required once, before any FfiNodeKey keystore method runs on
        // Android -- see Keyring's own doc for why.
        ensureAndroidNativeKeyringStoreInitialized(applicationContext)

        var isReady by mutableStateOf(false)
        splashScreen.setKeepOnScreenCondition { !isReady }

        val db = Cam2MeDatabase.getInstance(applicationContext)

        setContent {
            MaterialTheme {
                Cam2MeApp(this, db, onReady = { isReady = true })
            }
        }
    }
}

@Composable
private fun Cam2MeApp(activity: ComponentActivity, db: Cam2MeDatabase, onReady: () -> Unit) {
    val context = activity.applicationContext

    val nodeKeyPair by produceState<FfiNodeKey?>(initialValue = null) {
        value = NodeKeyPair.loadOrCreate()
    }
    val ownerPhoneNumber by OwnerPhoneNumber.observe(context).collectAsStateWithLifecycle(initialValue = null)
    val stationPreference by StationPreference.observe(context)
        .collectAsStateWithLifecycle(initialValue = null)

    val currentNodeKeyPair = nodeKeyPair
    val currentPreference = stationPreference
    if (currentNodeKeyPair == null || currentPreference == null) {
        return
    }
    LaunchedEffect(Unit) { onReady() }

    val phoneNumber = ownerPhoneNumber
    if (phoneNumber == null) {
        OnboardingScreen(onPhoneNumberEntered = { entered ->
            activity.lifecycleScope.launch {
                OwnerPhoneNumber.set(context, entered)
            }
        })
        return
    }

    ConnectedApp(activity, db, currentNodeKeyPair, currentPreference, phoneNumber)
}

private sealed class AppScreen {
    object Contacts : AppScreen()
    object Settings : AppScreen()
}

@Composable
private fun ConnectedApp(
    activity: ComponentActivity,
    db: Cam2MeDatabase,
    nodeKeyPair: FfiNodeKey,
    preference: StationPreference.Preference,
    ownerPhoneNumber: String,
) {
    val context = activity.applicationContext
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Contacts) }
    var heartbeat by remember { mutableStateOf<PresenceHeartbeat?>(null) }

    var locationGranted by remember { mutableStateOf(LocationFix.hasPermission(context)) }
    val requestLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> locationGranted = granted }

    // Auto discovery needs the permission dialog exactly once per launch,
    // not once per recomposition -- Unit as the key, not
    // preference.autoDiscovery, so turning auto mode off and back on in
    // the same session doesn't re-prompt a user who already answered.
    LaunchedEffect(Unit) {
        if (preference.autoDiscovery && !locationGranted) {
            requestLocation.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    // A known station keeps its city for the indicator; a custom one is
    // shown by its host.
    val manualTarget = KNOWN_STATIONS.find {
        it.host == preference.stationHost && it.port == preference.stationPort
    } ?: KnownStation(preference.stationHost, preference.stationPort, "", "", preference.stationNodeIdHex)

    // Re-runs whenever the manual fallback OR auto-mode OR the permission
    // answer changes. Two-phase connect: the pool links to the fallback
    // station first (discovery needs SOME link to carry the call), then to
    // the nearest stations; MeshPool keeps the pool as it is when those are
    // the stations it already has. No station answering leaves the app
    // offline (the indicator says so) until one of the keys changes.
    LaunchedEffect(
        preference.autoDiscovery, preference.stationHost, preference.stationPort,
        preference.stationNodeIdHex, locationGranted,
    ) {
        heartbeat?.stop()
        heartbeat = null

        val bootstrap = connectOrNull(listOf(manualTarget), nodeKeyPair)

        val targets = if (preference.autoDiscovery && locationGranted && bootstrap != null) {
            val fix = LocationFix.lastKnown(context)
            val discovered = fix?.let {
                StationDiscovery.nearestStations(bootstrap, it.latitude, it.longitude, limit = 3)
            }
            discovered?.takeIf { it.isNotEmpty() } ?: listOf(manualTarget)
        } else {
            listOf(manualTarget)
        }

        val pool = connectOrNull(targets, nodeKeyPair) ?: return@LaunchedEffect
        heartbeat = PresenceHeartbeat(
            scope = activity.lifecycleScope,
            pool = pool,
            myPhoneNumber = ownerPhoneNumber,
            myStations = targets.map { DialTarget(it.host, it.port) },
            contactDao = db.contactDao(),
            phonePresenceDao = db.phonePresenceDao(),
        ).also { it.start() }
    }

    when (screen) {
        AppScreen.Settings -> {
            SettingsScreen(
                currentAutoDiscovery = preference.autoDiscovery,
                currentHost = preference.stationHost,
                currentPort = preference.stationPort,
                currentNodeIdHex = preference.stationNodeIdHex,
                onBack = { screen = AppScreen.Contacts },
                onSaveAuto = {
                    activity.lifecycleScope.launch {
                        if (!locationGranted) requestLocation.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                        StationPreference.setAutoDiscovery(context)
                    }
                    screen = AppScreen.Contacts
                },
                onSaveManual = { station ->
                    activity.lifecycleScope.launch {
                        StationPreference.setManualStation(context, station.host, station.port, station.nodeIdHex)
                    }
                    screen = AppScreen.Contacts
                },
            )
        }
        AppScreen.Contacts -> {
            ContactsScreen(activity, db, onOpenSettings = { screen = AppScreen.Settings })
        }
    }
}

@Composable
private fun ContactsScreen(
    activity: ComponentActivity,
    db: Cam2MeDatabase,
    onOpenSettings: () -> Unit,
) {
    val rows by remember {
        combine(db.contactDao().observeAll(), db.phonePresenceDao().observeForContacts()) { contacts, presenceList ->
            val presenceByNumber = presenceList.associateBy { it.phoneNumber }
            contacts.map { ContactRow(it, presenceByNumber[it.phoneNumber]) }
        }
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Ticks the online/offline indicator forward even when nothing new
    // arrives from Room -- otherwise a contact who stopped heartbeating
    // would stay "online" forever in a screen that never recomposes. The
    // station indicator follows the pool's links on the same tick: the
    // pool redials a dropped link by itself, and nothing else would tell
    // the screen.
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(5_000)
        }
    }
    val stationsUp by produceState(initialValue = MeshPool.stationsUp()) {
        while (true) {
            value = MeshPool.stationsUp()
            delay(5_000)
        }
    }

    ContactListScreen(
        contacts = rows,
        nowMs = nowMs,
        stationsUp = stationsUp,
        onAddContact = { phoneNumber, displayName ->
            activity.lifecycleScope.launch {
                db.contactDao().upsert(
                    Contact(
                        phoneNumber = phoneNumber,
                        displayName = displayName,
                        addedAtMs = System.currentTimeMillis(),
                    )
                )
            }
        },
        onOpenSettings = onOpenSettings,
    )
}

/** The pool linked to [targets], or null when no station answered. */
private suspend fun connectOrNull(targets: List<KnownStation>, key: FfiNodeKey): FfiPool? =
    try {
        MeshPool.connect(targets, key)
    } catch (_: FfiException) {
        null
    }
