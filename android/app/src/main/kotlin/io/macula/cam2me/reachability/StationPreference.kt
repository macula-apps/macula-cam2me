package io.macula.cam2me.reachability

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.stationPreferenceDataStore by preferencesDataStore(name = "station_preference")

private val AUTO_DISCOVERY = booleanPreferencesKey("auto_discovery")
private val STATION_HOST = stringPreferencesKey("station_host")
private val STATION_PORT = intPreferencesKey("station_port")
private val STATION_NODE_ID = stringPreferencesKey("station_node_id")

/**
 * How this device picks which station(s) to connect to: auto-discover
 * the nearest via GPS (the default -- see [StationDiscovery]), or a
 * manually-picked fallback station used either while auto discovery is
 * off, or as the bootstrap link auto discovery itself needs before it can
 * call mcl-stations/list_stations. A station is kept with the node id it
 * must prove: the pool refuses a seed without one. The fallback defaults
 * to the Frankfurt public station.
 */
object StationPreference {
    private const val DEFAULT_STATION_HOST = "station-de-frankfurt.macula.io"
    private const val DEFAULT_STATION_PORT = 4433
    private val DEFAULT_STATION_NODE_ID = KNOWN_STATIONS.first { it.host == DEFAULT_STATION_HOST }.nodeIdHex

    data class Preference(
        val autoDiscovery: Boolean,
        val stationHost: String,
        val stationPort: Int,
        val stationNodeIdHex: String,
    )

    fun observe(context: Context): Flow<Preference> =
        context.stationPreferenceDataStore.data.map { prefs ->
            Preference(
                autoDiscovery = prefs[AUTO_DISCOVERY] ?: true,
                stationHost = prefs[STATION_HOST] ?: DEFAULT_STATION_HOST,
                stationPort = prefs[STATION_PORT] ?: DEFAULT_STATION_PORT,
                stationNodeIdHex = prefs[STATION_NODE_ID] ?: DEFAULT_STATION_NODE_ID,
            )
        }

    suspend fun setAutoDiscovery(context: Context) {
        context.stationPreferenceDataStore.edit { it[AUTO_DISCOVERY] = true }
    }

    /** Picking a specific station is an explicit override -- it turns
     * auto discovery off until [setAutoDiscovery] is chosen again. */
    suspend fun setManualStation(context: Context, host: String, port: Int, nodeIdHex: String) {
        context.stationPreferenceDataStore.edit {
            it[AUTO_DISCOVERY] = false
            it[STATION_HOST] = host
            it[STATION_PORT] = port
            it[STATION_NODE_ID] = nodeIdHex.lowercase()
        }
    }
}
