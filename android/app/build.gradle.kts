plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.macula.cam2me"
    // compose-bom's own transitive deps (material-ripple-android 1.12.0)
    // require API 37+ to compile against -- caught by CI's
    // checkDebugAarMetadata failing outright with 36, not assumed.
    compileSdk = 37

    defaultConfig {
        applicationId = "io.macula.cam2me"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    // compileDebugKotlin defaults its JVM target to the JDK actually
    // running Gradle (Temurin 21 in CI) while compileDebugJavaWithJavac
    // defaults to 11 -- AGP fails the build outright on that mismatch
    // rather than silently picking one. Matching both to 21 here avoids
    // provisioning a second JDK toolchain just to reconcile them.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    // Populated by ../../scripts/build-rust-android.sh, which cross-compiles
    // macula-rust-sdk-ffi via cargo-ndk and drops one libmacula_rust_sdk_ffi.so
    // per ABI here. Not committed -- build artifacts, regenerated locally or
    // in CI before this module ever compiles.
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }

    lint {
        // The generated bindings (io/macula/sdk/) guard their one API-33+
        // call (java.lang.ref.Cleaner#create) behind a runtime
        // Class.forName/catch-ClassNotFoundException check, falling back
        // to a JNA-based cleaner below minSdk 33 -- genuinely safe, but
        // Lint's static NewApi check can't see through reflection-based
        // guards and flags it as an outright violation regardless.
        // Verified directly in the generated source, not assumed.
        disable += "NewApi"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    // lifecycleScope (presence heartbeat's coroutine scope, tied to the
    // Activity rather than leaking past it) and collectAsStateWithLifecycle
    // (pauses Room/DataStore Flow collection while backgrounded, instead
    // of the plain collectAsState that keeps collecting regardless).
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    // The splash theme (res/values/themes.xml) targets Theme.SplashScreen,
    // which only exists natively on API 31+ -- this compat library backports
    // it down to minSdk 26 and is required for installSplashScreen() to do
    // anything below 31.
    implementation("androidx.core:core-splashscreen:1.2.0")

    // Generated Kotlin bindings (io.macula.sdk package, under
    // src/main/kotlin/io/macula/sdk/) call into the native lib above via
    // JNA, and every async method (all of them, on FfiSession/FfiStream)
    // is a Kotlin suspend fun bridged through kotlinx.coroutines --
    // confirmed by inspecting the real generated file's own imports, not
    // assumed. Both are runtime requirements of the generated code
    // itself, not a choice made here.
    implementation("net.java.dev.jna:jna:5.17.0@aar")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Local storage for contacts and presence -- structured, queryable
    // data that grows, unlike the handful of scalar settings below.
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    // A handful of scalars this device needs once and reads on every
    // launch: the persisted identity seed, this device's own phone
    // number, and station host/port. Room would be the wrong tool for
    // single-row config like this.
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // JVM unit tests (src/test), run by `gradle build`: the app's own
    // connection decisions, with the SDK's calls replaced by lambdas.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

ksp {
    // Keeps Room's schema-history JSON alongside the code, the
    // documented default for exportSchema = true -- otherwise Room
    // emits a build warning every compile.
    arg("room.schemaLocation", "$projectDir/schemas")
}
