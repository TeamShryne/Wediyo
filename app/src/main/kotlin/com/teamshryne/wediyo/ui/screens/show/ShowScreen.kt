package com.teamshryne.wediyo.ui.screens.show

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
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.util.bestThumbUrl
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShowScreen(playlistId: String, onBack: () -> Unit, vm: ShowViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { SettingsManager(ctx) }
    var thumbQ by remember { mutableStateOf("high") }
    var expandedDesc by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(playlistId) { vm.load(playlistId) }

    val listState = rememberLazyListState()
    LaunchedEffect(listState.firstVisibleItemIndex, state.continuation) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 3 && state.continuation.isNotBlank() && !state.isSeasonSwitching) vm.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.detail?.header?.title ?: "Show", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                            // Hero thumbnail 16:9 — 4 qualities, overlay for S1:E1 + Play episode (all metadata used)
                            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF0F0F0F))) {
                                val thumb = bestThumbUrl(h?.thumbsJson ?: "[]", h?.thumbUrl ?: "", thumbQ)
                                AsyncImage(model = thumb, contentDescription = h?.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                // Season badge top-left
                                if (h?.seasonText?.isNotBlank() == true) {
                                    Box(Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                                        Text(h.seasonText, color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                                // Episode badge top-end (S1:E1)
                                if (h?.subtitle?.isNotBlank() == true) {
                                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 3.dp)) {
                                        Text(h.subtitle, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                                // Center Play episode — uses overlayTitle + first episode
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
                                    val first = state.episodes.firstOrNull()
                                    FilledButton(
                                        onClick = {
                                            if (first != null) try { ctx.startActivity(Intent(Intent.ACTION_VIEW, "https://www.youtube.com/watch?v=${first.videoId}&list=${state.detail?.playlistId ?: playlistId}".toUri())) } catch (_: Exception) {}
                                            else if (h?.overlayTitle?.isNotBlank() == true) { /* fallback: open show */ }
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.filledButtonColors(containerColor = Color.White, contentColor = Color.Black)
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Play episode", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }
                                }
                                // Overlay title/subtitle bottom scrim
                                if (h?.overlayTitle?.isNotBlank() == true) {
                                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(10.dp)) {
                                        Column {
                                            Text(h.overlayTitle, color = Color.White, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            if (h.overlaySubtitle.isNotBlank()) Text(h.overlaySubtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(h?.title ?: "", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                // All show metadata: description (needed), seasonText • episodeCount, currentSeason
                                if (h?.description?.isNotBlank() == true) {
                                    Text(
                                        h.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (expandedDesc) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.clickable { expandedDesc = !expandedDesc }
                                    )
                                    if (h.description.length > 120) Text(if (expandedDesc) "Show less" else "Show more", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { expandedDesc = !expandedDesc })
                                }
                                val meta = listOfNotNull(
                                    h?.seasonText?.takeIf { it.isNotBlank() },
                                    h?.episodeCountText?.takeIf { it.isNotBlank() },
                                    h?.currentSeason?.takeIf { it.isNotBlank() }?.let { "Current: $it" }
                                ).joinToString(" • ")
                                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                // Seasons — collection sortFilterSubMenuRenderer (needed, even if 1 season)
                                if (h?.seasons?.isNotEmpty() == true) {
                                    Text("Seasons", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        h.seasons.forEach { season ->
                                            val selected = season.selected && season.params == state.currentParams || (state.currentParams.isBlank() && season.selected)
                                            FilterChip(
                                                selected = selected,
                                                onClick = { if (!selected && season.params.isNotBlank()) vm.switchSeason(season.params) },
                                                label = { Text(season.title) },
                                                enabled = !state.isSeasonSwitching
                                            )
                                        }
                                    }
                                    if (state.isSeasonSwitching) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Text("Loading ${h.seasons.find { it.params != state.currentParams }?.title ?: "season"}...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
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
                            // Index + S label
                            Column(Modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(ep.indexText.ifBlank { "${idx + 1}" }, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("E${ep.indexText.ifBlank { "${idx + 1}" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(Modifier.size(width = 150.dp, height = 84.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F0F0F))) {
                                val thumb = bestThumbUrl(ep.thumbsJson, ep.thumbUrl, thumbQ)
                                AsyncImage(model = thumb, contentDescription = ep.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                if (ep.durationText.isNotBlank()) {
                                    Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(ep.durationText, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                                }
                                if (ep.isUnplayable) {
                                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) { Text("Unavailable", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ep.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                                Spacer(Modifier.height(4.dp))
                                val metaEp = buildString {
                                    append("Episode ${ep.indexText.ifBlank { "${idx + 1}" }}")
                                    if (ep.durationText.isNotBlank()) append(" • ${ep.durationText}")
                                    // durationSecs via parseDuration retained as needed for player
                                }
                                Text(metaEp, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (idx < state.episodes.size - 1) HorizontalDivider(Modifier.padding(start = 48.dp, end = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                    if (state.isPaginating) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } }
                    if (state.continuation.isBlank() && state.episodes.isNotEmpty() && !state.isPaginating && !state.isSeasonSwitching) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("End • ${state.episodes.size} episodes" + (state.detail?.header?.episodeCountText?.let { " • $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                    if (state.episodes.isEmpty() && !state.isPaginating && !state.isLoading && !state.isSeasonSwitching) item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No episodes in this show", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                }
            }
        }
    }
}

@Composable
private fun FilledButton(onClick: () -> Unit, shape: RoundedCornerShape, colors: ButtonColors, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, shape = shape, colors = colors, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), content = content)
}
