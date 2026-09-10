package com.teamshryne.wediyo.ui.components

import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.teamshryne.wediyo.data.model.UiVideoDetail
import com.teamshryne.wediyo.player.PlayerManager
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Flow-style floating miniplayer: the LIVE video itself in a small rounded card —
 * same shared ExoPlayer surface the full watch page uses (surface handoff is automatic:
 * this card is only composed when the watch page is gone, and vice versa).
 *
 * Body (mirrors Flow's mini):
 * - floating 16:9 card, 12dp corners, shadow — video keeps playing visibly inside
 * - play/pause top-start, close X top-end (white on black scrim circles)
 * - replay-10 / forward-10 bottom-center row, 2dp red progress hairline at the bottom
 * - tap video → expand to full watch page (instant via VideoViewModel reuse gate)
 * - sideways fling past threshold → full stop (queue cleared, service released)
 *
 * Fast/reliable: owns no player and fetches nothing — observes the PlayerManager
 * singleton, thumbnail-free, listeners removed on dispose.
 */
@OptIn(UnstableApi::class)
@Composable
fun Miniplayer(
    detail: UiVideoDetail,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    var player by remember(detail.videoId) { mutableStateOf(PlayerManager.get().playerOrNull()) }
    var isPlaying by remember(detail.videoId) { mutableStateOf(player?.isPlaying == true) }
    var isBuffering by remember(detail.videoId) { mutableStateOf(player?.playbackState == Player.STATE_BUFFERING) }
    var hasEnded by remember(detail.videoId) { mutableStateOf(player?.playbackState == Player.STATE_ENDED) }
    var position by remember(detail.videoId) { mutableLongStateOf(0L) }
    var duration by remember(detail.videoId) { mutableLongStateOf(0L) }

    DisposableEffect(detail.videoId) {
        val p = PlayerManager.get().playerOrNull()
        player = p
        isPlaying = p?.isPlaying == true
        isBuffering = p?.playbackState == Player.STATE_BUFFERING
        hasEnded = p?.playbackState == Player.STATE_ENDED
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) {
                isPlaying = v
                if (v) hasEnded = false
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) duration = p?.duration?.coerceAtLeast(0L) ?: 0L
                if (state == Player.STATE_ENDED) {
                    hasEnded = true
                    isPlaying = false
                }
            }
        }
        p?.addListener(listener)
        onDispose { try { p?.removeListener(listener) } catch (_: Exception) {} }
    }

    LaunchedEffect(detail.videoId) {
        while (true) {
            delay(500)
            try {
                PlayerManager.get().playerOrNull()?.let { p ->
                    position = p.currentPosition.coerceAtLeast(0L)
                    val d = p.duration.coerceAtLeast(0L)
                    if (d > 0) duration = d
                }
            } catch (_: Exception) {
            }
        }
    }

    // Fling-away-to-dismiss (Flow gesture): fast sideways fling stops playback.
    var dragX by remember(detail.videoId) { mutableFloatStateOf(0f) }
    val dismissPx = with(density) { 160.dp.toPx() }
    val animatedX by animateFloatAsState(targetValue = dragX, label = "miniDrag")
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = modifier
            .width(208.dp)
            .aspectRatio(16f / 9f)
            .offset { IntOffset(animatedX.roundToInt(), 0) }
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .pointerInput(detail.videoId) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (kotlin.math.abs(dragX) > dismissPx) {
                            try {
                                PlayerManager.get().stopAndClear(ctx)
                            } catch (_: Exception) {
                            }
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                    onHorizontalDrag = { _, delta -> dragX += delta }
                )
            },
        color = Color.Black
    ) {
        Box(Modifier.fillMaxSize()) {
            // Live video — same shared player the watch page uses.
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        (player as? ExoPlayer)?.let { this.player = it }
                    }
                },
                update = { view ->
                    view.player = PlayerManager.get().playerOrNull() as? ExoPlayer
                },
                onRelease = { view -> view.player = null },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(onExpand) {
                        detectTapGestures(onTap = { onExpand() })
                    }
            )

            if (isBuffering) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White
                    )
                }
            }

            // Top-start: play/pause (replay when ended). Top-end: close.
            IconButton(
                onClick = {
                    try {
                        val pm = PlayerManager.get()
                        when {
                            hasEnded -> {
                                pm.seekTo(0)
                                pm.resume()
                            }
                            isPlaying -> pm.pause()
                            else -> pm.resume()
                        }
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(40.dp)
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        when {
                            hasEnded -> Icons.Filled.Replay
                            isPlaying -> Icons.Filled.Pause
                            else -> Icons.Filled.PlayArrow
                        },
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            IconButton(
                onClick = {
                    try {
                        PlayerManager.get().stopAndClear(ctx)
                    } catch (_: Exception) {
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(40.dp)
            ) {
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.30f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close miniplayer",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            // Bottom-center: -10s / +10s seek pair.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        try {
                            PlayerManager.get().playerOrNull()?.let { p ->
                                PlayerManager.get().seekTo((p.currentPosition - 10_000).coerceAtLeast(0))
                            }
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(Color.Black.copy(alpha = 0.36f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Replay10, contentDescription = "Back 10 seconds", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                IconButton(
                    onClick = {
                        try {
                            PlayerManager.get().playerOrNull()?.let { p ->
                                val dur = p.duration.coerceAtLeast(0L)
                                PlayerManager.get().seekTo((p.currentPosition + 10_000).coerceAtMost(dur))
                            }
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(Color.Black.copy(alpha = 0.36f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }

            // Red progress hairline.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.Transparent)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(2.dp)
                        .background(Color.Red)
                )
            }
        }
    }
}
