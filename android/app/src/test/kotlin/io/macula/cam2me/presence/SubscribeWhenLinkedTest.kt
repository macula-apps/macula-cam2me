package io.macula.cam2me.presence

import io.macula.sdk.FfiException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The presence loop's subscribe: a station refusing the register, or no
 * link up yet, must not escape the heartbeat's coroutine (it would take
 * the app down at launch). */
@OptIn(ExperimentalCoroutinesApi::class)
class SubscribeWhenLinkedTest {
    private val interval = 30_000L

    @Test
    fun noLinkIsRetriedOnTheIntervalUntilARegisterIsTaken() = runTest {
        var attempts = 0
        val subscription = subscribeWhenLinked(interval) {
            attempts++
            if (attempts < 3) throw FfiException.NoLink("no link took the register")
            "subscribed"
        }
        assertEquals("subscribed", subscription)
        assertEquals(3, attempts)
        assertEquals(2 * interval, currentTime)
    }

    @Test
    fun aClosedPoolEndsTheLoopWithoutThrowing() = runTest {
        var attempts = 0
        val subscription = subscribeWhenLinked(interval) {
            attempts++
            throw FfiException.Closed()
        }
        assertNull(subscription)
        assertEquals(1, attempts)
    }
}
