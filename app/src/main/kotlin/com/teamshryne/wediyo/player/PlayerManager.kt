package com.teamshryne.wediyo.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
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
    }

    private var player: ExoPlayer? = null
    private var context: Context? = null
    private var isShortsMode: Boolean = false
    var lastVideoId: String? = null
        private set

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
        val (p, _) = PlayerFactory.createPlayer(context, isShorts)
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) { _isPlaying.value = v }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) _duration.value = p.duration.coerceAtLeast(0L)
            }
        })
        player = p
        return p
    }

    fun playDetail(context: Context, detail: UiVideoDetail, startMs: Long = 0, isShorts: Boolean = false, preferredHeight: Int? = null) {
        val p = ensure(context, isShorts)
        lastVideoId = detail.videoId
        val source = buildSource(context, detail, preferredHeight)
        if (source == null) return
        p.setMediaSource(source, startMs)
        p.prepare()
        p.playWhenReady = true
    }

    fun playDetailWithQuality(context: Context, detail: UiVideoDetail, quality: String, startMs: Long = 0, isShorts: Boolean = false) {
        val cleaned = quality.lowercase().removeSuffix("p").trim()
        val h = cleaned.toIntOrNull() // "auto" -> null
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

    fun release() {
        player?.release()
        player = null
    }

    fun pause() { player?.pause() }
    fun resume() { player?.play() }
    fun seekTo(ms: Long) { player?.seekTo(ms) }

    // Build MediaSource: respect preferredHeight for quality selection.
    // When user explicitly selects a height, force merging path even if DASH/HLS exists, so quality actually changes.
    private fun buildSource(context: Context, d: UiVideoDetail, preferredHeight: Int? = null): androidx.media3.exoplayer.source.MediaSource? {
        val hasQualityPreference = preferredHeight != null && preferredHeight > 0
        if (!hasQualityPreference) {
            if (d.dashManifestUrl.isNotBlank()) {
                val ds = YouTubeDataSource.factory(context)
                return DashMediaSource.Factory(ds).createMediaSource(MediaItem.fromUri(d.dashManifestUrl))
            }
            if (d.hlsManifestUrl.isNotBlank()) {
                val ds = YouTubeDataSource.factory(context)
                return HlsMediaSource.Factory(ds).createMediaSource(MediaItem.fromUri(d.hlsManifestUrl))
            }
        }
        // Separate adaptive video+audio (VISIONOS gives direct urls)
        val videoFormats = d.adaptiveFormats.filter { !it.isAudio && it.url.isNotBlank() }
            .sortedWith(compareByDescending<UiStreamingFormat> { it.height }.thenByDescending { it.bitrate })
        val audioFormats = d.adaptiveFormats.filter { it.isAudio && it.url.isNotBlank() }
            .sortedByDescending { it.bitrate }

        // If we have adaptive video+audio, merge preferred/best video + best audio
        if (videoFormats.isNotEmpty() && audioFormats.isNotEmpty()) {
            val bestVideo = if (preferredHeight != null && preferredHeight > 0) {
                videoFormats.filter { it.height == preferredHeight }.maxByOrNull { it.bitrate }
                    ?: videoFormats.minByOrNull { kotlin.math.abs(it.height - preferredHeight) }
                    ?: videoFormats.first()
            } else {
                videoFormats.first()
            }
            val bestAudio = audioFormats.first()
            return merging(context, bestVideo.url, bestAudio.url, pickMime(bestVideo.mimeType), pickMime(bestAudio.mimeType))
        }
        // Progressive muxed fallback (single file with audio)
        val prog = d.formats.firstOrNull { it.url.isNotBlank() } ?: videoFormats.firstOrNull()
        if (prog != null && prog.url.isNotBlank()) {
            val cacheDs = YouTubeDataSource.factory(context)
            return ProgressiveMediaSource.Factory(cacheDs).createMediaSource(MediaItem.fromUri(prog.url))
        }
        // Last resort: first adaptive video url alone
        if (videoFormats.isNotEmpty()) {
            val cacheDs = YouTubeDataSource.factory(context)
            return ProgressiveMediaSource.Factory(cacheDs).createMediaSource(MediaItem.fromUri(videoFormats.first().url))
        }
        return null
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

    // Quality switch: rebuild source with chosen height, keep position.
    // Works even when original source was DASH/HLS by falling back to merging adaptive formats.
    fun switchQuality(context: Context, detail: UiVideoDetail, height: Int) {
        val p = player ?: return
        val pos = p.currentPosition
        val wasPlaying = p.isPlaying
        val videoFormats = detail.adaptiveFormats.filter { !it.isAudio && it.url.isNotBlank() }
        val audioFormats = detail.adaptiveFormats.filter { it.isAudio && it.url.isNotBlank() }.sortedByDescending { it.bitrate }

        // If we have adaptive formats, use merging path (preferred for quality control)
        if (videoFormats.isNotEmpty() && audioFormats.isNotEmpty()) {
            val target = if (height == 0) {
                videoFormats.maxByOrNull { it.height } // auto = best
            } else {
                videoFormats.filter { it.height == height }.maxByOrNull { it.bitrate }
                    ?: videoFormats.minByOrNull { kotlin.math.abs(it.height - height) }
            } ?: return
            val audio = audioFormats.firstOrNull() ?: return
            val src = merging(context, target.url, audio.url, pickMime(target.mimeType), pickMime(audio.mimeType))
            p.setMediaSource(src, pos)
            p.prepare()
            p.playWhenReady = wasPlaying
            return
        }
        // Fallback: if only progressive formats or DASH/HLS with no adaptive list, rebuild via buildSource with preference
        val fallbackHeight = if (height == 0) null else height
        val src = buildSource(context, detail, fallbackHeight) ?: return
        p.setMediaSource(src, pos)
        p.prepare()
        p.playWhenReady = wasPlaying
    }

    fun qualityOptions(detail: UiVideoDetail): List<Int> {
        val adaptive = detail.adaptiveFormats.filter { !it.isAudio && it.url.isNotBlank() }
            .map { it.height }.distinct().sortedDescending()
        if (adaptive.isNotEmpty()) return adaptive
        // Fallback to progressive formats if adaptive empty (still offer what we have)
        return detail.formats.filter { it.url.isNotBlank() }.map { it.height }.filter { it > 0 }.distinct().sortedDescending()
    }

    fun currentQualityHeight(): Int? {
        val p = player as? ExoPlayer ?: return null
        return p.videoFormat?.height?.takeIf { it > 0 } ?: p.videoSize.height.takeIf { it > 0 }
    }

    fun speedOptions(): List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    fun setSpeed(s: Float) { player?.setPlaybackSpeed(s) }
    fun setCaptionEnabled(enabled: Boolean) { /* captions merged via HLS/DASH auto; stub for now */ }
}
