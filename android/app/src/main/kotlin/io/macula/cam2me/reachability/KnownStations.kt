package io.macula.cam2me.reachability

import io.macula.cam2me.hexToBytes

/** A station to link to: where it is dialed, the node id it must prove on
 * the handshake (a pool refuses a station that proves another one), and
 * where it is, for display.
 *
 * [KNOWN_STATIONS] is the fixed fallback: the six public stations as the
 * station directory (mcl-stations/list_stations, io.macula) lists them.
 * [StationDiscovery] returns the same shape from the live directory. */
data class KnownStation(
    val host: String,
    val port: Int,
    val city: String,
    val country: String,
    val nodeIdHex: String,
) {
    val nodeId: ByteArray get() = hexToBytes(nodeIdHex)
}

val KNOWN_STATIONS = listOf(
    KnownStation("station-de-frankfurt.macula.io", 4433, "Frankfurt", "DE",
        "00cd0008ec2e72b6572b7bf6fc8b048d7fe83993faf1fc544370f2bc1eb71f85"),
    KnownStation("station-de-falkenstein.macula.io", 4433, "Falkenstein", "DE",
        "00df68247d119685f94030afdb203ab7a2a105fb6093a964dbf0509a57e86435"),
    KnownStation("station-de-nuremberg.macula.io", 4433, "Nuremberg", "DE",
        "00a9b4143e24ae42e5a058dd28c9aab585636acd17012cc4d418a3bb5413af22"),
    KnownStation("station-fr-paris.macula.io", 4433, "Paris", "FR",
        "0063acc4a5af409ca6b15041975128d389222c48366fb3a1dfb948da01f7ca94"),
    KnownStation("station-nl-ams.macula.io", 4433, "Amsterdam", "NL",
        "000370eebafa9a89a44c9448b4796788fbff1885abd67c80d681d28cebb04b0c"),
    KnownStation("station-fi-helsinki.macula.io", 4433, "Helsinki", "FI",
        "004d1f470097ccf8826ce291900e882fdb1f20375e53901facaec0f23eb4efd8"),
)

/** ISO 3166-1 alpha-2 -> flag emoji, via the standard Unicode trick: each
 * letter maps to its own Regional Indicator Symbol (U+1F1E6 = 'A'), and
 * any two-letter code renders as that country's flag on every platform
 * with emoji support. No per-country lookup table -- this is arithmetic,
 * not data, so it never needs updating as new stations join. */
fun countryFlagEmoji(isoCode: String): String {
    if (isoCode.length != 2) return ""
    return isoCode.uppercase()
        .map { Character.toChars(0x1F1E6 + (it - 'A')) }
        .joinToString("") { String(it) }
}
