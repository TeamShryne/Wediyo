package com.teamshryne.wediyo.ui.screens.shorts

import android.view.ViewGroup
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.teamshryne.wediyo.data.model.UiVideoDetail
import com.teamshryne.wediyo.player.PlayerManager
import com.teamshryne.wediyo.util.bestThumbUrl
import kotlinx.coroutines.delay

@UnstableApi
@Composable
fun ShortsPlayer(
    shortThumbJson: String,
    shortThumbUrl: String,
    shortTitle: String,
    channelName: String,
    detail: UiVideoDetail?,
    isCurrent: Boolean,
    thumbQ: String,
    preferredQuality: String, // "auto" or "720"
    modifier: Modifier = Modifier,
    onVideoEnded: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var bufferedPct by remember { mutableStateOf(0f) }
    var hasEverReady by remember { mutableStateOf(false) }

    val preferredHeight = remember(preferredQuality) { preferredQuality.toIntOrNull() }

    // Attach player and start playback when isCurrent + detail
    DisposableEffect(detail?.videoId, isCurrent, preferredHeight) {
        if (!isCurrent || detail == null || (detail.formats.isEmpty() && detail.adaptiveFormats.isEmpty())) {
            onDispose { }
            return@DisposableEffect onDispose { }
        }
        val p = PlayerManager.get().ensure(ctx, isShorts = true)
        player = p
        // play with preferred quality
        PlayerManager.get().playDetail(ctx, detail, 0, isShorts = true, preferredHeight = preferredHeight)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) { isPlaying = v }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    isReady = true
                    hasEverReady = true
                    duration = p.duration.coerceAtLeast(0L)
                    bufferedPct = if (p.duration > 0) p.bufferedPosition.toFloat() / p.duration else 0f
                }
                if (state == Player.STATE_ENDED) {
                    isPlaying = false
                    onVideoEnded?.invoke()
                }
                if (state == Player.STATE_IDLE) {
                    isReady = false
                }
            }
            override fun onEvents(player: Player, events: Player.Events) {
                duration = player.duration.coerceAtLeast(0L)
                if (player.duration > 0) {
                    bufferedPct = (player.bufferedPosition.toFloat() / player.duration).coerceIn(0f, 1f)
                }
            }
        }
        p.addListener(listener)
        // init snapshot
        isPlaying = p.isPlaying
        isBuffering = p.playbackState == Player.STATE_BUFFERING
        isReady = p.playbackState == Player.STATE_READY
        duration = p.duration.coerceAtLeast(0L)
        onDispose { p.removeListener(listener) }
    }

    // lifecycle pause/resume only for current page
    DisposableEffect(lifecycle, isCurrent) {
        val obs = LifecycleEventObserver { _, e ->
            if (!isCurrent) return@LifecycleEventObserver
            when (e) {
                Lifecycle.Event.ON_PAUSE -> player?.pause()
                Lifecycle.Event.ON_RESUME -> player?.play()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    // poll position
    LaunchedEffect(player, isCurrent) {
        while (true) {
            delay(200)
            player?.let { p ->
                if (isCurrent) {
                    currentPos = p.currentPosition
                    val d = p.duration.coerceAtLeast(0L)
                    if (d > 0) duration = d
                    if (d > 0) bufferedPct = (p.bufferedPosition.toFloat() / d).coerceIn(0f, 1f)
                    isPlaying = p.isPlaying
                    isBuffering = p.playbackState == Player.STATE_BUFFERING
                }
            }
        }
    }

    val showLoading = isCurrent && (detail == null || !hasEverReady || isBuffering)

    Box(modifier.background(Color.Black)) {
        // Exo surface
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    (player as? ExoPlayer)?.let { this.player = it }
                }
            },
            update = { view ->
                if (isCurrent) view.player = player as? ExoPlayer else view.player = null
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading overlay: thumbnail 50% + glowing channel name left↔right loop
        if (showLoading) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = bestThumbUrl(shortThumbJson, shortThumbUrl, thumbQ),
                    contentDescription = shortTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.5f
                )
                // subtle dark for readability
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.10f)))
                // glowing channel name center – left→right → right→left loop
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlowingChannelName(name = channelName.ifBlank { shortTitle.take(24) })
                }
            }
        }

        // Red seekbar at bottom – draggable, always visible when duration>0 and current
        if (isCurrent && duration > 0) {
            ShortsSeekBar(
                position = currentPos,
                duration = duration,
                bufferedPct = bufferedPct,
                onSeek = { pct ->
                    val ms = (pct * duration).toLong().coerceIn(0L, duration)
                    PlayerManager.get().seekTo(ms)
                    currentPos = ms
                },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(bottom = 2.dp)
            )
        }
    }
}

@Composable
private fun GlowingChannelName(name: String) {
    // fast left↔right shimmer loop: translate shimmer across text
    val infinite = rememberInfiniteTransition(label = "shortsShimmer")
    // 0→1→0 ping-pong
    val shimmerProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer"
    )

    // brush that sweeps
    // We simulate glow by animating brush offset
    val brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.45f),
            Color.White,
            Color.White.copy(alpha = 0.45f)
        ),
        start = Offset(-200f + shimmerProgress * 600f, 0f),
        end = Offset(200f + shimmerProgress * 600f, 0f),
        tileMode = TileMode.Clamp
    )

    Text(
        text = name,
        color = Color.White,
        style = MaterialTheme.typography.titleMedium.copy(
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            letterSpacing = 0.2.sp,
            shadow = Shadow(Color.Black.copy(alpha = 0.85f), blurRadius = 10f),
            brush = brush
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
    )
}

@Composable
private fun ShortsSeekBar(
    position: Long,
    duration: Long,
    bufferedPct: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    var sliderPos by remember(progress) { mutableStateOf(progress) }
    var isDragging by remember { mutableStateOf(false) }
    val display = if (isDragging) sliderPos else progress

    Box(modifier = modifier.height(18.dp), contentAlignment = Alignment.Center) {
        // track bg
        Box(
            Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.28f))
        ) {
            Box(Modifier.fillMaxWidth(bufferedPct.coerceIn(0f, 1f)).fillMaxHeight().background(Color.White.copy(alpha = 0.55f)))
            Box(Modifier.fillMaxWidth(display).fillMaxHeight().background(Color.Red))
        }
        Slider(
            value = display,
            onValueChange = {
                sliderPos = it
                isDragging = true
            },
            onValueChangeFinished = {
                onSeek(sliderPos)
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}
