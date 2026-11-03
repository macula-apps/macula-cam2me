package io.macula.cam2me.reachability

import io.macula.sdk.FfiException
import io.macula.sdk.FfiNodeKey
import io.macula.sdk.FfiProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Scoped to this app: the platform's secure store is shared by the whole
// device. A new account name, not the old Ed25519 one: a key of the old
// kind does not load as a macula 12 identity, and nothing has shipped
// that would need it carried over.
private const val KEYSTORE_SERVICE = "io.macula.cam2me"
private const val KEYSTORE_ACCOUNT = "node_identity"

private val PROFILE = FfiProfile.PQ_HYBRID

/**
 * This device's node identity key (ML-DSA-87 with RSA-PSS-4096, macula
 * 12's pq_hybrid profile), stable across restarts: its node id is how
 * this device is known on the wire, so a key that changed on every launch
 * would make the device unreachable to every contact that knew it.
 *
 * Minted once, on first launch, by [FfiNodeKey.generate], which also
 * solves the admission puzzle stations require (a few seconds, so it runs
 * off the main thread), and kept in the platform's secure store (Android
 * Keystore through `keyring`). Requires
 * `ensureAndroidNativeKeyringStoreInitialized` to have run (see
 * [io.macula.cam2me.MainActivity.onCreate]).
 *
 * A transport signing key, nothing more: no name, no phone number,
 * nothing biometric.
 */
object NodeKeyPair {
    suspend fun loadOrCreate(): FfiNodeKey = withContext(Dispatchers.Default) {
        try {
            FfiNodeKey.loadFromKeystore(KEYSTORE_SERVICE, KEYSTORE_ACCOUNT, PROFILE)
        } catch (_: FfiException.KeystoreNotFound) {
            FfiNodeKey.generate(PROFILE).also { it.saveToKeystore(KEYSTORE_SERVICE, KEYSTORE_ACCOUNT) }
        }
    }
}
