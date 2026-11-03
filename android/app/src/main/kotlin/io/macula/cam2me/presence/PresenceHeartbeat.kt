package io.macula.cam2me.presence

import io.macula.sdk.FfiDelivery
import io.macula.sdk.FfiEvent
import io.macula.sdk.FfiException
import io.macula.sdk.FfiPool
import io.macula.sdk.FfiSubscription
import io.macula.sdk.FfiValue
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import io.macula.cam2me.APP_REALM
import io.macula.cam2me.contacts.ContactDao

private const val HEARTBEAT_INTERVAL_MS = 30_000L

/** How long each wait for the next delivery blocks before this loop comes
 * back around to check whether a publish is due. Short enough that publish
 * stays close to on-schedule, long enough not to busy-loop. */
private const val POLL_TIMEOUT_MS = 5_000L

/** Type-level topic, no ids -- every device's own hashed phone number,
 * node id and stations live in the payload, never in the topic name. */
private const val PRESENCE_TOPIC = "cam2me.presence"

/**
 * Publishes this device's own (hashed) phone number and every station it
 * is linked through on [PRESENCE_TOPIC] every [HEARTBEAT_INTERVAL_MS], and
 * listens for the same from everyone else, updating [PhonePresenceDao]
 * wherever an incoming hash matches a known contact's phone number. The
 * sender's node id is [FfiEvent.publisher], verified by the SDK: no id
 * appears in the topic name or needs duplicating into the payload.
 *
 * One instance per pool ([io.macula.cam2me.reachability.MeshPool]): the
 * pool publishes on all its links and its subscription hears from all of
 * them, so one loop covers every station. [myStations] is the full set the
 * pool was opened for.
 *
 * A subscribe or publish that fails (no link up for a moment while the
 * pool redials, or a station refusing it) is tried again at the next
 * interval; a closed pool ends the loop. No [FfiException] leaves it.
 * The connection indicator shows offline from the pool's own status.
 *
 * The payload is the one the app has always sent; issue #4 redesigns it
 * (it exposes a hash of the phone number, and the sender is not bound to
 * it), so nothing here is meant to outlive that.
 */
/**
 * Subscribes through [subscribe], trying again every [retryIntervalMs]
 * while no link takes the register (none up yet, or the station refused
 * it): the pool redials, so a later try can succeed. Null once the pool is
 * closed, since nothing will take it then. Never throws an
 * [FfiException]: one escaping the heartbeat's coroutine would end the app.
 */
internal suspend fun <S> subscribeWhenLinked(retryIntervalMs: Long, subscribe: suspend () -> S): S? {
    while (true) {
        try {
            return subscribe()
        } catch (_: FfiException.Closed) {
            return null
        } catch (_: FfiException) {
            delay(retryIntervalMs)
        }
    }
}

class PresenceHeartbeat(
    private val scope: CoroutineScope,
    private val pool: FfiPool,
    private val myPhoneNumber: String,
    private val myStations: List<DialTarget>,
    private val contactDao: ContactDao,
    private val phonePresenceDao: PhonePresenceDao,
) {
    private var job: Job? = null

    fun start() {
        stop()
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun loop() {
        val subscription = subscribeWhenLinked(HEARTBEAT_INTERVAL_MS) {
            pool.subscribe(APP_REALM, PRESENCE_TOPIC)
        } ?: return
        try {
            listen(subscription)
        } finally {
            withContext(NonCancellable) {
                try {
                    subscription.unsubscribe()
                } catch (_: FfiException) {
                    // The pool closed first: nothing left to unsubscribe from.
                }
            }
        }
    }

    private suspend fun listen(subscription: FfiSubscription) {
        val myHash = sha256Hex(myPhoneNumber)
        var lastPublishAtMs = 0L

        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            if (now - lastPublishAtMs >= HEARTBEAT_INTERVAL_MS) {
                lastPublishAtMs = now
                try {
                    publish(myHash)
                } catch (_: FfiException.Closed) {
                    return
                } catch (_: FfiException) {
                    // Tried again at the next interval.
                }
            }

            val delivery = try {
                subscription.next(POLL_TIMEOUT_MS.toULong())
            } catch (_: FfiException.Closed) {
                return
            } catch (_: FfiException) {
                // One event this boundary could not carry (an unopened or
                // group-sealed one): skip it, keep listening.
                null
            }
            val event = (delivery as? FfiDelivery.Event)?.event
            if (event != null && event.topic == PRESENCE_TOPIC) {
                handleEvent(event)
            }
        }
    }

    private suspend fun publish(myHash: String) {
        val stations = JSONArray()
        myStations.forEach { stations.put(JSONObject().put("host", it.host).put("port", it.port)) }
        val payload = JSONObject()
            .put("phone_hash", myHash)
            .put("stations", stations)
            .toString()
        pool.publish(APP_REALM, PRESENCE_TOPIC, FfiValue.Text(payload), null)
    }

    private suspend fun handleEvent(event: FfiEvent) {
        val text = (event.payload as? FfiValue.Text)?.v1 ?: return
        val json = JSONObject(text)
        val phoneHash = json.optString("phone_hash").ifEmpty { return }
        val stationsArray = json.optJSONArray("stations") ?: return
        val stations = (0 until stationsArray.length()).mapNotNull { i ->
            val o = stationsArray.optJSONObject(i) ?: return@mapNotNull null
            val host = o.optString("host").ifEmpty { return@mapNotNull null }
            val port = o.optInt("port", -1).takeIf { it > 0 } ?: return@mapNotNull null
            DialTarget(host, port)
        }
        if (stations.isEmpty()) return

        // Self-delivery is harmless and needs no special-casing: this
        // device's own phone number is never one of its own contacts, so
        // its own heartbeat simply never matches anything below.
        val matched = contactDao.listAll().firstOrNull { sha256Hex(it.phoneNumber) == phoneHash }
            ?: return

        phonePresenceDao.upsert(
            PhonePresence(
                phoneNumber = matched.phoneNumber,
                nodeIdHex = event.publisher.joinToString("") { "%02x".format(it) },
                stationsJson = PhonePresence.encodeStations(stations),
                lastSeenOnlineAtMs = System.currentTimeMillis(),
            )
        )
    }

    private fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
