package io.macula.cam2me.reachability

import org.junit.Assert.assertEquals
import org.junit.Test

/** When the discovered stations cannot be reached, the app falls back to
 * the station that carried discovery instead of going offline. */
class ConnectionPlanTest {
    private fun station(host: String, idByte: String) = KnownStation(host, 4433, "", "", idByte.repeat(32))

    private val fallback = station("fallback.example", "0f")
    private val nearest = listOf(station("a.example", "0a"), station("b.example", "0b"))

    @Test
    fun discoveredStationsFirstThenTheFallback() {
        assertEquals(listOf(nearest, listOf(fallback)), connectionPlan(nearest, fallback))
    }

    @Test
    fun noDiscoveryAnswerMeansOnlyTheFallback() {
        assertEquals(listOf(listOf(fallback)), connectionPlan(null, fallback))
        assertEquals(listOf(listOf(fallback)), connectionPlan(emptyList(), fallback))
    }

    @Test
    fun theFallbackIsNotTriedTwice() {
        assertEquals(listOf(listOf(fallback)), connectionPlan(listOf(fallback), fallback))
    }
}
