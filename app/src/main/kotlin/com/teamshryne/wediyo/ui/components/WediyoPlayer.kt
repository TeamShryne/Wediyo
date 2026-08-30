package com.teamshryne.wediyo.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WediyoPlayer(
    detail: UiVideoDetail,
    isShorts: Boolean = false,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    onFullscreenToggle: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var player by remember { mutableStateOf<Player?>(null) }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var currentPos by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    DisposableEffect(detail.videoId, isShorts) {
        val p = PlayerManager.get().ensure(ctx, isShorts)
        player = p
        PlayerManager.get().playDetail(ctx, detail, 0, isShorts)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(v: Boolean) { isPlaying = v }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = p.duration.coerceAtLeast(0L)
            }
        }
        p.addListener(listener)
        onDispose {
            p.removeListener(listener)
            // don't release here for gapless; VideoScreen handles release on dispose if needed
        }
    }

    // Lifecycle pause/resume
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

    // Position ticker
    LaunchedEffect(player) {
        while (true) {
            delay(500)
            player?.let {
                if (it.isPlaying) {
                    currentPos = it.currentPosition
                    duration = it.duration.coerceAtLeast(0L)
                }
            }
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }

    Box(modifier.background(Color.Black).clickable { showControls = !showControls }) {
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

        // Center play/pause when controls visible and paused
        if (showControls && !isPlaying) {
            Surface(
                modifier = Modifier.align(Alignment.Center).size(64.dp).clickable {
                    if (isPlaying) PlayerManager.get().pause() else PlayerManager.get().resume()
                },
                shape = CircleShape, color = Color.Black.copy(alpha = 0.52f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.PlayArrow,
                        contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Top bar
        if (showControls) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent)))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // title truncated
                Text(
                    detail.title, color = Color.White, style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                IconButton(onClick = { showQualitySheet = true }) { Icon(Icons.Filled.Info, contentDescription = "Quality", tint = Color.White) }
                IconButton(onClick = { showSpeedSheet = true }) { Icon(Icons.Filled.PlayArrow, contentDescription = "Speed", tint = Color.White) }
                if (onFullscreenToggle != null) IconButton(onClick = onFullscreenToggle) { Icon(Icons.Filled.Info, contentDescription = "Fullscreen", tint = Color.White) }
            }
        }

        // Bottom controls — seek + transport
        if (showControls) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .navigationBarsPadding()
            ) {
                // Seek bar
                val prog = if (duration > 0) currentPos.toFloat() / duration else 0f
                Slider(
                    value = prog.coerceIn(0f, 1f),
                    onValueChange = { v ->
                        val ms = (v * duration).toLong()
                        PlayerManager.get().seekTo(ms)
                        currentPos = ms
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White, activeTrackColor = Color(0xFFFF0000), inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.fillMaxWidth().height(20.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatMs(currentPos), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Text(formatMs(duration), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { PlayerManager.get().seekTo((currentPos - 10000).coerceAtLeast(0)) }) { Icon(Icons.Filled.Refresh, contentDescription = "Back 10", tint = Color.White) }
                    Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(48.dp).clickable {
                        if (isPlaying) PlayerManager.get().pause() else PlayerManager.get().resume()
                    }) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
                        }
                    }
                    IconButton(onClick = { PlayerManager.get().seekTo((currentPos + 10000).coerceAtMost(duration)) }) { Icon(Icons.Filled.Refresh, contentDescription = "Fwd 10", tint = Color.White) }
                }
            }
        }

        if (showQualitySheet) {
            val opts = PlayerManager.get().qualityOptions(detail)
            ModalBottomSheet(onDismissRequest = { showQualitySheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Quality", style = MaterialTheme.typography.titleMedium)
                    // Auto + heights
                    val all = listOf(0) + opts // 0 = Auto (best)
                    all.forEach { h ->
                        val label = if (h == 0) "Auto" else "${h}p"
                        val selected = false // could track current
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable {
                                PlayerManager.get().switchQuality(ctx, detail, h)
                                showQualitySheet = false
                            }
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.weight(1f))
                                if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }

        if (showSpeedSheet) {
            ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Playback speed", style = MaterialTheme.typography.titleMedium)
                    PlayerManager.get().speedOptions().forEach { s ->
                        val label = if (s == 1f) "Normal" else "${s}x"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable {
                                PlayerManager.get().setSpeed(s)
                                showSpeedSheet = false
                            }
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
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
