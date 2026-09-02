package com.teamshryne.wediyo.player.factory

import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import com.teamshryne.wediyo.player.config.PlayerConfig

object LoadControlFactory {
    fun forVideo(): DefaultLoadControl = build(PlayerConfig.VIDEO)
    fun forShorts(): DefaultLoadControl = build(PlayerConfig.SHORTS)

    private fun build(p: PlayerConfig.BufferProfile): DefaultLoadControl {
        val resolvedMin = maxOf(p.minBufferMs, p.playbackMs, p.rebufferMs)
        val resolvedMax = maxOf(p.maxBufferMs, resolvedMin)
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(resolvedMin, resolvedMax, p.playbackMs, p.rebufferMs)
            .setBackBuffer(p.backBufferMs, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setAllocator(DefaultAllocator(true, PlayerConfig.ALLOCATOR_BUFFER_SIZE))
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
            .build()
    }
}
