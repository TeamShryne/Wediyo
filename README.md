# Wediyo

Mobile YouTube client — **metadata only** for now, full playback later.

* Android app: Kotlin + Jetpack Compose, **highly compatible** (`minSdk 21` → Android 5.0+, `targetSdk 35`)
* Go core: `go/wediyo` — metadata engine exposed to Kotlin via **gomobile bind** (`com.teamshryne.wediyo`)
* Local library: **Room** (`data/local` — history, Watch Later, likes, custom playlists, subscriptions, search events) + **DataStore** prefs — on-device only, no login
* Builds: **GitHub Actions only** — do NOT run `./gradlew` locally (this machine is bad)

## Project layout

```
Wediyo/
├── app/                                   # Android application (com.teamshryne.wediyo)
│   ├── build.gradle.kts                   # Compose, minSdk21, Go AAR via files()
│   ├── src/main/kotlin/...                # MainActivity + theme
│   │   ├── data/local/                   # Room DB: videos/channels cache, history_events, progress, likes, watch_later, subscriptions, local_playlists, search_events
│   │   ├── ui/screens/library/           # You tab (history, Watch Later, liked, playlists)
│   │   └── ui/screens/subscriptions/     # Local-only followed channels
│   └── libs/wediyo.aar                    # COMMITTED Go AAR (via go.yml)
├── go/
│   └── wediyo/                            # Go metadata engine (pure Go, net/http)
│       ├── go.mod                         # module wediyo, go 1.26
│       ├── types.go                       # VideoMetadata/ChannelResult/ShortResult/TopicCard/SearchResult/InnertubeSession
│       ├── session.go                     # GET youtube.com → cookies + VISITOR_DATA/API_KEY
│       └── search.go                      # POST youtubei/v1/search + pagination
├── gradle/wrapper/                        # checked in for CI
└── .github/workflows/
    ├── go.yml                             # Go → AAR via gomobile → commits AAR
    └── android.yml                        # APK only — uses committed AAR
```

## Workflows

**`go.yml` — Go AAR → commit**
- Triggers on: `go/**`, manual
- Does: `go vet` + `go test` → `gomobile bind -target=android -androidapi 21 -javapkg com.teamshryne.wediyo -o app/libs/wediyo.aar .`
- Then: `git commit` + push — so Android build reuses committed AAR

**`android.yml` — APK only**
- Triggers on: `app/**`, `gradle/**`, manual, `workflow_run` from `go.yml`
- Verifies committed `app/libs/wediyo.aar` exists, then just `./gradlew assembleDebug/assembleRelease` — **no Go regen**

## Quick start (local — but machine is bad, prefer CI)

```bash
go vet ./... -C go/wediyo
go test ./... -C go/wediyo -run TestCollect -v
go test ./... -C go/wediyo -run TestLiveSearch -v  # live network
# Do NOT run ./gradlew locally — push to trigger go.yml / android.yml
```

## Go + gomobile

* `go/wediyo` is pure Go — no cgo, `net/http` + `encoding/json`
* `gomobile bind` generates `wediyo` Java package from exported `FetchInnertubeSession()` + `Search(session, query, continuation)` + structs

```kotlin
import wediyo.Wediyo
val session = Wediyo.fetchInnertubeSession() // stateless, caller holds
val r1 = Wediyo.search(session, "lo-fi beats", "") // page 1
val r2 = Wediyo.search(session, "lo-fi beats", r1.continuation) // pagination
r1.videos[0].title; r1.channels; r1.shorts; r1.topicCard; r1.estimatedResults
```

## Compatibility

* `minSdk 21` (Android 5.0 Lollipop) — ~99.5% devices (2026)
* `compileSdk 35`, `targetSdk 35`, `build-tools 35.0.0`, `NDK 27.0.12077973`
* ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86` (gomobile default, `androidapi 21`)
* `android.useAndroidX=true`, `vectorDrawables.useSupportLibrary=true`, Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01

## Next steps (playback)

* Add `player` / `next` Innertube endpoints
* Add `media3` / ExoPlayer
* Sign release builds (`signingConfigs` in `app/build.gradle.kts`)

## License

MIT OR Apache-2.0
