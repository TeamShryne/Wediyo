package com.teamshryne.wediyo.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.ui.components.HomeTopBar
import com.teamshryne.wediyo.ui.components.ShortsShelf
import com.teamshryne.wediyo.ui.components.VideoCard
import com.teamshryne.wediyo.util.bestThumbUrl

/**
 * YouTube-Android-style home: top bar + topic chips + flat video feed
 * with resume hero on top and Shorts shelf interleaved.
 *
 * Data comes from [HomeViewModel] (subs + related-queue branching),
 * never from a YouTube home/trending endpoint.
 */
@Composable
fun HomeScreen(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onShorts: () -> Unit = onSearch,
    onLibrary: () -> Unit = onSearch,
    onVideoClick: (String) -> Unit = {},
    onShortClick: (String) -> Unit = {},
    onChannelClick: (String) -> Unit = {},
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val settings = remember(ctx) { SettingsManager(ctx) }
    val thumbQuality by settings.thumbQuality.collectAsState(initial = "high")
    val avatarQuality by settings.avatarQuality.collectAsState(initial = "high")
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.refresh() }

    // True infinite scroll: when the last visible item gets close to the
    // end, pull the next deep-branched page. loadMore() itself guards
    // against concurrent / exhausted loads, so this is safe to fire often.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to info.totalItemsCount
        }.collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - 4) vm.loadMore()
        }
    }

    Scaffold(
        topBar = { HomeTopBar(onSearch = onSearch, onSettings = onSettings) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            HomeChipsBar(
                filter = state.filter,
                onFilter = vm::setFilter,
                onRefresh = vm::refresh,
                refreshing = state.isLoading || state.isRefreshing,
                offline = state.offline
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            val videos = state.videos
            val shorts = state.shorts

            if (state.isLoading && videos.isEmpty()) {
                // Skeletons — same shape as VideoCard so no layout jump.
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(5) { HomeSkeletonCard() }
                }
            } else if (videos.isEmpty() && state.error != null && !state.isRefreshing) {
                HomeEmptyState(
                    message = state.error ?: "",
                    onSearch = onSearch,
                    onLibrary = onLibrary,
                    onRetry = vm::retry
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
                ) {
                    // Resume hero — last session's unfinished video.
                    state.resume?.let { r ->
                        item(key = "resume") {
                            ResumeHeroCard(
                                resume = r,
                                onResume = { onVideoClick(r.videoId) },
                                onDismiss = vm::dismissResume,
                                onChannel = { if (r.channelId.isNotBlank()) onChannelClick(r.channelId) }
                            )
                        }
                    }
                    // Flat feed with Shorts shelf every ~6 videos (YouTube pattern).
                    if (videos.isEmpty()) {
                        item(key = "filter-empty") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when (state.filter) {
                                        HomeFilter.SUBS -> "No subscription videos yet — pull new uploads with refresh"
                                        HomeFilter.QUEUE -> "Queue is empty — watch something to grow it"
                                        HomeFilter.FRESH -> "Nothing fresh — everything here is already watched"
                                        else -> "No videos yet"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    videos.forEachIndexed { idx, v ->
                        item(key = "v-${v.id}-$idx") {
                            VideoCard(
                                video = v,
                                thumbQuality = thumbQuality,
                                avatarQuality = avatarQuality,
                                onClick = { onVideoClick(v.id) }
                            )
                        }
                        if (idx == 5 && shorts.isNotEmpty()) {
                            item(key = "shorts-shelf") {
                                Column {
                                    Spacer(Modifier.height(4.dp))
                                    ShortsShelf(
                                        shorts = shorts,
                                        thumbQuality = thumbQuality,
                                        onShortClick = onShortClick
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    HorizontalDivider(
                                        thickness = 0.5.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                    // Pagination footer — re-triggers on every new page while
                    // visible, so chained deep-branch pages stream in.
                    item(key = "footer") {
                        HomeFooter(
                            loadingMore = state.isLoadingMore,
                            canLoadMore = state.canLoadMore,
                            hasItems = videos.isNotEmpty(),
                            totalItems = videos.size,
                            onLoadMore = vm::loadMore
                        )
                    }
                }
            }
        }
    }
}

// ── Chips bar (local filter — no refetch) ────────────────────────

@Composable
private fun HomeChipsBar(
    filter: HomeFilter,
    onFilter: (HomeFilter) -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    offline: Boolean
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val chips = listOf(
            HomeFilter.ALL to "All",
            HomeFilter.SUBS to "Subscriptions",
            HomeFilter.QUEUE to "Queue",
            HomeFilter.FRESH to "Fresh"
        )
        items(chips.size) { i ->
            val (f, label) = chips[i]
            FilterChip(
                selected = filter == f,
                onClick = { if (filter != f) onFilter(f) },
                label = { Text(label) },
                shape = RoundedCornerShape(8.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.onBackground,
                    selectedLabelColor = MaterialTheme.colorScheme.background,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null
            )
        }
        item {
            if (offline) {
                AssistChip(
                    onClick = onRefresh,
                    label = { Text("Offline") },
                    shape = RoundedCornerShape(8.dp)
                )
            } else {
                IconButton(onClick = onRefresh, enabled = !refreshing, modifier = Modifier.size(36.dp)) {
                    if (refreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh feed")
                    }
                }
            }
        }
    }
}

// ── Resume hero ──────────────────────────────────────────────────

@Composable
private fun ResumeHeroCard(
    resume: ResumeCard,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    onChannel: () -> Unit
) {
    Card(
        onClick = onResume,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color(0xFF111111))
            ) {
                AsyncImage(
                    model = bestThumbUrl(resume.thumbsJson, resume.thumbUrl, "high"),
                    contentDescription = resume.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Progress bar (YouTube red, bottom edge).
                LinearProgressIndicator(
                    progress = { resume.progress },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFFFF0000),
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White)
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 10.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Resume • ${formatMs(resume.durationMs - resume.positionMs)} left",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        resume.title.ifBlank { "Continue watching" },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        resume.author.ifBlank { "Last session" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { onChannel() }
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = onResume,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Play")
                }
            }
        }
    }
}

// ── Footer / empty / skeleton ────────────────────────────────────

@Composable
private fun HomeFooter(
    loadingMore: Boolean,
    canLoadMore: Boolean,
    hasItems: Boolean,
    totalItems: Int,
    onLoadMore: () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            loadingMore -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
            canLoadMore && hasItems -> {
                // Keyed on page growth: while the footer stays visible each
                // new page re-fires the next deep-branch fetch (infinite).
                LaunchedEffect(totalItems, canLoadMore) { onLoadMore() }
                TextButton(onClick = onLoadMore) { Text("Load more") }
            }
            hasItems -> Text(
                "You're all caught up",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HomeEmptyState(
    message: String,
    onSearch: () -> Unit,
    onLibrary: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "What will you watch today?",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message.ifBlank { "Discover videos from your subscriptions and watch queue." },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSearch,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(28.dp)
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Search Wediyo")
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onRetry) { Text("Retry") }
            TextButton(onClick = onLibrary) { Text("Open library") }
        }
    }
}

@Composable
private fun HomeSkeletonCard() {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .padding(horizontal = 0.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        )
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:${sec.toString().padStart(2, '0')}"
    else "$m:${sec.toString().padStart(2, '0')}"
}
