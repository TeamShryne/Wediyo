package com.teamshryne.wediyo.ui.screens.podcast

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.teamshryne.wediyo.data.model.toUiVideo
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.ui.components.VideoOverflowButton
import com.teamshryne.wediyo.util.bestThumbUrl
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen(playlistId: String, onBack: () -> Unit, vm: PodcastViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { SettingsManager(ctx) }
    var thumbQ by remember { mutableStateOf("high") }
    var avatarQ by remember { mutableStateOf("high") }
    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(Unit) { settings.avatarQuality.collectLatest { avatarQ = it } }
    LaunchedEffect(playlistId) { vm.load(playlistId) }

    val listState = rememberLazyListState()
    LaunchedEffect(listState.firstVisibleItemIndex, state.continuation) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 3 && state.continuation.isNotBlank() && !state.isPaginating) vm.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.header?.title ?: "Podcast", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } }
            )
        }
    ) { pad ->
        when {
            state.isLoading && state.detail == null -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            state.error != null && state.detail == null -> {
                Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.retry() }) { Text("Retry") }
                }
            }
            state.detail != null -> {
                val h = state.detail!!.header
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                            // Square hero like podcast
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.size(120.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0F0F0F))) {
                                    val thumb = bestThumbUrl(h?.thumbsJson ?: "[]", h?.thumbUrl ?: "", thumbQ)
                                    AsyncImage(model = thumb, contentDescription = h?.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(h?.title ?: "", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 3, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(6.dp))
                                    if (h?.channelName?.isNotBlank() == true) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val av = bestThumbUrl(h.channelAvatarsJson ?: "[]", h.channelAvatarUrl ?: "", avatarQ)
                                            if (av.isNotBlank()) AsyncImage(model = av, contentDescription = h.channelName, modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                            Spacer(Modifier.width(6.dp))
                                            Text(h.channelName, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    val meta = listOfNotNull(
                                        h?.episodeCountText?.takeIf { it.isNotBlank() },
                                        h?.updatedText?.takeIf { it.isNotBlank() }
                                    ).joinToString(" • ")
                                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val first = state.episodes.firstOrNull()
                                            if (first != null) try { ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${first.videoId}&list=${state.detail?.playlistId ?: playlistId}".toUri())) } catch (_: Exception) {}
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Play", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                            }
                            if (h?.description?.isNotBlank() == true) {
                                Text(h.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                            }
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    itemsIndexed(state.episodes) { idx, ep ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${ep.videoId}&list=${state.detail?.playlistId ?: playlistId}".toUri())) } catch (_: Exception) {} }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(Modifier.size(width = 150.dp, height = 84.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F0F0F))) {
                                val thumb = bestThumbUrl(ep.thumbsJson, ep.thumbUrl, thumbQ)
                                AsyncImage(model = thumb, contentDescription = ep.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                if (ep.durationText.isNotBlank()) {
                                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(ep.durationText, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ep.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Spacer(Modifier.height(4.dp))
                                val meta = buildString {
                                    if (ep.viewCountText.isNotBlank()) append(ep.viewCountText)
                                    if (ep.publishedText.isNotBlank()) {
                                        if (isNotEmpty()) append(" • ")
                                        append(ep.publishedText)
                                    }
                                    if (ep.durationText.isNotBlank()) {
                                        if (isNotEmpty()) append(" • ")
                                        append(ep.durationText)
                                    }
                                }
                                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (ep.channelName.isNotBlank()) Text(ep.channelName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            VideoOverflowButton(ep.toUiVideo())
                        }
                        if (idx < state.episodes.size - 1) HorizontalDivider(Modifier.padding(start = 48.dp, end = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    if (state.isPaginating) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } }
                    if (state.continuation.isBlank() && state.episodes.isNotEmpty() && !state.isPaginating) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("End • ${state.episodes.size} episodes" + (state.detail?.header?.episodeCountText?.let { " • $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                    if (state.episodes.isEmpty() && !state.isPaginating && !state.isLoading) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No episodes in this podcast", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
            }
        }
    }
}
