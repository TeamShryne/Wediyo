# Flow-fast Playback for Wediyo — Plan

## Goal
Replicate Flow's instant playback (VISIONOS token-free 98 formats + SABR + n-cascade + cache + preload) with its polished player UI.

## Why Go stays for extraction
- **Go = stream resolver** (fast, reusable via gomobile): add VISIONOS ladder → direct `url` adaptive formats, no WebView cipher, no PoToken for first frame. Go already owns `FetchVideoDetail` → extend to `FetchStreamsFast`.
- **Kotlin = player** (ExoPlayer/media3 must be Android). Go returns URLs + `thumbnailsJson` etc., Kotlin builds `MediaSource` (Dash/HLS/progressive + MergingMediaSource) with `CacheDataSource`, `LoadControl`, `TrackSelector`.

Answer: **Library split — Go for fast stream URLs (all qualities, decipher), Kotlin for ExoPlayer playback.** Keeps single source of truth, avoids duplicating decipher in Kotlin if Go VISIONOS succeeds.

## Steps (mirrors /tmp/flow)

1. **Deps** `gradle/libs.versions.toml` → `media3 1.11.0` (exoplayer, ui, common, session, dash, hls, datasource, okhttp), `newPipeExtractor v0.26.5` (fallback), `okhttp 5.4.0`; `app/build.gradle.kts` → `implementation(libs.androidx.media3.*)`, `coil-network-okhttp`, `foundation` already added.

2. **Go fast ladder** `go/wediyo/session.go` + `video.go`:
   - New func `fetchPlayerWithClient(session, videoId, clientName, version, userAgent, ...)` — builds `context/client` per YouTubeClient (VISIONOS 1.02 RealityDevice17,1 visionOS 26.5, plus WEB fallback).
   - `FetchVideoDetail` tries `VISIONOS → WEB` (like Flow `FAST_CLIENTS → BOT_RESISTANT`), picks first with `len(adaptiveFormats)>1 && hasUrl`.
   - Keep existing `parseStreamingFormats` but ensure `url` populated (VISIONOS gives direct URLs). If still cipher, keep `cipher` fields for Kotlin NewPipe fallback.

3. **Kotlin core** `app/src/main/kotlin/com/teamshryne/wediyo/player/`:
   - `PlayerCacheManager.kt` — `SimpleCache` 500 MB `SimpleCache + CacheDataSource.Factory(IGNORE_CACHE_ON_ERROR)` preloaded on `Dispatchers.IO`.
   - `YouTubeHttpDataSource.kt` — wrap `OkHttpDataSource.Factory` with YouTube headers (`Origin https://www.youtube.com`, `c=` User-Agent switch, `Referer`).
   - `LoadControlFactory.kt` — 3 profiles `forVideo` (20s/45s), `forShorts` (1.5s/8s), `forMusic` via `DefaultLoadControl.Builder` + `DefaultAllocator(64KB)`.
   - `PlayerFactory.kt` — `DefaultBandwidthMeter(5Mbps)`, `DefaultTrackSelector` (preferred mime, viewport, heap-capped max size), `ExoPlayer.Builder(handleAudioFocus, WAKE_MODE_LOCAL, SEEK_CLOSEST_SYNC)`.
   - `MediaLoader.kt` — priority: local file → DASH manifest (`dashManifestUrl`) → HLS → progressive MergingMediaSource (video+audio adaptive) → single progressive; merges captions via `MergingMediaSource`.

4. **Extraction bridge fallback** `player/stream/InnerTubeStreamBridge.kt` — if Go `url` empty (cipher case), use `NewPipeExtractor` + `yt.solver.core.js` copy to `transformN`/`deobfuscateSignature` (like Flow `CipherDeobfuscator`), else use Go direct URLs.

5. **Player UI** `ui/screens/player/PlayerScreen.kt` + `components/`:
   - `VideoPlayerSurface` (AndroidView `PlayerView` via `SurfaceManager`), `PlayerTopBar`, `PlayerTransportControls`, `PlayerSeekbarRow`+`SeekbarWithPreview`, `PlayerSettingsMenu` (Quality/Speed/Audio/Captions), gestures/zoom/ambient.
   - Wire into `VideoScreen` hero `Box` replace thumbnail with `PlayerView` when `detail != null`; `ShortsScreen` VerticalPager auto-play current page (like Flow shorts).

6. **Speed tricks** — `PlaybackPrefetcher` on tap `prefetch(videoId)` coalesced, `GaplessPreloadController` adds next `MediaItem` as second window, all off main thread; keep `InFlightRequestCoalescer` in Go for dedup.

## Verification
- `player.json` adaptive 108 now have `url` (VISIONOS) vs 0 before
- `go vet go/wediyo`, `./gradlew :app:assembleDebug` (GH `android.yml` uses committed AAR after `go.yml` builds `wediyo.aar`)
- Manual: VideoScreen 1080p/720p/480p switches in <500 ms, Shorts swipe instant, no 403 n/cipher.

## Speed claim
Matches Flow: VISIONOS no-token + 5 Mbps BW meter + 1.5s shorts buffer + cache + prewarm = <500 ms first frame, all qualities via adaptive DASH (not just progressive 360p).
