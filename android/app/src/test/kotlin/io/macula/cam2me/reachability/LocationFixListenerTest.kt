package io.macula.cam2me.reachability

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The listener a requested fix uses must implement every method the
 * platform calls, not only the one that delivers a position. On API 26-29
 * the framework calls the other three directly, and a missing one is an
 * AbstractMethodError on the main looper -- the app dying because the user
 * switched location off, or a provider changed status, during the wait.
 *
 * It cannot be run here: the JVM sees the compile-SDK interface, where
 * those methods have defaults, and this box has no API 26-29 image. What
 * these tests check is the shape the framework dispatches on.
 */
class LocationFixListenerTest {
    private val frameworkCalls = setOf(
        "onLocationChanged",
        "onProviderDisabled",
        "onProviderEnabled",
        "onStatusChanged",
    )

    @Test
    fun everyMethodTheFrameworkCallsIsImplemented() {
        val declared = singleUpdateListener { }.javaClass.declaredMethods.map { it.name }.toSet()
        assertEquals(frameworkCalls, declared.intersect(frameworkCalls))
    }

    @Test
    fun switchingLocationOffAnswersNullAtOnce() {
        var answered = false
        var fix: Location? = null
        singleUpdateListener {
            answered = true
            fix = it
        }.onProviderDisabled("gps")
        assertTrue(answered)
        assertNull(fix)
    }

    @Test
    fun aProviderStatusChangeIsNotAnAnswer() {
        var answered = false
        singleUpdateListener { answered = true }.onStatusChanged("gps", 1, null)
        assertFalse(answered)
    }
}
