package com.teamshryne.wediyo.ui.components

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.teamshryne.wediyo.data.model.UiVideoDetail
import com.teamshryne.wediyo.player.PlayerManager
import kotlinx.coroutines.delay

// ── Flow scrim palette ───────────────────────────────────────────────────────
private val PlayerScrim = Color.Black
private val PlayerScrimAffordance = Color.Black.copy(alpha = 0.40f)
private val PlayerScrimContent = Color.White
private val PlayerScrimContentDisabled = Color.White.copy(alpha = 0.30f)
private val PlayerLiveIndicator = Color(0xFFFF0000)
private const val TOP_GRADIENT_ALPHA = 0.52f
private const val BOTTOM_GRADIENT_ALPHA = 0.78f

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WediyoPlayer(
    detail: UiVideoDetail,
    isShorts: Boolean = false,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    isFullscreen: Boolean = false,
    onFullscreenToggle: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember { mutableStateOf<Player?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPos by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var bufferedPct by remember { mutableStateOf(0f) }
    var isBuffering by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var isLiveChatAvailable by remember { mutableStateOf(false) }
    var hasEnded by remember { mutableStateOf(false) }

    // Fullscreen: toggle orientation + system bars
    fun setFullscreen(full: Boolean) {
        val activity = ctx as? Activity ?: return
        activity.requestedOrientation = if (full) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (full) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(detail.videoId, isShorts) {
        val p = PlayerManager.get().ensure(ctx, isShorts)
        player = p
        PlayerManager.get().playDetail(ctx, detail, 0, isShorts)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) { isPlaying = v; hasEnded = false }
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    duration = p.duration.coerceAtLeast(0L)
                    bufferedPct = if (p.duration > 0) p.bufferedPosition.toFloat() / p.duration else 0f
                }
                if (state == Player.STATE_ENDED) { hasEnded = true; isPlaying = false }
            }
            override fun onEvents(player: Player, events: Player.Events) {
                duration = player.duration.coerceAtLeast(0L)
                if (player.duration > 0) bufferedPct = (player.bufferedPosition.toFloat() / player.duration).coerceIn(0f, 1f)
            }
        }
        p.addListener(listener)
        onDispose { p.removeListener(listener) }
    }

    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_PAUSE -> player?.pause()
                Lifecycle.Event.ON_RESUME -> if (autoPlay) player?.play()
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(250)
            player?.let {
                if (it.isPlaying) {
                    currentPos = it.currentPosition
                    duration = it.duration.coerceAtLeast(0L)
                    if (it.duration > 0) bufferedPct = (it.bufferedPosition.toFloat() / it.duration).coerceIn(0f, 1f)
                } else if (!isBuffering) {
                    // still update while paused for scrub preview
                    currentPos = it.currentPosition
                }
            }
        }
    }

    LaunchedEffect(showControls, isPlaying, isLocked) {
        if (showControls && isPlaying && !isLocked) {
            delay(3500)
            showControls = false
        }
    }

    // ambient bg: blurred thumb behind player (Flow's VideoAmbientBackground simplified)
    Box(
        modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val w = size.width
                        if (offset.x < w * 0.35f) {
                            PlayerManager.get().seekTo((currentPos - 10_000).coerceAtLeast(0))
                        } else if (offset.x > w * 0.65f) {
                            PlayerManager.get().seekTo((currentPos + 10_000).coerceAtMost(duration))
                        } else {
                            if (isPlaying) PlayerManager.get().pause() else PlayerManager.get().resume()
                        }
                        showControls = true
                    }
                )
            }
    ) {
        // Underlying ExoPlayer surface
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    (player as? androidx.media3.exoplayer.ExoPlayer)?.let { this.player = it }
                }
            },
            update = { view -> view.player = player as? androidx.media3.exoplayer.ExoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        // LIVE badge (top-left) when isLive
        if (detail.isLive && !isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (isFullscreen) 16.dp else 10.dp)
                    .statusBarsPadding()
                    .padding(top = if (showControls) 56.dp else 0.dp)
            ) {
                Surface(shape = RoundedCornerShape(4.dp), color = PlayerLiveIndicator) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                        Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp))
                    }
                }
            }
        }

        // ========== LOCKED MODE: minimal overlay ==========
        if (isLocked) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showControls = !showControls }
            )
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                Surface(shape = CircleShape, color = PlayerScrimAffordance, modifier = Modifier.clickable { isLocked = false }) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LockOpen, contentDescription = "Unlock", tint = PlayerScrimContent, modifier = Modifier.size(28.dp)) }
                }
            }
            if (showControls) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = PlayerScrimAffordance,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).clickable { isLocked = false }
                ) { Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Rounded.Lock, null, tint = PlayerScrimContent, modifier = Modifier.size(18.dp)); Text("Tap to unlock", color = PlayerScrimContent, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)) } }
            }
            // thin seekbar still visible when locked
            FlowThinSeekbar(progress = if (duration > 0) currentPos.toFloat() / duration else 0f, buffered = bufferedPct, modifier = Modifier.align(Alignment.BottomCenter))
        } else {
            // ========== NORMAL CONTROLS (Flow-style) ==========

            // Top gradient
            AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(PlayerScrim.copy(alpha = TOP_GRADIENT_ALPHA), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    FlowPlayerTopBar(
                        title = detail.title,
                        isFullscreen = isFullscreen,
                        speedLabel = if (playbackSpeed == 1f) "1x" else "${playbackSpeed}x",
                        isPlaying = isPlaying,
                        onBack = onBack,
                        onSpeedClick = { showSpeedSheet = true },
                        onQualityClick = { showQualitySheet = true },
                        onMoreClick = { showMoreSheet = true },
                        onFullscreenClick = {
                            if (onFullscreenToggle != null) onFullscreenToggle() else {
                                val next = !isFullscreen
                                setFullscreen(next)
                            }
                        },
                        onLockClick = { isLocked = true; showControls = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Center transport
            AnimatedVisibility(visible = showControls, enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.Center)) {
                FlowTransportControls(
                    isPlaying = isPlaying,
                    hasEnded = hasEnded,
                    isBuffering = isBuffering,
                    onPrev10 = { PlayerManager.get().seekTo((currentPos - 10_000).coerceAtLeast(0)); currentPos = (currentPos - 10_000).coerceAtLeast(0) },
                    onNext10 = { PlayerManager.get().seekTo((currentPos + 10_000).coerceAtMost(duration)); currentPos = (currentPos + 10_000).coerceAtMost(duration) },
                    onPlayPause = { if (isPlaying) PlayerManager.get().pause() else PlayerManager.get().resume() },
                    onReplay = { PlayerManager.get().seekTo(0); PlayerManager.get().resume() }
                )
            }

            // Double-tap hint icons (faint)
            if (showControls) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.08f), modifier = Modifier.size(44.dp).clickable { PlayerManager.get().seekTo((currentPos - 10_000).coerceAtLeast(0)) }) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Replay10, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp)) } }
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.08f), modifier = Modifier.size(44.dp).clickable { PlayerManager.get().seekTo((currentPos + 10_000).coerceAtMost(duration)) }) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Forward10, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(22.dp)) } }
                }
            }

            // Bottom gradient + seek + time + bottom bar
            AnimatedVisibility(visible = showControls, enter = slideInVertically { it / 2 } + fadeIn(), exit = slideOutVertically { it / 2 } + fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, PlayerScrim.copy(alpha = BOTTOM_GRADIENT_ALPHA))))
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Seekbar row (Flow SeekbarWithPreview simplified to Slider + buffered + chapter dots)
                    FlowSeekbarRow(
                        position = currentPos,
                        duration = duration,
                        bufferedPct = bufferedPct,
                        onSeek = { pct ->
                            val ms = (pct * duration).toLong()
                            PlayerManager.get().seekTo(ms); currentPos = ms
                        }
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(formatMs(currentPos), color = PlayerScrimContent, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp))
                        Text(formatMs(duration), color = PlayerScrimContent.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp))
                    }
                    Spacer(Modifier.height(4.dp))
                    // Bottom bar: left controls, right fullscreen/more
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { showQualitySheet = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Settings, contentDescription = "Quality", tint = PlayerScrimContent, modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { showSpeedSheet = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.SlowMotionVideo, contentDescription = "Speed", tint = PlayerScrimContent, modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { isLocked = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Lock, contentDescription = "Lock", tint = PlayerScrimContent, modifier = Modifier.size(18.dp)) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (detail.captionTracks.isNotEmpty()) {
                                IconButton(onClick = {}, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.ClosedCaption, null, tint = PlayerScrimContent, modifier = Modifier.size(20.dp)) }
                            }
                            IconButton(
                                onClick = {
                                    if (onFullscreenToggle != null) onFullscreenToggle() else setFullscreen(!isFullscreen)
                                },
                                modifier = Modifier.size(36.dp)
                            ) { Icon(if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, contentDescription = "Fullscreen", tint = PlayerScrimContent, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }

            // Always-visible thin seekbar when controls hidden (Flow edge-aligned seekbar)
            AnimatedVisibility(visible = !showControls && duration > 0 && !isLocked, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                FlowThinSeekbar(progress = if (duration > 0) currentPos.toFloat() / duration else 0f, buffered = bufferedPct, modifier = Modifier.fillMaxWidth())
            }
        }

        // ── Sheets ────────────────────────────────────────────────────────────
        if (showQualitySheet) {
            val opts = PlayerManager.get().qualityOptions(detail)
            ModalBottomSheet(onDismissRequest = { showQualitySheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quality", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Text("Select playback quality. Auto picks best based on bandwidth.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    val all = listOf(0) + opts
                    all.forEach { h ->
                        val label = if (h == 0) "Auto (recommended)" else "${h}p"
                        val sub = if (h == 0) "Adapts to network" else "${h}p • ${if (h >= 1080) "High" else if (h >= 720) "HD" else "SD"}"
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth().clickable {
                                PlayerManager.get().switchQuality(ctx, detail, h)
                                showQualitySheet = false
                            }
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.HighQuality, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
                                Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)); Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        if (showSpeedSheet) {
            ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Playback speed", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    PlayerManager.get().speedOptions().forEach { s ->
                        val label = when (s) { 0.25f -> "0.25x"; 0.5f -> "0.5x"; 0.75f -> "0.75x"; 1f -> "Normal"; else -> "${s}x" }
                        val selected = s == playbackSpeed
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            modifier = Modifier.fillMaxWidth().clickable {
                                PlayerManager.get().setSpeed(s); playbackSpeed = s; showSpeedSheet = false
                            }
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.PlayCircle, null, modifier = Modifier.size(20.dp), tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium), color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.weight(1f))
                                if (selected) Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        if (showMoreSheet) {
            ModalBottomSheet(onDismissRequest = { showMoreSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("More options", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    FlowMoreOption(icon = Icons.Rounded.ContentCopy, title = "Copy link", subtitle = detail.canonicalUrl.ifBlank { "https://www.youtube.com/watch?v=${detail.videoId}" }, onClick = { showMoreSheet = false })
                    FlowMoreOption(icon = Icons.Rounded.Download, title = "Download", subtitle = "${detail.formats.size} progressive • ${detail.adaptiveFormats.size} adaptive", onClick = { showMoreSheet = false })
                    FlowMoreOption(icon = Icons.Rounded.ClosedCaption, title = "Captions", subtitle = if (detail.captionTracks.isEmpty()) "No captions" else "${detail.captionTracks.size} languages", onClick = { showMoreSheet = false })
                    FlowMoreOption(icon = Icons.Rounded.Info, title = "Stats for nerds", subtitle = "View: ${detail.viewCountText} • ${detail.durationText}", onClick = { showMoreSheet = false })
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun FlowPlayerTopBar(
    title: String,
    isFullscreen: Boolean,
    speedLabel: String,
    isPlaying: Boolean,
    onBack: (() -> Unit)?,
    onSpeedClick: () -> Unit,
    onQualityClick: () -> Unit,
    onMoreClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onLockClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onBack != null) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize", tint = PlayerScrimContent, modifier = Modifier.size(24.dp)) }
            } else {
                Box(Modifier.size(40.dp).clip(CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayCircle, null, tint = PlayerScrimContent.copy(alpha = 0.0f), modifier = Modifier.size(24.dp)) }
            }
            if (isFullscreen && title.isNotBlank()) {
                Text(title, color = PlayerScrimContent, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(end = 8.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(color = PlayerScrimAffordance, shape = RoundedCornerShape(14.dp), modifier = Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).clickable { onSpeedClick() }) {
                Box(Modifier.padding(horizontal = 10.dp), contentAlignment = Alignment.Center) { Text(speedLabel, color = PlayerScrimContent, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp)) }
            }
            TopBarIconButton(icon = Icons.Rounded.Settings, desc = "Settings", onClick = onQualityClick)
            TopBarIconButton(icon = Icons.Rounded.MoreVert, desc = "More", onClick = onMoreClick)
            TopBarIconButton(icon = Icons.Rounded.Lock, desc = "Lock", onClick = onLockClick)
            TopBarIconButton(icon = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, desc = "Fullscreen", onClick = onFullscreenClick)
        }
    }
}

@Composable
private fun TopBarIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = desc, tint = PlayerScrimContent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FlowTransportControls(
    isPlaying: Boolean,
    hasEnded: Boolean,
    isBuffering: Boolean,
    onPrev10: () -> Unit,
    onNext10: () -> Unit,
    onPlayPause: () -> Unit,
    onReplay: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(36.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(PlayerScrimAffordance).clickable { onPrev10() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Replay10, contentDescription = "Back 10s", tint = PlayerScrimContent, modifier = Modifier.size(26.dp))
        }
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(PlayerScrimAffordance).clickable {
                if (hasEnded) onReplay() else onPlayPause()
            },
            contentAlignment = Alignment.Center
        ) {
            if (isBuffering) CircularProgressIndicator(color = PlayerScrimContent, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
            else Icon(
                when { hasEnded -> Icons.Rounded.Replay; isPlaying -> Icons.Rounded.Pause; else -> Icons.Rounded.PlayArrow },
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = PlayerScrimContent, modifier = Modifier.size(42.dp)
            )
        }
        Box(Modifier.size(48.dp).clip(CircleShape).background(PlayerScrimAffordance).clickable { onNext10() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10s", tint = PlayerScrimContent, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun FlowSeekbarRow(position: Long, duration: Long, bufferedPct: Float, onSeek: (Float) -> Unit) {
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    var sliderPos by remember(progress) { mutableStateOf(progress) }
    var isDragging by remember { mutableStateOf(false) }
    val display = if (isDragging) sliderPos else progress
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
            // buffered bg
            Box(
                Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(PlayerScrimContent.copy(alpha = 0.28f))
            ) {
                Box(Modifier.fillMaxWidth(bufferedPct).fillMaxHeight().background(PlayerScrimContent.copy(alpha = 0.55f)))
                Box(Modifier.fillMaxWidth(display).fillMaxHeight().background(MaterialTheme.colorScheme.primary))
            }
            Slider(
                value = display,
                onValueChange = { sliderPos = it; isDragging = true },
                onValueChangeFinished = { onSeek(sliderPos); isDragging = false },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun FlowThinSeekbar(progress: Float, buffered: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(3.dp).background(Color.Transparent)) {
        Box(Modifier.fillMaxWidth().height(3.dp).background(PlayerScrimContent.copy(alpha = 0.25f)))
        Box(Modifier.fillMaxWidth(buffered.coerceIn(0f,1f)).height(3.dp).background(PlayerScrimContent.copy(alpha = 0.55f)))
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f,1f)).height(3.dp).background(Color.Red))
    }
}

@Composable
private fun FlowMoreOption(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)); Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Icon(Icons.Rounded.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return if (m >= 60) String.format("%d:%02d:%02d", m / 60, m % 60, sec) else String.format("%d:%02d", m, sec)
}
