package com.teamshryne.wediyo.ui.components

import android.view.ViewGroup
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.teamshryne.wediyo.data.model.UiVideoDetail
import com.teamshryne.wediyo.player.PlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PlayerScrim = Color.Black
private val PlayerScrimAffordance = Color.Black.copy(alpha = 0.40f)
private val PlayerScrimContent = Color.White
private val PlayerLiveIndicator = Color(0xFFFF0000)

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
    var showControls by remember { mutableStateOf(false) }
    var currentPos by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var bufferedPct by remember { mutableStateOf(0f) }
    var isBuffering by remember { mutableStateOf(false) }
    var hasEnded by remember { mutableStateOf(false) }
    var displayRemaining by remember { mutableStateOf(false) }
    var isHolding2x by remember { mutableStateOf(false) }
    var normalSpeed by remember { mutableStateOf(1f) }
    var playbackSpeed by remember { mutableStateOf(1f) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var settingsMode by remember { mutableStateOf<String?>(null) } // null=main, quality, speed, captions, stats
    val haptic = LocalHapticFeedback.current
    var savedVideoQ by remember { mutableStateOf("auto") }
    LaunchedEffect(Unit) {
        try { com.teamshryne.wediyo.data.prefs.SettingsManager(ctx).videoQuality.collect { savedVideoQ = it } } catch (_: Exception) {}
    }
    // Auto-apply saved video quality on first load if not shorts
    val preferredHeightFromSettings = remember(savedVideoQ) { if (isShorts) null else savedVideoQ.lowercase().removeSuffix("p").trim().toIntOrNull() }

    DisposableEffect(detail.videoId, isShorts, preferredHeightFromSettings) {
        val p = PlayerManager.get().ensure(ctx, isShorts)
        player = p
        // Only reset source if video changed, else preserve position (fixes scroll/fullscreen restart)
        val sameId = PlayerManager.get().lastVideoId == detail.videoId
        val pos = if (sameId) p.currentPosition else 0L
        val prefH = preferredHeightFromSettings
        // If same id and already ready/buffering, just attach and resume (avoid restart); if quality pref differs, switch via switchQuality
        if (sameId && p.playbackState != Player.STATE_IDLE && p.playbackState != Player.STATE_ENDED) {
            if (p.duration > 0 || p.isPlaying) {
                p.playWhenReady = true
                // If saved quality differs from current, apply via switchQuality
                val curH = PlayerManager.get().currentQualityHeight()
                if (prefH != null && prefH != curH && detail.adaptiveFormats.isNotEmpty()) {
                    // defer switch slightly to avoid race with attach
                }
            } else {
                PlayerManager.get().playDetail(ctx, detail, pos, isShorts, prefH)
            }
        } else {
            PlayerManager.get().playDetail(ctx, detail, pos, isShorts, prefH)
        }
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
                // track speed
                playbackSpeed = player.playbackParameters.speed
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
                    currentPos = it.currentPosition
                }
            }
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying && !isHolding2x) {
            delay(3200)
            showControls = false
        }
    }

    Box(
        modifier
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    (player as? androidx.media3.exoplayer.ExoPlayer)?.let { this.player = it }
                }
            },
            update = { view -> view.player = player as? androidx.media3.exoplayer.ExoPlayer },
            modifier = Modifier.fillMaxSize().pointerInput(player, duration, isHolding2x) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val w = size.width
                        if (offset.x < w * 0.35f) {
                            val np = (currentPos - 10_000).coerceAtLeast(0)
                            PlayerManager.get().seekTo(np); currentPos = np
                        } else if (offset.x > w * 0.65f) {
                            val np = (currentPos + 10_000).coerceAtMost(duration)
                            PlayerManager.get().seekTo(np); currentPos = np
                        } else {
                            if (isPlaying) PlayerManager.get().pause() else PlayerManager.get().resume()
                        }
                        showControls = true
                    },
                    onLongPress = {
                        isHolding2x = true
                        normalSpeed = playbackSpeed
                        PlayerManager.get().setSpeed(2f)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showControls = true
                    },
                    onPress = {
                        try { awaitRelease() } finally {
                            if (isHolding2x) {
                                isHolding2x = false
                                PlayerManager.get().setSpeed(normalSpeed)
                            }
                        }
                    }
                )
            }
        )

        if (detail.isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (isFullscreen) 16.dp else 10.dp)
                    .statusBarsPadding()
                    .padding(top = if (showControls) 48.dp else 0.dp)
            ) {
                Surface(shape = RoundedCornerShape(4.dp), color = PlayerLiveIndicator) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                        Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp))
                    }
                }
            }
        }

        // Hold 2x indicator top
        AnimatedVisibility(visible = isHolding2x, enter = fadeIn() + slideInVertically { -it/2 }, exit = fadeOut() + slideOutVertically { -it/2 }, modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp).statusBarsPadding()) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.72f)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("2>>", color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        // Top row: title left -> settings gear right (when controls visible)
        AnimatedVisibility(visible = showControls, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null && isFullscreen) {
                        Box(Modifier.size(36.dp).clip(CircleShape).clickable { onBack() }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        detail.title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, shadow = Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 8f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.14f)).clickable { showSettingsSheet = true; settingsMode = null }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Settings, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // Center pause
        AnimatedVisibility(visible = showControls, enter = fadeIn() + scaleIn(initialScale = 0.92f), exit = fadeOut() + scaleOut(), modifier = Modifier.align(Alignment.Center)) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(PlayerScrimAffordance).clickable {
                    if (hasEnded) { PlayerManager.get().seekTo(0); PlayerManager.get().resume() }
                    else if (isPlaying) PlayerManager.get().pause() else PlayerManager.get().resume()
                },
                contentAlignment = Alignment.Center
            ) {
                if (isBuffering) CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
                else Icon(
                    when { hasEnded -> Icons.Filled.Replay; isPlaying -> Icons.Filled.Pause; else -> Icons.Filled.PlayArrow },
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White, modifier = Modifier.size(42.dp)
                )
            }
        }

        // Time + fullscreen controls - only when tapped
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .padding(bottom = 4.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (displayRemaining && duration > 0) "-${formatMs(duration - currentPos)}" else "${formatMs(currentPos)} / ${formatMs(duration)}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 4f)),
                        modifier = Modifier.clickable { displayRemaining = !displayRemaining }
                    )
                    IconButton(onClick = {
                        if (onFullscreenToggle != null) onFullscreenToggle() else {
                            val act = ctx as? android.app.Activity ?: return@IconButton
                            val next = !isFullscreen
                            act.requestedOrientation = if (next) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }
                    }, modifier = Modifier.size(36.dp)) {
                        Icon(if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        // Seekbar at exact bottom of video - red bar is main bar, always visible, circle only when controls visible
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .height(if (showControls) 14.dp else 3.dp)
                .padding(horizontal = 0.dp)
        ) {
            // track background
            Box(Modifier.fillMaxWidth().height(if (showControls) 4.dp else 3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.28f)).align(Alignment.Center)) {
                Box(Modifier.fillMaxWidth(bufferedPct.coerceIn(0f,1f)).fillMaxHeight().background(Color.White.copy(alpha = 0.55f)))
                val prog = if (duration > 0) (currentPos.toFloat()/duration).coerceIn(0f,1f) else 0f
                Box(Modifier.fillMaxWidth(prog).fillMaxHeight().background(Color.Red))
            }
            // slider
            val prog = if (duration > 0) (currentPos.toFloat()/duration).coerceIn(0f,1f) else 0f
            var dragPos by remember(prog) { mutableStateOf(prog) }
            var dragging by remember { mutableStateOf(false) }
            val disp = if (dragging) dragPos else prog
            Slider(
                value = disp,
                onValueChange = { dragPos = it; dragging = true },
                onValueChangeFinished = {
                    val ms = (dragPos * duration).toLong()
                    PlayerManager.get().seekTo(ms); currentPos = ms; dragging = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = if (showControls) Color.White else Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent
                ),
                thumb = {
                    if (showControls) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(Color.White))
                    }
                },
                modifier = Modifier.fillMaxWidth().align(Alignment.Center)
            )
        }

        // Settings sheets - portrait bottom sheets, landscape side panels 30% width
        Box(Modifier.fillMaxSize()) {
            val isLandscapeSheet = isFullscreen
            if (isLandscapeSheet) {
                // Fullscreen: sheets come from right 30% width, video adjusted to 70%
                if (showSettingsSheet && settingsMode == null) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)).clickable { showSettingsSheet = false }) {}
                    Surface(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.30f),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                        tonalElevation = 4.dp
                    ) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                                IconButton(onClick = { showSettingsSheet = false }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, null, modifier = Modifier.size(18.dp)) }
                            }
                            SettingsRow(icon = Icons.Filled.Hd, title = "Quality", subtitle = "${PlayerManager.get().qualityOptions(detail).size} options", onClick = { settingsMode = "quality" })
                            SettingsRow(icon = Icons.Filled.Subtitles, title = "Captions", subtitle = if (detail.captionTracks.isEmpty()) "No captions" else "${detail.captionTracks.size} languages", onClick = { settingsMode = "captions" })
                            SettingsRow(icon = Icons.Filled.Speed, title = "Playback speed", subtitle = if (playbackSpeed==1f) "Normal" else "${playbackSpeed}x", onClick = { settingsMode = "speed" })
                            SettingsRow(icon = Icons.Filled.QueryStats, title = "Stats for nerds", subtitle = "View playback stats", onClick = { settingsMode = "stats" })
                        }
                    }
                }
                if (settingsMode == "quality") {
                    val opts = PlayerManager.get().qualityOptions(detail)
                    val selectedH = savedVideoQ.lowercase().removeSuffix("p").trim().toIntOrNull() ?: 0
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)).clickable { settingsMode = null }) {}
                    Surface(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.30f), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { settingsMode = null }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ArrowBack, null, modifier = Modifier.size(18.dp)) }; Text("Quality", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f)); IconButton(onClick = { settingsMode = null; showSettingsSheet = false }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, null, modifier = Modifier.size(18.dp)) } }
                            Text("Select quality — will be used for all videos. Auto picks best.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val all = listOf(0) + opts
                            all.forEach { h ->
                                val label = if (h==0) "Auto (recommended)" else "${h}p"
                                val sub = if (h==0) "Adapts to network" else "${h}p • ${if (h >= 1080) "High" else if (h >= 720) "HD" else "SD"}"
                                val isSel = h == selectedH
                                Surface(shape = RoundedCornerShape(14.dp), color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth().clickable {
                                    val qStr = if (h==0) "auto" else "${h}p"
                                    PlayerManager.get().switchQuality(ctx, detail, h)
                                    // persist without needing IO scope here: use rememberCoroutineScope in composable; fallback fire-and-forget
                                    // launched via global scope to avoid composable scope capture inside clickable
                                    try { kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) { com.teamshryne.wediyo.data.prefs.SettingsManager(ctx).setVideoQuality(qStr) } } catch (_: Exception) {}
                                    settingsMode = null; showSettingsSheet = false
                                }) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Surface(shape = CircleShape, color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(32.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Hd, null, modifier = Modifier.size(16.dp), tint = if (isSel) Color.White else MaterialTheme.colorScheme.onPrimaryContainer) } }
                                        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface); Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        if (isSel) Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
                if (settingsMode == "speed") {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)).clickable { settingsMode = null }) {}
                    Surface(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.30f), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { settingsMode = null }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ArrowBack, null) }; Text("Playback speed", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f)); IconButton(onClick = { settingsMode = null; showSettingsSheet = false }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, null) } }
                            PlayerManager.get().speedOptions().forEach { s ->
                                val label = when(s){0.25f->"0.25x";0.5f->"0.5x";0.75f->"0.75x";1f->"Normal"; else->"${s}x"}; val sel = s==playbackSpeed
                                Surface(shape = RoundedCornerShape(14.dp), color = if(sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), modifier = Modifier.fillMaxWidth().clickable { PlayerManager.get().setSpeed(s); playbackSpeed=s; settingsMode=null; showSettingsSheet=false }) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(if(sel) Icons.Filled.CheckCircle else Icons.Filled.PlayCircle, null, modifier = Modifier.size(18.dp), tint = if(sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if(sel) FontWeight.Bold else FontWeight.Medium), color = if(sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                        Spacer(Modifier.weight(1f))
                                        if(sel) Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                if (settingsMode == "captions") {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)).clickable { settingsMode = null }) {}
                    Surface(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.30f), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { settingsMode = null }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ArrowBack, null) }; Text("Captions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f)); IconButton(onClick = { settingsMode = null; showSettingsSheet = false }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, null) } }
                            if (detail.captionTracks.isEmpty()) {
                                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) { Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Text("No captions available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            } else {
                                detail.captionTracks.forEach { ct ->
                                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth().clickable { settingsMode=null; showSettingsSheet=false }) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(32.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(ct.languageCode.take(2).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) } }
                                            Column(Modifier.weight(1f)) { Text(ct.name.ifBlank{ct.languageCode}, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)); Text(ct.kind.ifBlank{"standard"}, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (settingsMode == "stats") {
                    val p = player as? androidx.media3.exoplayer.ExoPlayer; val vs = p?.videoSize; val vf = p?.videoFormat
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)).clickable { settingsMode = null }) {}
                    Surface(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.30f), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { settingsMode = null }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ArrowBack, null) }; Text("Stats for nerds", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f)); IconButton(onClick = { settingsMode = null; showSettingsSheet = false }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, null) } }
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatLine("Video ID", detail.videoId)
                                    StatLine("Resolution", if(vs!=null && vs.width>0) "${vs.width}×${vs.height}" else detail.adaptiveFormats.firstOrNull { !it.isAudio }?.let{"${it.width}×${it.height}"} ?: "—")
                                    StatLine("FPS", vf?.frameRate?.takeIf{it>0}?.let{"%.0f".format(it)} ?: detail.adaptiveFormats.firstOrNull{it.fps>0}?.fps?.toString() ?: "—")
                                    StatLine("Bitrate", vf?.bitrate?.takeIf{it>0}?.let{"${it/1000} kbps"} ?: "—")
                                    StatLine("Codec", vf?.sampleMimeType ?: detail.adaptiveFormats.firstOrNull{!it.isAudio}?.mimeType?.substringBefore(";") ?: "—")
                                    StatLine("Buffered", "${(bufferedPct*100).toInt()}%  •  ${formatMs(currentPos)} / ${formatMs(duration)}")
                                    StatLine("Speed", "${playbackSpeed}x")
                                    StatLine("State", when(p?.playbackState){ Player.STATE_BUFFERING->"Buffering"; Player.STATE_READY-> if(isPlaying) "Playing" else "Paused"; Player.STATE_ENDED->"Ended"; else->"Idle"})
                                    StatLine("View", detail.viewCountText.ifBlank{"—"})
                                    StatLine("Duration", detail.durationText.ifBlank{ formatMs(duration) })
                                }
                            }
                        }
                    }
                }
            } else {
                if (showSettingsSheet && settingsMode == null) {
                    ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                        Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Settings", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            SettingsRow(icon = Icons.Filled.Hd, title = "Quality", subtitle = "${PlayerManager.get().qualityOptions(detail).size} options", onClick = { settingsMode = "quality" })
                            SettingsRow(icon = Icons.Filled.Subtitles, title = "Captions", subtitle = if (detail.captionTracks.isEmpty()) "No captions" else "${detail.captionTracks.size} languages", onClick = { settingsMode = "captions" })
                            SettingsRow(icon = Icons.Filled.Speed, title = "Playback speed", subtitle = if (playbackSpeed==1f) "Normal" else "${playbackSpeed}x", onClick = { settingsMode = "speed" })
                            SettingsRow(icon = Icons.Filled.QueryStats, title = "Stats for nerds", subtitle = "View playback stats", onClick = { settingsMode = "stats" })
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                if (settingsMode == "quality") {
                    val opts = PlayerManager.get().qualityOptions(detail)
                    val selectedH = savedVideoQ.lowercase().removeSuffix("p").trim().toIntOrNull() ?: 0
                    ModalBottomSheet(onDismissRequest = { settingsMode = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                        Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { settingsMode = null }) { Icon(Icons.Filled.ArrowBack, null) }
                                Text("Quality", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                                IconButton(onClick = { settingsMode = null; showSettingsSheet = false }) { Icon(Icons.Filled.Close, null) }
                            }
                            Text("Select quality — persisted for all videos. Auto picks best.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val all = listOf(0) + opts
                            all.forEach { h ->
                                val label = if (h==0) "Auto (recommended)" else "${h}p"
                                val sub = if (h==0) "Adapts to network" else "${h}p • ${if (h >= 1080) "High" else if (h >= 720) "HD" else "SD"}"
                                val isSel = h == selectedH
                                Surface(shape = RoundedCornerShape(14.dp), color = if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth().clickable {
                                    val qStr = if (h==0) "auto" else "${h}p"
                                    PlayerManager.get().switchQuality(ctx, detail, h)
                                    try { kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) { com.teamshryne.wediyo.data.prefs.SettingsManager(ctx).setVideoQuality(qStr) } } catch (_: Exception) {}
                                    settingsMode = null; showSettingsSheet = false
                                }) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Surface(shape = CircleShape, color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Hd, null, modifier = Modifier.size(18.dp), tint = if (isSel) Color.White else MaterialTheme.colorScheme.onPrimaryContainer) } }
                                        Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface); Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        if (isSel) Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                if (settingsMode == "speed") {
                    ModalBottomSheet(onDismissRequest = { settingsMode = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                        Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { settingsMode = null }) { Icon(Icons.Filled.ArrowBack, null) }
                                Text("Playback speed", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                                IconButton(onClick = { settingsMode = null; showSettingsSheet = false }) { Icon(Icons.Filled.Close, null) }
                            }
                            PlayerManager.get().speedOptions().forEach { s ->
                                val label = when(s){0.25f->"0.25x";0.5f->"0.5x";0.75f->"0.75x";1f->"Normal"; else->"${s}x"}
                                val sel = s==playbackSpeed
                                Surface(shape = RoundedCornerShape(14.dp), color = if(sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), modifier = Modifier.fillMaxWidth().clickable {
                                    PlayerManager.get().setSpeed(s); playbackSpeed=s; settingsMode=null; showSettingsSheet=false
                                }) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(if(sel) Icons.Filled.CheckCircle else Icons.Filled.PlayCircle, null, modifier = Modifier.size(20.dp), tint = if(sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if(sel) FontWeight.Bold else FontWeight.Medium), color = if(sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                                        Spacer(Modifier.weight(1f))
                                        if(sel) Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                if (settingsMode == "captions") {
                    ModalBottomSheet(onDismissRequest = { settingsMode = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                        Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { settingsMode = null }) { Icon(Icons.Filled.ArrowBack, null) }
                                Text("Captions", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                                IconButton(onClick = { settingsMode = null; showSettingsSheet = false }) { Icon(Icons.Filled.Close, null) }
                            }
                            if (detail.captionTracks.isEmpty()) {
                                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Text("No captions available", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            } else {
                                detail.captionTracks.forEach { ct ->
                                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth().clickable { settingsMode=null; showSettingsSheet=false }) {
                                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(36.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(ct.languageCode.take(2).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) } }
                                            Column(Modifier.weight(1f)) { Text(ct.name.ifBlank{ct.languageCode}, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)); Text(ct.kind.ifBlank{"standard"}, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
                if (settingsMode == "stats") {
                    val p = player as? androidx.media3.exoplayer.ExoPlayer
                    val vs = p?.videoSize
                    val vf = p?.videoFormat
                    ModalBottomSheet(onDismissRequest = { settingsMode = null }, containerColor = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)) {
                        Column(Modifier.padding(20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { settingsMode = null }) { Icon(Icons.Filled.ArrowBack, null) }
                                Text("Stats for nerds", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                                IconButton(onClick = { settingsMode = null; showSettingsSheet = false }) { Icon(Icons.Filled.Close, null) }
                            }
                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    StatLine("Video ID", detail.videoId)
                                    StatLine("Resolution", if(vs!=null && vs.width>0) "${vs.width}×${vs.height}" else detail.adaptiveFormats.firstOrNull { !it.isAudio }?.let{"${it.width}×${it.height}"} ?: "—")
                                    StatLine("FPS", vf?.frameRate?.takeIf{it>0}?.let{"%.0f".format(it)} ?: detail.adaptiveFormats.firstOrNull{it.fps>0}?.fps?.toString() ?: "—")
                                    StatLine("Bitrate", vf?.bitrate?.takeIf{it>0}?.let{"${it/1000} kbps"} ?: "—")
                                    StatLine("Codec", vf?.sampleMimeType ?: detail.adaptiveFormats.firstOrNull{!it.isAudio}?.mimeType?.substringBefore(";") ?: "—")
                                    StatLine("Buffered", "${(bufferedPct*100).toInt()}%  •  ${formatMs(currentPos)} / ${formatMs(duration)}")
                                    StatLine("Speed", "${playbackSpeed}x")
                                    StatLine("State", when(p?.playbackState){ Player.STATE_BUFFERING->"Buffering"; Player.STATE_READY-> if(isPlaying) "Playing" else "Paused"; Player.STATE_ENDED->"Ended"; else->"Idle"})
                                    StatLine("View", detail.viewCountText.ifBlank{"—"})
                                    StatLine("Duration", detail.durationText.ifBlank{ formatMs(duration) })
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(36.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) } }
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)); Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Icon(Icons.Filled.ChevronRight, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 12.dp))
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return if (m >= 60) String.format("%d:%02d:%02d", m / 60, m % 60, sec) else String.format("%d:%02d", m, sec)
}
