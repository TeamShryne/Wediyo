# Wediyo

Mobile YouTube client — **metadata only** for now, full playback later.

* Android app: Kotlin + Jetpack Compose, **highly compatible** (`minSdk 21` → Android 5.0+, `targetSdk 35`)
* Rust core: `rust/wediyo_engine` — metadata engine exposed to Kotlin via **UniFFI (proc-macro, no UDL)**
* Builds: **GitHub Actions only** — do NOT run `./gradlew` locally (this machine is bad)

## Project layout

```
Wediyo/
├── app/                                   # Android application (com.teamshryne.wediyo)
│   ├── build.gradle.kts                   # Compose, minSdk21, no auto-Rust regen
│   ├── src/main/kotlin/...                # MainActivity + theme
│   ├── src/main/kotlin/uniffi/            # COMMITTED UniFFI Kotlin bindings (via rust.yml)
│   └── src/main/jniLibs/<abi>/            # COMMITTED .so libs (via rust.yml)
├── rust/
│   ├── Cargo.toml                         # workspace
│   └── wediyo_engine/
│       ├── Cargo.toml                     # cdylib + uniffi 0.28.3, crate-type cdylib/staticlib
│       ├── src/lib.rs                     # metadata stubs + uniffi::setup_scaffolding!
│       └── uniffi-bindgen.rs              # helper bin for `cargo run --bin uniffi-bindgen`
├── gradle/wrapper/                        # checked in for CI
└── .github/workflows/
    ├── rust.yml                           # Rust → libs + UniFFI → commits outputs
    └── android.yml                        # APK only — uses committed outputs
```

## Workflows (split per your request)

**`rust.yml` — Rust libs + UniFFI → commit**
- Triggers on: `rust/**`, `rust-toolchain.toml`, manual
- Does: `cargo ndk` (arm64-v8a, armeabi-v7a, x86_64, API 21) → `app/src/main/jniLibs/`
- Then: `cargo run --bin uniffi-bindgen generate --library .../arm64-v8a/libwediyo_engine.so --language kotlin --out-dir app/src/main/kotlin/uniffi`
- Then: `git commit` + push `[skip ci]` — so Android build reuses committed artifacts

**`android.yml` — APK only**
- Triggers on: `app/**`, `gradle/**`, manual
- Verifies committed `jniLibs` + `uniffi` exist, then just `./gradlew assembleDebug/assembleRelease` — **no Rust regen**

> This avoids remaking bindings/APKs just because you touched `rust/`; Rust workflow commits once, Android workflow consumes.

## Quick start (local — but machine is bad, prefer CI)

```bash
# If you must test Rust locally:
cargo check -p wediyo_engine --manifest-path rust/Cargo.toml
cargo test -p wediyo_engine --manifest-path rust/Cargo.toml
cargo clippy -p wediyo_engine --manifest-path rust/Cargo.toml

# Do NOT run ./gradlew locally — push to trigger android.yml / rust.yml
```

## Rust + UniFFI

* No UDL — uses `uniffi::setup_scaffolding!("wediyo_engine")` in `lib.rs` + `#[uniffi::export]`/`#[derive(uniffi::Record/Error)]`
* No `build.rs` needed for proc-macro mode
* `uniffi.toml` sets Kotlin package `uniffi.wediyo_engine`

Add new function in Rust → `#[uniffi::export]` → push `rust/**` → `rust.yml` commits new Kotlin → `android.yml` builds APK:

```kotlin
import uniffi.wediyo_engine.*

val hello = helloWorld()
val meta = getVideoMetadata("dQw4w9WgXcQ")
val res = search("lo-fi beats")
```

## Compatibility (highly compatible across devices)

* `minSdk 21` (Android 5.0 Lollipop) — ~99.5% devices (2026)
* `compileSdk 35`, `targetSdk 35`, `build-tools 35.0.0`, `NDK 27.0.12077973`
* ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64` (x86_64 for emulators; drop armeabi if size matters)
* `android.useAndroidX=true`, `vectorDrawables.useSupportLibrary=true`, Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01

To raise minSdk to 24 saves ~4% coverage but smaller APK; 19 loses androidx.

## Next steps (playback phase)

* Replace `get_video_metadata` / `search` stubs with real Innertube API
* Enable `uniffi/tokio` + `async fn` for network
* Add `media3` / ExoPlayer
* Sign release builds (`signingConfigs` in `app/build.gradle.kts`)

## License

MIT OR Apache-2.0
