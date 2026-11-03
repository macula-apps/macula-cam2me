package io.macula.cam2me.reachability

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A phone with no cached location fix must still discover its nearest
 * stations (#9): ask for exactly one fix, bounded in time, and run
 * discovery on the answer. Every failure keeps the fallback, unchanged. */
class DiscoveryFixTest {
    private val here = Fix(50.8798, 4.7010)

    /** Counts the requests the seam makes, and answers what the test says. */
    private class Requests(private val answer: Fix?) {
        var count = 0

        suspend fun ask(): Fix? {
            count += 1
            return answer
        }
    }

    @Test
    fun aCachedFixIsUsedWithoutAsking() = runTest {
        val requests = Requests(here)
        assertEquals(here, fixForDiscovery(here) { requests.ask() })
        assertEquals(0, requests.count)
    }

    @Test
    fun noCachedFixAsksOnceAndUsesTheAnswer() = runTest {
        val requests = Requests(here)
        assertEquals(here, fixForDiscovery(null) { requests.ask() })
        assertEquals(1, requests.count)
    }

    @Test
    fun aRequestThatAnswersNothingIsNotRetried() = runTest {
        val requests = Requests(null)
        assertNull(fixForDiscovery(null) { requests.ask() })
        assertEquals(1, requests.count)
    }

    @Test
    fun aRequestThatOverrunsTheLimitGivesUpAndIsNotRetried() = runTest {
        var count = 0
        val fix = fixForDiscovery(null) {
            count += 1
            delay(Long.MAX_VALUE)
            here
        }
        assertNull(fix)
        assertEquals(1, count)
    }
}
