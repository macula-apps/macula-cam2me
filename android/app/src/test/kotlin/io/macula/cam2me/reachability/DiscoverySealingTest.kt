package io.macula.cam2me.reachability

import io.macula.sdk.FfiConfidentiality
import org.junit.Assert.assertEquals
import org.junit.Test

/** list_stations carries this device's location: it is sealed, or not
 * sent (sealing is required by default, with no compat mode). */
class DiscoverySealingTest {
    @Test
    fun theDiscoveryCallRequiresSealing() {
        assertEquals(FfiConfidentiality.REQUIRED, DISCOVERY_CONFIDENTIALITY)
    }
}
