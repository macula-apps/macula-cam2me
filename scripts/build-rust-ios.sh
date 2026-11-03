#!/usr/bin/env bash
# Cross-compiles cam2me-ffi (cam2me's own top-level build target for
# macula-rust-ffi -- see rust/cam2me-ffi/src/lib.rs for why that
# indirection exists) for iOS (device + simulator, both architectures),
# packages an XCFramework, regenerates the Swift bindings, and copies
# both into the Xcode project.
#
# Requires: macOS with Xcode (for `xcodebuild -create-xcframework`), and
# the iOS Rust targets (`rustup target add aarch64-apple-ios
# aarch64-apple-ios-sim x86_64-apple-ios`).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FFI_CRATE_DIR="$REPO_ROOT/rust/cam2me-ffi"
SWIFT_OUT_DIR="$REPO_ROOT/ios/MaculaCam2Me/ffi"
XCFRAMEWORK_OUT="$REPO_ROOT/ios/MaculaRustFFI.xcframework"
# cam2me-ffi's own [lib] name is "cam2me_ffi", NOT "macula_rust_ffi" --
# giving it the identical name to macula-rust-ffi's own [lib] triggers a
# real Cargo "output filename collision" warning (see rust/cam2me-ffi/
# Cargo.toml for the full explanation). Unlike Android's jniLibs (where
# Kotlin's System.loadLibrary("macula_rust_ffi") does a runtime
# dlopen-by-name and the .so MUST be renamed -- see build-rust-android.sh),
# no rename is needed here: verified the generated Swift modulemap has no
# `link` directive (just `module macula_rust_ffiFFI { header ...;
# export * }`), so Xcode links whatever static lib is packaged into the
# XCFramework directly, not by string-matching a library name.
LIB_NAME="libcam2me_ffi.a"

# One deployment target for the C code (aws-lc, ring) and the Rust link,
# the app's own (ios/project.yml). Without it the cc crate compiles C for
# the SDK's newest iOS while rustc links for 10.0, and aws-lc's
# ___chkstk_darwin calls do not resolve in that link.
export IPHONEOS_DEPLOYMENT_TARGET="17.0"

if [[ "$(uname)" != "Darwin" ]]; then
    echo "This script must run on macOS (xcodebuild -create-xcframework is Apple-only)." >&2
    exit 1
fi

echo "==> Building cam2me-ffi for iOS device + simulator"
(
    cd "$FFI_CRATE_DIR"
    cargo build --release --target aarch64-apple-ios
    cargo build --release --target aarch64-apple-ios-sim
    cargo build --release --target x86_64-apple-ios
)

TARGET_DIR="$FFI_CRATE_DIR/target"
SIM_LIB_DIR="$(mktemp -d)"
trap 'rm -rf "$SIM_LIB_DIR"' EXIT

echo "==> Building a universal simulator (arm64 + x86_64) static lib via lipo"
lipo -create \
    "$TARGET_DIR/aarch64-apple-ios-sim/release/$LIB_NAME" \
    "$TARGET_DIR/x86_64-apple-ios/release/$LIB_NAME" \
    -output "$SIM_LIB_DIR/$LIB_NAME"

echo "==> Regenerating Swift bindings (headers included)"
rm -rf "$SWIFT_OUT_DIR"
mkdir -p "$SWIFT_OUT_DIR"
(
    cd "$FFI_CRATE_DIR"
    cargo run --release --bin uniffi-bindgen -- generate \
        --library "$TARGET_DIR/aarch64-apple-ios/release/$LIB_NAME" \
        --language swift \
        --out-dir "$SWIFT_OUT_DIR" \
        --no-format
)
# uniffi-bindgen's Swift output splits the C header/modulemap from the
# .swift source; the XCFramework wants the headers in their own dir, the
# .swift file compiled directly into the app target.
HEADERS_DIR="$SWIFT_OUT_DIR/headers"
mkdir -p "$HEADERS_DIR"
mv "$SWIFT_OUT_DIR"/*.h "$HEADERS_DIR/"
mv "$SWIFT_OUT_DIR"/*.modulemap "$HEADERS_DIR/module.modulemap"

echo "==> Assembling XCFramework"
rm -rf "$XCFRAMEWORK_OUT"
xcodebuild -create-xcframework \
    -library "$TARGET_DIR/aarch64-apple-ios/release/$LIB_NAME" -headers "$HEADERS_DIR" \
    -library "$SIM_LIB_DIR/$LIB_NAME" -headers "$HEADERS_DIR" \
    -output "$XCFRAMEWORK_OUT"

echo "==> Done. XCFramework: $XCFRAMEWORK_OUT"
echo "==> Done. Swift bindings: $SWIFT_OUT_DIR/*.swift"
