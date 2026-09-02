package com.teamshryne.wediyo.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.teamshryne.wediyo.data.model.UiAudioTrack
import com.teamshryne.wediyo.data.model.UiCaptionTrack
import com.teamshryne.wediyo.data.model.UiStreamingFormat
import com.teamshryne.wediyo.data.model.UiVideoDetail
import com.teamshryne.wediyo.player.cache.PlayerCacheManager
import com.teamshryne.wediyo.player.datasource.YouTubeDataSource
import com.teamshryne.wediyo.player.factory.AppContextHolder
import com.teamshryne.wediyo.player.factory.PlayerFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@UnstableApi
class PlayerManager private constructor() {
    companion object {
        @Volatile private var inst: PlayerManager? = null
        fun get(): PlayerManager = inst ?: synchronized(this) { inst ?: PlayerManager().also { inst = it } }
        private fun subtitleId(index: Int) = "wediyo-subtitle-$index"
        private fun timedTextUrl(baseUrl: String): String {
            if (baseUrl.isBlank()) return baseUrl
            val qIdx = baseUrl.indexOf('?')
            val path = if (qIdx >= 0) baseUrl.substring(0, qIdx) else baseUrl
            val query = if (qIdx >= 0) baseUrl.substring(qIdx + 1) else ""
            val retained = query.split('&').filter { it.isNotEmpty() && !it.startsWith("fmt=", ignoreCase = true) && !it.startsWith("tlang=", ignoreCase = true) }.toMutableList()
            retained.add("fmt=vtt")
            return "$path?${retained.joinToString("&")}"
        }
    }

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var context: Context? = null
    private var isShortsMode: Boolean = false
    var lastVideoId: String? = null
        private set
    private var currentDetail: UiVideoDetail? = null
    private var selectedCaptionLang: String? = null // null or "off" means disabled
    private var selectedAudioTrackId: String? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    private val _duration = MutableStateFlow(0L)
    private val _position = MutableStateFlow(0L)

