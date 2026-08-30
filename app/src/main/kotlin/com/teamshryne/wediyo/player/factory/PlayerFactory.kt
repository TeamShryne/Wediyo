package com.teamshryne.wediyo.player.factory

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.teamshryne.wediyo.player.config.PlayerConfig
import com.teamshryne.wediyo.player.datasource.YouTubeDataSource

@UnstableApi
object PlayerFactory {

    fun createBandwidthMeter(): DefaultBandwidthMeter =
        DefaultBandwidthMeter.Builder(AppContextHolder.context)
            .setInitialBitrateEstimate(PlayerConfig.INITIAL_BANDWIDTH_BPS)
            .setResetOnNetworkTypeChange(false)
            .build()

    fun createTrackSelector(context: Context, bandwidthMeter: DefaultBandwidthMeter): DefaultTrackSelector {
        val adaptiveFactory = AdaptiveTrackSelection.Factory()
        val selector = DefaultTrackSelector(context, adaptiveFactory)
        val params = selector.buildUponParameters()
            .setPreferredVideoMimeTypes("video/mp4", "video/webm", "video/avc", "video/av01", "video/vp9")
            .setAllowVideoMixedMimeTypeAdaptiveness(true)
            .setAllowMultipleAdaptiveSelections(true)
        selector.setParameters(params)
        return selector
    }

    fun createPlayer(
        context: Context,
        isShorts: Boolean = false
    ): Pair<ExoPlayer, DefaultTrackSelector> {
        val bw = createBandwidthMeter()
        val trackSelector = createTrackSelector(context, bw)
        val loadControl = if (isShorts) LoadControlFactory.forShorts() else LoadControlFactory.forVideo()
        val dsFactory = YouTubeDataSource.factory(context)
        val mediaSourceFactory = DefaultMediaSourceFactory(dsFactory)
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.playWhenReady = true
        return player to trackSelector
    }
}

// Simple holder to avoid passing context everywhere for bandwidth meter
object AppContextHolder {
    lateinit var context: Context
    fun init(c: Context) { context = c.applicationContext }
}
