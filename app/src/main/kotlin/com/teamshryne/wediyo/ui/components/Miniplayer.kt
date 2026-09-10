package com.teamshryne.wediyo.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.teamshryne.wediyo.data.model.UiVideoDetail
import com.teamshryne.wediyo.player.PlayerManager
import com.teamshryne.wediyo.util.bestThumbUrl
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * YouTube-style docked miniplayer: sits above the bottom nav, keeps the video's audio
 * (and session) alive while the user browses Home / Search / Library / etc.
 *
 * Behavior (matches youtube.com/watch miniplayer + 2024 mobile redesign, docked variant):
 * - tap bar → back to the full watch page
 * - play/pause in place, no reload
 * - X or sideways swipe → full stop (player queue cleared, service released)
 * - thin red progress bar, always live
 * - thumbnail via Coil memory cache (no refetch), 360p variant for speed
 *
 * Fast/reliable notes: this UI owns no player — it only observes the PlayerManager
 * singleton, so appearing/disappearing never re-buffers. All listeners are removed on dispose.
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
    var isPlaying by remember(detail.videoId) { mutableStateOf(PlayerManager.get().playerOrNull()?.isPlaying == true) }
    var isBuffering by remember(detail.videoId) { mutableStateOf(false) }
    var position by remember(detail.videoId) { mutableLongStateOf(0L) }
    var duration by remember(detail.videoId) { mutableLongStateOf(0L) }

    // Observe the shared player — attach/detach cleanly, never recreate it.
    DisposableEffect(detail.videoId) {
        val p = PlayerManager.get().playerOrNull()
        isPlaying = p?.isPlaying == true
        isBuffering = p?.playbackState == Player.STATE_BUFFERING
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) {
                isPlaying = v
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_ENDED) isPlaying = false
            }
        }
        p?.addListener(listener)
        onDispose { try { p?.removeListener(listener) } catch (_: Exception) {} }
    }

    // Live progress ticker (cheap: reads cached player fields, no allocation).
    LaunchedEffect(detail.videoId) {
        while (true) {
            delay(500)
            try {
                PlayerManager.get().playerOrNull()?.let { p ->
                    position = p.currentPosition.coerceAtLeast(0L)
                    duration = p.duration.coerceAtLeast(0L)
                }
            } catch (_: Exception) {
            }
        }
    }

    // Swipe-away-to-dismiss (YouTube gesture): drag sideways past threshold stops playback.
    var dragX by remember(detail.videoId) { mutableFloatStateOf(0f) }
    val dismissPx = with(density) { 160.dp.toPx() }
    val animatedX by animateFloatAsState(targetValue = dragX, label = "miniDrag")

    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val channel = detail.channelTitle.ifBlank { detail.author }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .offset { IntOffset(animatedX.roundToInt(), 0) }
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
            }
            .clickable(onClick = onExpand),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(start = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = bestThumbUrl(detail.thumbnailsJson, detail.thumbnailUrl, "360p"),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        detail.title.ifBlank { "Playing video" },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (channel.isNotBlank()) channel else "Wediyo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = {
                        try {
                            if (isPlaying) PlayerManager.get().pause() else PlayerManager.get().resume()
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }
                IconButton(
                    onClick = {
                        try {
                            PlayerManager.get().stopAndClear(ctx)
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close miniplayer",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            // Thin live progress (YouTube red), 2dp — always visible.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
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