    fun ensure(context: Context, isShorts: Boolean = false): ExoPlayer {
        if (player != null && isShortsMode == isShorts) return player!!
        release()
        AppContextHolder.init(context)
        this.context = context.applicationContext
        this.isShortsMode = isShorts
        val (p, ts) = PlayerFactory.createPlayer(context, isShorts)
        trackSelector = ts
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) { _isPlaying.value = v }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) _duration.value = p.duration.coerceAtLeast(0L)
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                // when tracks become available, re-apply pending caption selection (ensures off/on is respected after reload)
                selectedCaptionLang?.let { lang ->
                    if (lang == "off") disableCaptionsInternal() else applyCaptionSelectionInternal(lang)
                } ?: run {
                    // default: keep disabled unless user enabled
                    // do not auto-enable; respect previous state (if null, keep disabled)
                }
            }
        })
        player = p
        return p
    }

    fun playDetail(context: Context, detail: UiVideoDetail, startMs: Long = 0, isShorts: Boolean = false, preferredHeight: Int? = null) {
        val p = ensure(context, isShorts)
        lastVideoId = detail.videoId
        currentDetail = detail
        val source = buildSource(context, detail, preferredHeight, selectedAudioTrackId, selectedCaptionLang)
        if (source == null) return
        p.setMediaSource(source, startMs)
        p.prepare()
        p.playWhenReady = true
        // apply caption state after prepare (tracks may not be ready yet, but listener will re-apply)
        if (selectedCaptionLang == null || selectedCaptionLang == "off") {
            disableCaptionsInternal()
        } else {
            // will be applied onTracksChanged; also try now
            applyCaptionSelectionInternal(selectedCaptionLang)
        }
    }

    fun playDetailWithQuality(context: Context, detail: UiVideoDetail, quality: String, startMs: Long = 0, isShorts: Boolean = false) {
        val cleaned = quality.lowercase().removeSuffix("p").trim()
        val h = cleaned.toIntOrNull()
        playDetail(context, detail, startMs, isShorts = isShorts, preferredHeight = h)
    }

    fun playUrl(context: Context, url: String, isShorts: Boolean = false) {
        val p = ensure(context, isShorts)
        val item = MediaItem.fromUri(url)
        p.setMediaItem(item, 0)
        p.prepare()
        p.playWhenReady = true
    }

    fun playerOrNull(): ExoPlayer? = player
    fun trackSelectorOrNull(): DefaultTrackSelector? = trackSelector

    fun release() {
        player?.release()
        player = null
        trackSelector = null
    }

    fun pause() { player?.pause() }
    fun resume() { player?.play() }
    fun seekTo(ms: Long) { player?.seekTo(ms) }

    private fun buildSource(context: Context, d: UiVideoDetail, preferredHeight: Int? = null, audioTrackId: String? = null, captionLang: String? = null): androidx.media3.exoplayer.source.MediaSource? {
        val hasQualityPreference = preferredHeight != null && preferredHeight > 0
        val hasAudioPreference = !audioTrackId.isNullOrBlank()
        val forceMerge = hasQualityPreference || hasAudioPreference
        var baseSource: androidx.media3.exoplayer.source.MediaSource? = null
        if (!forceMerge) {
            if (d.dashManifestUrl.isNotBlank()) {
                val ds = YouTubeDataSource.factory(context)
                baseSource = DashMediaSource.Factory(ds).createMediaSource(MediaItem.fromUri(d.dashManifestUrl))
            } else if (d.hlsManifestUrl.isNotBlank()) {
                val ds = YouTubeDataSource.factory(context)
                baseSource = HlsMediaSource.Factory(ds).createMediaSource(MediaItem.fromUri(d.hlsManifestUrl))
            }
        }
        if (baseSource == null) {
            val videoFormats = d.adaptiveFormats.filter { !it.isAudio && it.url.isNotBlank() }
                .sortedWith(compareByDescending<UiStreamingFormat> { it.height }.thenByDescending { it.bitrate })
            val audioFormats = d.adaptiveFormats.filter { it.isAudio && it.url.isNotBlank() }
                .sortedByDescending { it.bitrate }
            if (videoFormats.isNotEmpty() && audioFormats.isNotEmpty()) {
                val bestVideo = if (preferredHeight != null && preferredHeight > 0) {
                    videoFormats.filter { it.height == preferredHeight }.maxByOrNull { it.bitrate }
                        ?: videoFormats.minByOrNull { kotlin.math.abs(it.height - preferredHeight) }
                        ?: videoFormats.first()
                } else {
                    videoFormats.first()
                }
                val bestAudio = selectAudioFormat(audioFormats, audioTrackId)
                baseSource = merging(context, bestVideo.url, bestAudio.url, pickMime(bestVideo.mimeType), pickMime(bestAudio.mimeType))
            } else {
                val prog = d.formats.firstOrNull { it.url.isNotBlank() } ?: videoFormats.firstOrNull()
                if (prog != null && prog.url.isNotBlank()) {
                    val cacheDs = YouTubeDataSource.factory(context)
                    baseSource = ProgressiveMediaSource.Factory(cacheDs).createMediaSource(MediaItem.fromUri(prog.url))
                } else if (videoFormats.isNotEmpty()) {
                    val cacheDs = YouTubeDataSource.factory(context)
                    baseSource = ProgressiveMediaSource.Factory(cacheDs).createMediaSource(MediaItem.fromUri(videoFormats.first().url))
                }
            }
        }
        if (baseSource == null) return null
        // Merge subtitles if available
        if (d.captionTracks.isEmpty()) return baseSource
        val subtitleSources = d.captionTracks.mapIndexedNotNull { index, ct ->
            if (ct.baseUrl.isBlank() || ct.languageCode.isBlank()) return@mapIndexedNotNull null
            createSubtitleSource(context, ct, index)
        }
        if (subtitleSources.isEmpty()) return baseSource
        return MergingMediaSource(true, true, baseSource, *subtitleSources.toTypedArray())
    }

    private fun selectAudioFormat(audioFormats: List<UiStreamingFormat>, preferredId: String?): UiStreamingFormat {
        if (preferredId.isNullOrBlank() || preferredId.trim().equals("original", ignoreCase = true)) {
            // Prefer original (isDefault && !isAutoDubbed) if exists, else any non-dubbed, else highest bitrate
            val original = audioFormats.filter { it.audioTrack?.isDefault == true && it.audioTrack?.isAutoDubbed == false }
            if (original.isNotEmpty()) return original.maxByOrNull { it.bitrate } ?: audioFormats.first()
            val nonDubbed = audioFormats.filter { it.audioTrack?.isAutoDubbed == false }
            if (nonDubbed.isNotEmpty()) return nonDubbed.maxByOrNull { it.bitrate } ?: audioFormats.first()
            return audioFormats.first()
        }
        val normalized = preferredId.trim()
        // Try exact id match
        val byId = audioFormats.filter { it.audioTrack?.id == normalized }
        if (byId.isNotEmpty()) return byId.maxByOrNull { it.bitrate } ?: byId.first()
        // Try displayName contains
        val byName = audioFormats.filter { it.audioTrack?.displayName?.contains(normalized, ignoreCase = true) == true }
        if (byName.isNotEmpty()) return byName.maxByOrNull { it.bitrate } ?: byName.first()
        // Try language prefix on id
        val byLang = audioFormats.filter { it.audioTrack?.id?.lowercase()?.contains(normalized.lowercase()) == true }
        if (byLang.isNotEmpty()) return byLang.maxByOrNull { it.bitrate } ?: byLang.first()
        // Fallback displayName lower
        val byLang2 = audioFormats.filter { it.audioTrack?.displayName?.lowercase()?.contains(normalized.lowercase()) == true }
        if (byLang2.isNotEmpty()) return byLang2.maxByOrNull { it.bitrate } ?: byLang2.first()
        return audioFormats.first()
    }

    private fun createSubtitleSource(context: Context, ct: UiCaptionTrack, index: Int): androidx.media3.exoplayer.source.MediaSource? {
        return try {
            val url = timedTextUrl(ct.baseUrl)
            if (url.isBlank()) return null
            val uri = Uri.parse(url)
            val isAsr = ct.kind.equals("asr", ignoreCase = true)
            val label = ct.name.ifBlank { ct.languageCode }.ifBlank { "Unknown" }
            val fullLabel = if (isAsr) "$label (Auto)" else label
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(ct.languageCode)
                .setLabel(fullLabel)
                .setSelectionFlags(0)
                .setRoleFlags(if (isAsr) C.ROLE_FLAG_SUBTITLE or C.ROLE_FLAG_TRANSCRIBES_DIALOG else C.ROLE_FLAG_SUBTITLE)
                .setId(subtitleId(index))
                .build()
            val ds = YouTubeDataSource.factory(context)
            SingleSampleMediaSource.Factory(ds)
                .setTreatLoadErrorsAsEndOfStream(true)
                .createMediaSource(subtitleConfig, C.TIME_UNSET)
        } catch (_: Exception) { null }
    }

    private fun merging(context: Context, videoUrl: String, audioUrl: String, videoMime: String, audioMime: String): MergingMediaSource {
        val ds = YouTubeDataSource.factory(context)
        val videoItem = MediaItem.Builder().setUri(videoUrl).setMimeType(videoMime).build()
        val audioItem = MediaItem.Builder().setUri(audioUrl).setMimeType(audioMime).build()
        val videoSource = ProgressiveMediaSource.Factory(ds).createMediaSource(videoItem)
        val audioSource = ProgressiveMediaSource.Factory(ds).createMediaSource(audioItem)
        return MergingMediaSource(videoSource, audioSource)
    }

    private fun pickMime(raw: String): String {
        if (raw.isBlank()) return MimeTypes.VIDEO_MP4
        return raw.substringBefore(";").trim().ifBlank { MimeTypes.VIDEO_MP4 }
    }

    fun switchQuality(context: Context, detail: UiVideoDetail, height: Int) {
        val p = player ?: return
        val pos = p.currentPosition
        val wasPlaying = p.isPlaying
        currentDetail = detail
        val videoFormats = detail.adaptiveFormats.filter { !it.isAudio && it.url.isNotBlank() }
        val audioFormats = detail.adaptiveFormats.filter { it.isAudio && it.url.isNotBlank() }.sortedByDescending { it.bitrate }
        if (videoFormats.isNotEmpty() && audioFormats.isNotEmpty()) {
            val target = if (height == 0) {
                videoFormats.maxByOrNull { it.height }
            } else {
                videoFormats.filter { it.height == height }.maxByOrNull { it.bitrate }
                    ?: videoFormats.minByOrNull { kotlin.math.abs(it.height - height) }
            } ?: return
            val audio = selectAudioFormat(audioFormats, selectedAudioTrackId)
            val src = merging(context, target.url, audio.url, pickMime(target.mimeType), pickMime(audio.mimeType))
            // merge subtitles again
            val finalSrc = if (detail.captionTracks.isNotEmpty()) {
                val subs = detail.captionTracks.mapIndexedNotNull { i, ct -> if (ct.baseUrl.isBlank()) null else createSubtitleSource(context, ct, i) }
                if (subs.isNotEmpty()) MergingMediaSource(true, true, src, *subs.toTypedArray()) else src
            } else src
            p.setMediaSource(finalSrc, pos)
            p.prepare()
            p.playWhenReady = wasPlaying
            // re-apply caption selection after switch
            if (selectedCaptionLang == null || selectedCaptionLang == "off") disableCaptionsInternal() else applyCaptionSelectionInternal(selectedCaptionLang)
            return
        }
        val fallbackHeight = if (height == 0) null else height
        val src = buildSource(context, detail, fallbackHeight, selectedAudioTrackId, selectedCaptionLang) ?: return
        p.setMediaSource(src, pos)
        p.prepare()
        p.playWhenReady = wasPlaying
        if (selectedCaptionLang == null || selectedCaptionLang == "off") disableCaptionsInternal() else applyCaptionSelectionInternal(selectedCaptionLang)
    }

    fun qualityOptions(detail: UiVideoDetail): List<Int> {
        val adaptive = detail.adaptiveFormats.filter { !it.isAudio && it.url.isNotBlank() }
            .map { it.height }.distinct().sortedDescending()
        if (adaptive.isNotEmpty()) return adaptive
        return detail.formats.filter { it.url.isNotBlank() }.map { it.height }.filter { it > 0 }.distinct().sortedDescending()
    }

    fun currentQualityHeight(): Int? {
        val p = player as? ExoPlayer ?: return null
        return p.videoFormat?.height?.takeIf { it > 0 } ?: p.videoSize.height.takeIf { it > 0 }
    }

    // ── Audio track handling ────────────────────────────────────────────────
    data class AudioOption(
        val id: String,
        val displayName: String,
        val languageCode: String,
        val isDefault: Boolean,
        val isAutoDubbed: Boolean,
        val sampleFormat: UiStreamingFormat?
    )

    fun audioOptions(detail: UiVideoDetail): List<AudioOption> {
        val audioFormats = detail.adaptiveFormats.filter { it.isAudio && it.url.isNotBlank() }
        if (audioFormats.isEmpty()) return emptyList()
        // Group by track id/displayName
        val grouped = audioFormats.groupBy { fmt ->
            val at = fmt.audioTrack
            when {
                at?.id?.isNotBlank() == true -> at.id
                at?.displayName?.isNotBlank() == true -> at.displayName
                else -> "original"
            }
        }
        return grouped.map { (key, list) ->
            val best = list.maxByOrNull { it.bitrate } ?: list.first()
            val at = best.audioTrack
            AudioOption(
                id = key,
                displayName = at?.displayName?.ifBlank { if (key == "original") "Original" else key } ?: if (key == "original") "Original" else key,
                languageCode = at?.id?.substringBefore("-")?.lowercase() ?: "",
                isDefault = at?.isDefault ?: (key == "original"),
                isAutoDubbed = at?.isAutoDubbed ?: false,
                sampleFormat = best
            )
        }.sortedWith(compareBy<AudioOption> { !it.isDefault }.thenBy { it.isAutoDubbed }.thenBy { it.displayName.lowercase() })
    }

    fun currentAudioTrackId(): String? = selectedAudioTrackId

    fun selectAudioTrack(context: Context, detail: UiVideoDetail, trackId: String) {
        selectedAudioTrackId = trackId
        currentDetail = detail
        val p = player ?: return
        val pos = p.currentPosition
        val wasPlaying = p.isPlaying || p.playWhenReady
        val keepHeight = currentQualityHeight()
        // keep current video height to avoid quality hop + extra buffering
        val src = buildSource(context, detail, keepHeight, trackId, selectedCaptionLang) ?: return
        p.setMediaSource(src, pos)
        p.prepare()
        p.playWhenReady = wasPlaying
        if (wasPlaying) p.play()
        if (selectedCaptionLang == null || selectedCaptionLang == "off") disableCaptionsInternal() else applyCaptionSelectionInternal(selectedCaptionLang)
    }

    // ── Caption handling ───────────────────────────────────────────────────
    fun captionOptions(detail: UiVideoDetail): List<UiCaptionTrack> = detail.captionTracks

    fun selectedCaption(): String? = selectedCaptionLang

    fun selectCaption(languageCode: String?) {
        val wasPlaying = player?.isPlaying == true || player?.playWhenReady == true
        selectedCaptionLang = if (languageCode.isNullOrBlank()) "off" else languageCode
        if (languageCode.isNullOrBlank() || languageCode == "off") {
            disableCaptionsInternal()
        } else {
            applyCaptionSelectionInternal(languageCode)
        }
        // keep playback running after track switch
        try {
            if (wasPlaying) {
                player?.playWhenReady = true
                player?.play()
            }
        } catch (_: Exception) {}
    }

    private fun disableCaptionsInternal() {
        val ts = trackSelector ?: return
        val wasPlaying = player?.isPlaying == true || player?.playWhenReady == true
        try {
            ts.setParameters(ts.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).clearOverridesOfType(C.TRACK_TYPE_TEXT).setPreferredTextLanguage(null).build())
        } catch (_: Exception) {}
        try { if (wasPlaying) { player?.playWhenReady = true; player?.play() } } catch (_: Exception) {}
    }

    private fun applyCaptionSelectionInternal(languageCode: String?) {
        val ts = trackSelector ?: return
        val lang = languageCode?.takeIf { it.isNotBlank() && it != "off" } ?: return
        val wasPlaying = player?.isPlaying == true || player?.playWhenReady == true
        try {
            // First try exact override if track group is available
            val p = player
            val idx = currentDetail?.captionTracks?.indexOfFirst { it.languageCode.equals(lang, ignoreCase = true) } ?: -1
            // also try base language match if exact not found
            var effectiveIdx = idx
            if (effectiveIdx < 0) {
                val base = lang.substringBefore("-").substringBefore("_").lowercase()
                effectiveIdx = currentDetail?.captionTracks?.indexOfFirst { it.languageCode.substringBefore("-").substringBefore("_").lowercase() == base } ?: -1
            }
            var matched = false
            if (p != null && effectiveIdx >= 0) {
                val wantedId = subtitleId(effectiveIdx)
                val tracks = p.currentTracks
                for (group in tracks.groups) {
                    if (group.type != C.TRACK_TYPE_TEXT) continue
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        if (fmt.id == wantedId) {
                            ts.setParameters(ts.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT).setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i)).build())
                            matched = true
                            break
                        }
                    }
                    if (matched) break
                }
            }
            if (!matched) {
                // Fallback to preferred language (handles base language match)
                ts.setParameters(ts.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).clearOverridesOfType(C.TRACK_TYPE_TEXT).setPreferredTextLanguage(lang).build())
            }
        } catch (_: Exception) {
            try {
                ts.setParameters(ts.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setPreferredTextLanguage(lang).build())
            } catch (_: Exception) {}
        }
        try { if (wasPlaying) { player?.playWhenReady = true; player?.play() } } catch (_: Exception) {}
    }

    fun isCaptionEnabled(): Boolean {
        val ts = trackSelector ?: return false
        return try { !ts.parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) && selectedCaptionLang != null && selectedCaptionLang != "off" } catch (_: Exception) { false }
    }

    fun speedOptions(): List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    fun setSpeed(s: Float) { player?.setPlaybackSpeed(s) }
    @Deprecated("Use selectCaption")
    fun setCaptionEnabled(enabled: Boolean) {
        if (!enabled) selectCaption(null) else {
            val lang = currentDetail?.captionTracks?.firstOrNull()?.languageCode
            if (lang != null) selectCaption(lang)
        }
    }
}
