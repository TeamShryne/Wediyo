package com.teamshryne.wediyo.ui.screens.playlist

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.util.bestThumbUrl
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(playlistId: String, onBack: () -> Unit, vm: PlaylistViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { SettingsManager(ctx) }
    var thumbQ by remember { mutableStateOf("high") }
    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(playlistId) { vm.load(playlistId) }

    val listState = rememberLazyListState()
    LaunchedEffect(listState.firstVisibleItemIndex, state.continuation) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 3 && state.continuation.isNotBlank()) {
            vm.loadMore()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.header?.header?.title ?: "Playlist", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { pad ->
        when {
            state.isLoading && state.header == null -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.error != null && state.header == null -> {
                Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.retry() }) { Text("Retry") }
                }
            }
            state.header != null -> {
                val header = state.header!!.header
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                            // Thumbnail with gradient overlay and video count
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF0F0F0F))) {
                                val thumb = bestThumbUrl(header?.thumbsJson ?: "[]", header?.thumbUrl ?: "", thumbQ)
                                AsyncImage(model = thumb, contentDescription = header?.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                // Video count badge at bottom
                                if (header?.videoCountText?.isNotBlank() == true) {
                                    Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                                        Text(header.videoCountText, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                // Play all scrim
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                    FilledButton(
                                        onClick = {
                                            val first = state.videos.firstOrNull { !it.isUnavailable }
                                            if (first != null) {
                                                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${first.videoId}&list=${state.header?.header?.let { it.title } ?: playlistId}".toUri())) } catch (_: Exception) {}
                                            }
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.filledButtonColors(containerColor = Color.White, contentColor = Color.Black)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play all", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                            // Info below thumbnail
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(header?.title ?: "", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                // Channel row
                                if (header?.channelName?.isNotBlank() == true) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                                        val cid = header.channelId
                                        if (cid.isNotBlank()) {
                                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/channel/$cid".toUri())) } catch (_: Exception) {}
                                        }
                                    }) {
                                        val av = bestThumbUrl(header.channelAvatarsJson ?: "[]", header.channelAvatarUrl ?: "", "high")
                                        if (av.isNotBlank()) {
                                            AsyncImage(model = av, contentDescription = header.channelName, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Text(header.channelName, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                                        if (header.channelHandle.isNotBlank()) {
                                            Text(" ${header.channelHandle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                // Stats row
                                val stats = listOfNotNull(
                                    header?.videoCountText?.takeIf { it.isNotBlank() },
                                    header?.viewCountText?.takeIf { it.isNotBlank() },
                                    header?.lastUpdatedText?.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")
                                if (stats.isNotBlank()) {
                                    Text(stats, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                // Has unavailable banner
                                if (header?.hasUnavailable == true) {
                                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), shape = RoundedCornerShape(8.dp)) {
                                        Text("Unavailable videos are shown • ${state.videos.count { it.isUnavailable }} hidden", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                                // Action row
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    FilledTonalButton(onClick = {
                                        val first = state.videos.firstOrNull { !it.isUnavailable }
                                        if (first != null) {
                                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${first.videoId}&list=$playlistId".toUri())) } catch (_: Exception) {}
                                        }
                                    }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play all")
                                    }
                                    OutlinedButton(onClick = {
                                        val shuffled = state.videos.filter { !it.isUnavailable }.shuffled().firstOrNull()
                                        if (shuffled != null) {
                                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${shuffled.videoId}&list=$playlistId".toUri())) } catch (_: Exception) {}
                                        }
                                    }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Shuffle")
                                    }
                                }
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    // Video list
                    itemsIndexed(state.videos) { idx, v ->
                        val isUnav = v.isUnavailable
                        Row(
                            Modifier.fillMaxWidth()
                                .alpha(if (isUnav) 0.55f else 1f)
                                .clickable(enabled = !isUnav) {
                                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${v.videoId}&list=$playlistId".toUri())) } catch (_: Exception) {}
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Index
                            Box(Modifier.width(28.dp).padding(top = 28.dp), contentAlignment = Alignment.TopCenter) {
                                Text("${idx + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // Thumbnail
                            Box(Modifier.size(width = 150.dp, height = 84.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F0F0F))) {
                                val thumb = bestThumbUrl(v.thumbsJson, v.thumbUrl, thumbQ)
                                AsyncImage(model = thumb, contentDescription = v.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                if (isUnav) {
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                                        Text("Unavailable", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    }
                                } else if (v.durationText.isNotBlank()) {
                                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                        Text(v.durationText, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (isUnav) v.unavailableReason.ifBlank { "Unavailable video" } else v.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, color = if (isUnav) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                )
                                Spacer(Modifier.height(4.dp))
                                val meta = if (isUnav) {
                                    v.unavailableReason.ifBlank { "This video is unavailable" }
                                } else {
                                    listOfNotNull(v.channelName.takeIf { it.isNotBlank() }, v.viewCountText.takeIf { it.isNotBlank() }, v.publishedText.takeIf { it.isNotBlank() }).joinToString(" • ")
                                }
                                if (meta.isNotBlank()) {
                                    Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                if (isUnav) {
                                    Spacer(Modifier.height(4.dp))
                                    AssistChip(onClick = {}, label = { Text("Unavailable", style = MaterialTheme.typography.labelSmall) }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.errorContainer, labelColor = MaterialTheme.colorScheme.onErrorContainer))
                                }
                            }
                        }
                        if (idx < state.videos.size - 1) {
                            HorizontalDivider(Modifier.padding(start = 40.dp, end = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                    if (state.isPaginating) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                    if (state.continuation.isBlank() && state.videos.isNotEmpty() && !state.isPaginating) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                Text("End of playlist • ${state.videos.size} videos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    state.error?.let { e ->
                        item {
                            Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp))
                            }
                        }
                    }
                    if (state.videos.isEmpty() && !state.isPaginating && !state.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No videos in this playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun FilledButton(onClick: () -> Unit, shape: RoundedCornerShape, colors: ButtonColors, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, shape = shape, colors = colors, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), content = content)
}
