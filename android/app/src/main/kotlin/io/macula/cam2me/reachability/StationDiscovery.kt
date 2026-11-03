package io.macula.cam2me.reachability

import android.util.Log
import io.macula.cam2me.APP_REALM
import io.macula.sdk.FfiConfidentiality
import io.macula.sdk.FfiException
import io.macula.sdk.FfiPool
import io.macula.sdk.FfiValue

/** The station directory's procedure, served by mcl-stations in io.macula. */
private const val LIST_STATIONS_PROCEDURE = "mcl-stations/list_stations"
private const val CALL_TIMEOUT_MS = 8_000UL
private const val DEFAULT_QUIC_PORT = 4433
private const val NODE_ID_BYTES = 32
private const val TAG = "StationDiscovery"

/** The list_stations call carries this device's location, so it is sealed
 * or not sent: REQUIRED refuses a provider that names no KEM key, where
 * PREFERRED would send the location in the clear. */
internal val DISCOVERY_CONFIDENTIALITY = FfiConfidentiality.REQUIRED

/**
 * Finds the stations nearest to a device location by calling
 * mcl-stations/list_stations through the pool: the call reaches the
 * directory wherever it is served, so the station the pool is linked to
 * never needs to be one of the results.
 */
object StationDiscovery {
    /**
     * Null on any failure (no link, no provider, a timeout, a reply of
     * another shape) rather than a throw: the caller falls back to the
     * station it already has. A mobile network is exactly where this call
     * sometimes does not complete.
     */
    suspend fun nearestStations(pool: FfiPool, lat: Double, lng: Double, limit: Int): List<KnownStation>? {
        val payload = ffiFields(
            "near" to ffiFields(
                "lat" to FfiValue.Float(lat),
                "lng" to FfiValue.Float(lng),
                "limit" to FfiValue.Int(limit.toLong()),
            ),
            "limit" to FfiValue.Int(limit.toLong()),
        )
        val reply = try {
            pool.call(
                APP_REALM, LIST_STATIONS_PROCEDURE, payload,
                null, CALL_TIMEOUT_MS, DISCOVERY_CONFIDENTIALITY, null,
            )
        } catch (e: FfiException.Confidentiality) {
            Log.w(TAG, "list_stations not sent, it could not be sealed: ${e.reason}, ${e.advertised.size} keys advertised")
            return null
        } catch (e: FfiException) {
            Log.w(TAG, "list_stations failed: ${e::class.simpleName}")
            return null
        }
        val rows = reply.field("stations")?.asItems() ?: run {
            Log.w(TAG, "list_stations reply has no stations list")
            return null
        }
        val stations = rows.mapNotNull { it.toKnownStation() }.take(limit)
        Log.i(TAG, "list_stations: ${rows.size} rows, nearest ${stations.size}: ${stations.joinToString { it.host }}")
        return stations
    }

    /** A directory row as a station to dial. Its node_id is what the pool
     * pins, so a row without one is skipped. The hostname is dialed when
     * the row has one; otherwise its first advertised address. */
    private fun FfiValue.toKnownStation(): KnownStation? {
        val kind = field("kind")?.asText()
        if (kind != null && kind != "station") return null
        val nodeIdHex = field("node_id")?.asBytes()?.takeIf { it.size == NODE_ID_BYTES }?.toHex()
            ?: return null
        val host = field("hostname")?.asText()?.takeIf { it.isNotBlank() }
            ?: field("host_advertised")?.asItems()?.firstOrNull()?.asText()
            ?: return null
        val port = field("quic_port")?.asInt()?.toInt() ?: DEFAULT_QUIC_PORT
        val city = field("city")?.asText()?.replaceFirstChar { it.titlecase() } ?: ""
        val country = field("country")?.asText() ?: ""
        return KnownStation(host, port, city, country, nodeIdHex)
    }
}
