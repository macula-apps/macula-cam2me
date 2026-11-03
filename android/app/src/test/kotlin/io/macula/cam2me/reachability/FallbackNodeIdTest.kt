package io.macula.cam2me.reachability

import org.junit.Assert.assertEquals
import org.junit.Test

/** The fallback station is pinned to the node id it must prove: the
 * default station's id belongs to the default host only. */
class FallbackNodeIdTest {
    private val frankfurt = KNOWN_STATIONS.first { it.host == "station-de-frankfurt.macula.io" }

    @Test
    fun nothingSavedPinsTheDefaultStation() {
        assertEquals(frankfurt.nodeIdHex, StationPreference.fallbackNodeIdHex(null, null))
        assertEquals(frankfurt.nodeIdHex, StationPreference.fallbackNodeIdHex(frankfurt.host, null))
    }

    @Test
    fun aCustomHostSavedWithoutAnIdIsNotPinnedToTheDefaultStation() {
        assertEquals("", StationPreference.fallbackNodeIdHex("my-station.example", null))
    }

    @Test
    fun aSavedIdIsKept() {
        val id = "ab".repeat(32)
        assertEquals(id, StationPreference.fallbackNodeIdHex("my-station.example", id))
    }
}
