package io.crates.keyring

import android.content.Context

/**
 * Required by the `android-native-keyring-store` Rust crate (a
 * `keyring`-crate backend `macula-rust-sdk`'s `KeyringStore` uses on
 * Android) — its own JNI export, `initializeNdkContext`, is only
 * reachable if a class with this exact package/name/signature exists on
 * the Kotlin side (`Java_io_crates_keyring_Keyring_00024Companion_initializeNdkContext`,
 * confirmed against the crate's own published docs). This name is fixed
 * by JNI's own symbol-mangling scheme — do not rename `initializeNdkContext`
 * or nest it differently, or the runtime linker will look for a symbol
 * that doesn't exist in the compiled library.
 *
 * The hook lives in `libmacula_rust_ffi.so` itself (macula-rust-ffi 0.13.1,
 * macula-io/macula-rust#63), so it sets the ndk-context of the very library
 * the SDK runs in; `scripts/build-rust-android.sh` ships no other library
 * and fails the build if that one does not export the hook. JNI resolves an
 * `external fun` only in libraries loaded through [System.loadLibrary], not
 * through JNA (which the generated bindings use), hence the explicit load:
 * both loads open the same library, so there is one ndk-context. Loading by
 * name matters: the APK keeps its native libraries uncompressed inside it
 * (the default since AGP 3.6), so `nativeLibraryDir` is empty on the device
 * and a directory listing finds nothing. Call
 * [ensureAndroidNativeKeyringStoreInitialized] once, before any keystore
 * access (see [io.macula.cam2me.MainActivity.onCreate]).
 */
class Keyring {
    companion object {
        external fun initializeNdkContext(context: Context)
    }
}

/** The SDK's library: the name macula-rust-ffi's uniffi.toml gives it. */
private const val SDK_LIBRARY = "macula_rust_ffi"

@Volatile
private var sdkLibraryLoaded = false

/**
 * Loads the SDK's library and calls [Keyring.initializeNdkContext].
 * Idempotent — safe to call more than once (e.g. across `onCreate` calls
 * after a config change).
 */
fun ensureAndroidNativeKeyringStoreInitialized(context: Context) {
    if (!sdkLibraryLoaded) {
        synchronized(Keyring::class) {
            if (!sdkLibraryLoaded) {
                System.loadLibrary(SDK_LIBRARY)
                sdkLibraryLoaded = true
            }
        }
    }
    Keyring.initializeNdkContext(context)
}
