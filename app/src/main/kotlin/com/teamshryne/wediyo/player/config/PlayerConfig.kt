package com.teamshryne.wediyo.player.config

object PlayerConfig {
    const val TAG = "WediyoPlayer"
    data class BufferProfile(
        val minBufferMs: Int,
        val maxBufferMs: Int,
        val playbackMs: Int,
        val rebufferMs: Int,
        val backBufferMs: Int = 0
    )

    // Tuned for fast start and stall resistance: small playback threshold, moderate min, capped max to avoid heap blow-up
    val VIDEO = BufferProfile(
        minBufferMs = 15000,
        maxBufferMs = 30000,
        playbackMs = 500,
        rebufferMs = 1000,
        backBufferMs = 5000
    )
    val SHORTS = BufferProfile(
        minBufferMs = 1500,
        maxBufferMs = 8000,
        playbackMs = 250,
        rebufferMs = 750,
        backBufferMs = 2000
    )
    const val INITIAL_BANDWIDTH_BPS: Long = 5_000_000L
    const val ALLOCATOR_BUFFER_SIZE: Int = 16 * 1024 // 16KB chunks for smoother progressive
}
