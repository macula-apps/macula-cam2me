# macula-cam2me

Native Android + iOS apps that stream a phone's camera over the [macula
mesh](https://github.com/macula-io/macula-rust) — pull-model: other
mesh participants dial into the phone via a macula-station it's
connected to, not the other way around. Identity, presence, contacts,
and station connectivity (including nearest-station auto-discovery) are
built; camera capture itself is not yet.

## Architecture

The C4 model, what the app talks to and what it is made of, is in
[architecture/README.md](architecture/README.md). Its diagrams are drawn by
`architecture/c4_diagrams.py` into `assets/`.

![macula-cam2me system context](assets/c4-context.svg)

Two separate native apps, sharing nothing but the Rust core:

- `rust/cam2me-ffi/`: this app's own top-level build target, which depends on
  `macula-rust-ffi` from crates.io
- `android/`: Kotlin and Gradle, with bindings generated for JNA
- `ios/`: Swift and XcodeGen, with bindings generated into an XCFramework
- `scripts/`: cross-compile the Rust core and regenerate the bindings

No Flutter, no Kotlin Multiplatform. Both `macula-rust-ffi`'s Kotlin and
Swift bindings already exist and are already verified (generated,
inspected, and CI-checked in `macula-rust` itself); this repo just
consumes them, once per platform, the same way `iroh-ffi`'s own example
apps consume `iroh`.

`macula-rust-ffi` is consumed as a real `crates.io` dependency (`rust/
cam2me-ffi/Cargo.toml`), not a git submodule — this repo no longer has
any submodules. `rust/cam2me-ffi` exists only because an otherwise-empty
crate that merely depends on `macula-rust-ffi` gets its whole dependency
silently dropped at link time (the linker treats an unreferenced
dependency as dead code, `#[no_mangle] extern "C"` doesn't save it); see
the doc comment on `rust/cam2me-ffi/src/lib.rs`'s `pub use` line for the
verified fix. Its own `[lib] name` is deliberately `cam2me_ffi`, not
`macula_rust_ffi` (that would collide with `macula-rust-ffi`'s own `[lib]
name` at the Cargo level) — `scripts/build-rust-*.sh` rename the final
build artifact to the `macula_rust_ffi` name the generated bindings
actually expect at runtime. See that crate's `Cargo.toml` for the full
reasoning.

Neither app links against a prebuilt binary. `scripts/build-rust-*.sh`
cross-compile `cam2me-ffi` (pulling in `macula-rust-ffi` at its pinned
crates.io version) and regenerate the Kotlin/Swift source fresh every
time — nothing FFI-shaped is committed to this repo, matching
`macula-rust`'s own "don't commit generated code" convention.

## Building

### Android

Requires: Android NDK (`ANDROID_NDK_HOME` set), `cargo-ndk`
(`cargo install cargo-ndk`), and the Android Rust targets:

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
./scripts/build-rust-android.sh
cd android && ./gradlew build   # or: gradle wrapper --gradle-version 9.1 first,
                                 # the wrapper jar itself isn't committed
```

Or just open `android/` in Android Studio after running the build
script once — it'll offer to generate the Gradle wrapper itself.

### iOS

Requires: macOS with Xcode, [XcodeGen](https://github.com/yonaskolb/XcodeGen)
(`brew install xcodegen`), and the iOS Rust targets:

```sh
rustup target add aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios
./scripts/build-rust-ios.sh
cd ios && xcodegen generate
open MaculaCam2Me.xcodeproj
```

**Honest status note:** this repo was scaffolded from a Linux
environment with no Android SDK/NDK and no macOS/Xcode available
locally. The Android side follows a well-established, standard Gradle
shape; the iOS side is built through XcodeGen specifically *because*
hand-authoring a raw `.xcodeproj` without ever being able to open it in
Xcode is too fragile to trust — XcodeGen's `project.yml` is a much
smaller, well-documented surface to get right blind. Both are verified
by CI (`.github/workflows/android.yml` on `ubuntu-latest`,
`ios.yml` on `macos-latest`, which has real Xcode) rather than by local
testing. Check the Actions tab before assuming either one actually
builds.

## Current status

Both apps generate a puzzle-hardened Ed25519 identity via
`FfiKeyPair.generate()`. Android keeps it across restarts in the Android
Keystore; iOS still mints a new one on every launch
([#5](https://github.com/macula-apps/macula-cam2me/issues/5)). Android additionally
has: a contact list (Room-backed), a presence heartbeat (pubsub, hashed
phone numbers, online/offline status), and station connectivity --
either up to 3 nearest stations auto-discovered via device location and
`hecate_stations.list_stations` (the default; see `MeshSessionPool`,
`StationDiscovery`), or a single manually-picked station when location
access is off. Both connection modes maintain one or more
`FfiSession`s and run a `PresenceHeartbeat` per session.

None of that reaches the live fleet today: the pinned Rust core predates wire
1.0, the realm constant is not io.macula's, and the directory has since been
renamed `mcl-stations/list_stations`
([#3](https://github.com/macula-apps/macula-cam2me/issues/3)). The presence
payload needs a redesign before it goes live
([#4](https://github.com/macula-apps/macula-cam2me/issues/4)).

Camera capture, dial/pickup, and a real call UI are not built yet --
that's the next pass, on both platforms.

## See also

- [macula-io/macula-rust](https://github.com/macula-io/macula-rust) —
  the Rust core + UniFFI bindings this repo consumes, published on
  crates.io as `macula-rust` / `macula-rust-ffi`. Its
  `plans/PLAN_WIRE_PROTOCOL.md` §13 covers the streaming RPC provider
  role this app will use to answer inbound viewers.
