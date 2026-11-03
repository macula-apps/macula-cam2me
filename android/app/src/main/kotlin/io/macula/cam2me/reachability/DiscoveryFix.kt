package io.macula.cam2me.reachability

/**
 * How long discovery waits for one requested location fix before it gives
 * up on it and stays on the fallback station.
 */
internal const val FIX_REQUEST_TIMEOUT_MS = 10_000L

/** A coarse position in degrees: all nearest-station discovery needs, and
 * the shape the test seam below can be given without an Android runtime. */
internal data class Fix(val latitude: Double, val longitude: Double)

/**
 * The fix discovery runs on: the cached one when Android has one,
 * otherwise at most one requested fix, bounded in time.
 *
 * `requestOnce` is called at most once and never again after it answers
 * (or does not), so a phone with no cached fix asks the OS once per launch
 * and a phone whose providers cannot answer keeps the fallback station
 * instead of polling for a fix. Cancelling the calling coroutine cancels
 * the request: the Android implementation removes its listener then.
 */
internal suspend fun fixForDiscovery(
    cached: Fix?,
    timeoutMs: Long = FIX_REQUEST_TIMEOUT_MS,
    requestOnce: suspend () -> Fix?,
): Fix? = cached
