package io.macula.cam2me.reachability

import io.macula.cam2me.APP_REALM_TRUST
import io.macula.sdk.FfiNodeKey
import io.macula.sdk.FfiPool
import io.macula.sdk.FfiPoolOptions
import io.macula.sdk.FfiSeed

/**
 * This device's one link pool to the mesh: up to 3 stations at once, for
 * redundancy, not load-balancing. The pool holds the links itself and
 * redials one that drops, so this device stays reachable through any of
 * them while the network changes underneath it.
 *
 * Every seed carries the node id its station must prove on the handshake,
 * so a station that proves another identity is refused whatever host it
 * answers on. The pool pins io.macula's realm key ([APP_REALM_TRUST]):
 * only a provider that key authorises is trusted for a call.
 *
 * [connect] keeps the pool it holds when the wanted stations are the ones
 * it was opened for (a fresh QUIC handshake for nothing would defeat the
 * point of long-lived links), and otherwise closes it and opens one for
 * the new set.
 */
object MeshPool {
    private class Held(val pool: FfiPool, val targets: List<KnownStation>)

    @Volatile
    private var held: Held? = null

    /** Returns once one link is up. Throws [io.macula.sdk.FfiException]
     * (NoLink when no station answered) and leaves no pool held. */
    suspend fun connect(targets: List<KnownStation>, key: FfiNodeKey): FfiPool {
        val current = held
        if (current != null && current.targets.nodeIds() == targets.nodeIds()) {
            held = Held(current.pool, targets)
            return current.pool
        }
        held = null
        current?.pool?.shutDown()
        val pool = FfiPool.connect(
            key,
            targets.map { FfiSeed(it.host, it.port.toUShort(), it.nodeId) },
            FfiPoolOptions(realmTrust = listOf(APP_REALM_TRUST)),
        )
        held = Held(pool, targets)
        return pool
    }

    /** The wanted stations whose link is up right now, in the order they
     * were asked for (nearest first when discovery picked them). */
    fun stationsUp(): List<KnownStation> {
        val current = held ?: return emptyList()
        val up = current.pool.status().filter { it.up }.map { it.station.toHex() }.toSet()
        return current.targets.filter { it.nodeIdHex in up }
    }

    private fun List<KnownStation>.nodeIds(): Set<String> = map { it.nodeIdHex }.toSet()
}

/** The station sets to try, in order, once discovery has answered (or
 * not): the discovered stations, then the fallback that carried discovery.
 * [MeshPool.connect] closes the pool it holds before it dials a new set,
 * so a discovered set that cannot be reached must not leave the app
 * offline while the fallback answers. */
internal fun connectionPlan(discovered: List<KnownStation>?, fallback: KnownStation): List<List<KnownStation>> {
    val fallbackOnly = listOf(fallback)
    val nearest = discovered?.takeIf { it.isNotEmpty() && it != fallbackOnly } ?: return listOf(fallbackOnly)
    return listOf(nearest, fallbackOnly)
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
