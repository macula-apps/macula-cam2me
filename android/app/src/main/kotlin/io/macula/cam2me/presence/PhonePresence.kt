package io.macula.cam2me.presence

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

/** One dial candidate: a station a contact was last heard advertising
 * itself through. */
data class DialTarget(val host: String, val port: Int)

/**
 * Where a phone number was last seen on the mesh. Filled in by matching
 * incoming `cam2me.presence` heartbeats -- payload carries a hash of the
 * sender's own phone number plus their current node_id and EVERY station
 * they're currently connected to (up to 3, for redundancy -- see
 * MeshPool) -- against [Contact.phoneNumber]. Never written for a
 * number that isn't a [Contact]; there's nothing to do with presence for a
 * number you haven't added.
 *
 * `stationsJson` rather than a Room relation table: at most 3 entries,
 * always read and written as a whole, never queried by individual station
 * -- a JSON column is the honest shape for that, not premature
 * normalization.
 */
@Entity(tableName = "phone_presence")
data class PhonePresence(
    @PrimaryKey val phoneNumber: String,
    val nodeIdHex: String,
    val stationsJson: String,
    val lastSeenOnlineAtMs: Long,
) {
    fun stations(): List<DialTarget> {
        val array = JSONArray(stationsJson)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            DialTarget(o.getString("host"), o.getInt("port"))
        }
    }

    companion object {
        fun encodeStations(stations: List<DialTarget>): String {
            val array = JSONArray()
            stations.forEach { array.put(JSONObject().put("host", it.host).put("port", it.port)) }
            return array.toString()
        }
    }
}
