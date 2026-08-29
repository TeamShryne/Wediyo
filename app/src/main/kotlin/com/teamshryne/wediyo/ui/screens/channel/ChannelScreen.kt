package com.teamshryne.wediyo.ui.screens.channel

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
fun ChannelScreen(
    browseId: String,
    onBack: () -> Unit,
    onPlaylistClick: (String) -> Unit = {},
    onCourseClick: (String) -> Unit = {},
    onShowClick: (String) -> Unit = {},
    onPodcastClick: (String) -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    onShortClick: (String) -> Unit = {},
    vm: ChannelViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { SettingsManager(ctx) }
    var thumbQ by remember { mutableStateOf("high") }
    var avatarQ by remember { mutableStateOf("high") }

    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(Unit) { settings.avatarQuality.collectLatest { avatarQ = it } }
    LaunchedEffect(browseId) { vm.load(browseId) }

    val listState = rememberLazyListState()
    LaunchedEffect(listState.firstVisibleItemIndex, state.videosContinuation, state.shortsContinuation, state.livesContinuation, state.podcastsContinuation, state.playlistsContinuation, state.postsContinuation, state.storeContinuation, state.coursesContinuation, state.showsContinuation, state.selectedTab) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 4) {
            when (state.selectedTab) {
                "Videos" -> vm.loadMoreVideos()
                "Shorts" -> vm.loadMoreShorts()
                "Live", "Streams" -> vm.loadMoreLive()
                "Podcasts" -> vm.loadMorePodcasts()
                "Playlists" -> vm.loadMorePlaylists()
                "Posts", "Community" -> vm.loadMorePosts()
                "Store" -> vm.loadMoreStore()
                "Courses" -> vm.loadMoreCourses()
                "Shows" -> vm.loadMoreShows()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.home?.header?.title ?: "Channel", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { vm.selectAboutTab() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { pad ->
        when {
            state.isLoading && state.home == null -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && state.home == null -> {
                Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.retry() }) { Text("Retry") }
                }
            }
            state.home != null -> {
                val home = state.home!!
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(pad)) {
                    item {
                        val h = home.header
                        if (h != null) {
                            Column(Modifier.fillMaxWidth()) {
                                if (h.bannerUrl.isNotBlank() || h.bannersJson != "[]") {
                                    val bannerUrl = bestThumbUrl(h.bannersJson, h.bannerUrl, "high")
                                    AsyncImage(model = bannerUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(140.dp).background(Color(0xFF111111)), contentScale = ContentScale.Crop)
                                }
                                Row(Modifier.fillMaxWidth().clickable { vm.selectAboutTab() }.padding(16.dp), verticalAlignment = Alignment.Top) {
                                    val avatarUrl = bestThumbUrl(h.avatarsJson, h.avatarUrl, avatarQ)
                                    AsyncImage(model = avatarUrl, contentDescription = h.title, modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF222222)), contentScale = ContentScale.Crop)
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(h.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 2, modifier = Modifier.clickable { vm.selectAboutTab() })
                                            if (h.verified) Text("  ✓", color = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(h.handle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { vm.selectAboutTab() })
                                        Spacer(Modifier.height(4.dp))
                                        Text(listOf(h.subs, h.videoCount).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (h.description.isNotBlank()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(h.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { vm.selectAboutTab() })
                                        }
                                    }
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                if (home.tabs.isNotEmpty()) {
                                    val allTabs = home.tabs + com.teamshryne.wediyo.data.model.UiChannelTab("About", state.selectedTab == "About", "", "", "")
                                    ScrollableTabRow(
                                        selectedTabIndex = allTabs.indexOfFirst { it.title == state.selectedTab }.coerceAtLeast(0),
                                        edgePadding = 16.dp, indicator = {}, divider = {}
                                    ) {
                                        allTabs.forEach { tab ->
                                            val selected = tab.title == state.selectedTab
                                            Tab(
                                                selected = selected,
                                                onClick = { vm.selectGenericTab(tab.title) },
                                                text = {
                                                    Text(tab.title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            )
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                    when (state.selectedTab) {
                        "Videos" -> {
                            if (state.videoChips.isNotEmpty()) {
                                item {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(state.videoChips.size) { idx ->
                                            val chip = state.videoChips[idx]
                                            FilterChip(selected = chip.selected, onClick = { vm.selectChip(chip) }, label = { Text(chip.title) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            } else if (state.isVideosLoading && state.videosList.isEmpty()) {
                                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } }
                            }
                            if (state.videosList.isNotEmpty()) {
                                items(state.videosList.size) { idx -> val v = state.videosList[idx]; com.teamshryne.wediyo.ui.components.ChannelVideoListCard(v, thumbQ) { onVideoClick(v.id) } }
                            } else if (!state.isVideosLoading) {
                                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No videos", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            }
                            if (state.isVideosLoading && state.videosList.isNotEmpty()) {
                                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } }
                            }
                            state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                            if (state.videosContinuation.isBlank() && state.videosList.isNotEmpty() && !state.isVideosLoading) {
                                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("You've reached the end", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            }
                        }
                        "Shorts" -> {
                            if (state.shortsChips.isNotEmpty()) {
                                item {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(state.shortsChips.size) { idx -> val chip = state.shortsChips[idx]; FilterChip(selected = chip.selected, onClick = { vm.selectShortsChip(chip) }, label = { Text(chip.title) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer)) }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            } else if (state.isShortsLoading && state.shortsList.isEmpty()) {
                                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } }
                            }
                            if (state.shortsList.isNotEmpty()) {
                                val rows = state.shortsList.chunked(3)
                                items(rows.size) { rowIdx ->
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val row = rows[rowIdx]
                                        row.forEach { s ->
                                            Column(Modifier.weight(1f).clickable { onShortClick(s.videoId) }) {
                                                Box(Modifier.fillMaxWidth().aspectRatio(9f/16f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111))) {
                                                    AsyncImage(model = bestThumbUrl(s.thumbsJson, s.thumbUrl, "high"), contentDescription = s.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Text(s.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), modifier = Modifier.padding(horizontal = 2.dp))
                                                if (s.views.isNotBlank()) Text(s.views, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            } else if (!state.isShortsLoading) {
                                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No shorts", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            }
                            if (state.isShortsLoading && state.shortsList.isNotEmpty()) {
                                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } }
                            }
                            state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                            if (state.shortsContinuation.isBlank() && state.shortsList.isNotEmpty() && !state.isShortsLoading) {
                                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("You've reached the end", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            }
                        }
                        "Live", "Streams" -> {
                            if (state.liveChips.isNotEmpty()) {
                                item {
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(state.liveChips.size) { idx -> val chip = state.liveChips[idx]; FilterChip(selected = chip.selected, onClick = { vm.selectLiveChip(chip) }, label = { Text(chip.title) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer)) }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            } else if (state.isLiveLoading && state.livesList.isEmpty()) {
                                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } }
                            }
                            if (state.livesList.isNotEmpty()) {
                                items(state.livesList.size) { idx -> val v = state.livesList[idx]; com.teamshryne.wediyo.ui.components.ChannelVideoListCard(v, thumbQ) { onVideoClick(v.id) } }
                            } else if (!state.isLiveLoading) {
                                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No live streams", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            }
                            if (state.isLiveLoading && state.livesList.isNotEmpty()) {
                                item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } }
                            }
                            state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                            if (state.livesContinuation.isBlank() && state.livesList.isNotEmpty() && !state.isLiveLoading) {
                                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("You've reached the end", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            }
                        }
                        "Podcasts" -> {
                            if (state.isPodcastsLoading && state.podcastsList.isEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } } }
                            if (state.podcastsList.isNotEmpty()) {
                                items(state.podcastsList.size) { idx ->
                                    val p = state.podcastsList[idx]
                                    Row(Modifier.fillMaxWidth().clickable { onPodcastClick(p.browseId.ifBlank { p.podcastId }) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111))) {
                                            AsyncImage(model = bestThumbUrl(p.thumbsJson, p.thumbUrl, "high"), contentDescription = p.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            if (p.episodeCountText.isNotBlank()) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(p.episodeCountText, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(p.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                            Spacer(Modifier.height(4.dp))
                                            if (p.updatedText.isNotBlank()) Text(p.updatedText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                            else if (p.episodeCountText.isNotBlank()) Text(p.episodeCountText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Box(Modifier.size(24.dp)) { Text("⋮", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                            } else if (!state.isPodcastsLoading) { item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No podcasts", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                            if (state.isPodcastsLoading && state.podcastsList.isNotEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } } }
                            state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                            if (state.podcastsContinuation.isBlank() && state.podcastsList.isNotEmpty() && !state.isPodcastsLoading) { item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("You've reached the end", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                        }
                        "Playlists" -> {
                            if (state.isPlaylistsLoading && state.playlistsList.isEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } } }
                            if (state.playlistsList.isNotEmpty()) {
                                items(state.playlistsList.size) { idx ->
                                    val pl = state.playlistsList[idx]
                                    Row(Modifier.fillMaxWidth().clickable { onPlaylistClick(pl.playlistId) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111))) {
                                            AsyncImage(model = bestThumbUrl(pl.thumbsJson, pl.thumbUrl, "high"), contentDescription = pl.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            if (pl.videoCountText.isNotBlank()) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(pl.videoCountText, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(pl.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                            Spacer(Modifier.height(4.dp))
                                            Text(pl.videoCountText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            } else if (!state.isPlaylistsLoading) { item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No playlists", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                            if (state.isPlaylistsLoading && state.playlistsList.isNotEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } } }
                            state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                            if (state.playlistsContinuation.isBlank() && state.playlistsList.isNotEmpty() && !state.isPlaylistsLoading) { item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("You've reached the end", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                        }
                        "Posts", "Community" -> {
                            if (state.isPostsLoading && state.postsList.isEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } } }
                            if (state.postsList.isNotEmpty()) {
                                items(state.postsList.size) { idx ->
                                    val post = state.postsList[idx]
                                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                val av = bestThumbUrl(post.authorThumbsJson, post.authorThumbUrl, avatarQ)
                                                AsyncImage(model = av, contentDescription = post.authorText, modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF222222)), contentScale = ContentScale.Crop)
                                                Spacer(Modifier.width(8.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(post.authorText, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                                                    Text(post.publishedTimeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            if (post.contentText.isNotBlank()) {
                                                Spacer(Modifier.height(8.dp))
                                                Text(post.contentText, style = MaterialTheme.typography.bodySmall, maxLines = 8, overflow = TextOverflow.Ellipsis)
                                            }
                                            when (post.attachmentType) {
                                                "poll" -> {
                                                    post.poll?.let { poll ->
                                                        Spacer(Modifier.height(8.dp))
                                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            poll.choices.forEach { ch ->
                                                                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                                                    Text(ch.text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                                                                }
                                                            }
                                                            if (poll.totalVotesText.isNotBlank()) Text(poll.totalVotesText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                        }
                                                    }
                                                }
                                                "singleImage" -> {
                                                    if (post.images.isNotEmpty()) {
                                                        Spacer(Modifier.height(8.dp))
                                                        val img = post.images[0]
                                                        val url = bestThumbUrl(img.thumbsJson, img.url, "high")
                                                        AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF111111)), contentScale = ContentScale.Crop)
                                                    }
                                                }
                                                "multiImage" -> {
                                                    if (post.images.isNotEmpty()) {
                                                        Spacer(Modifier.height(8.dp))
                                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            items(post.images) { img ->
                                                                val url = bestThumbUrl(img.thumbsJson, img.url, "high")
                                                                AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(160.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF111111)), contentScale = ContentScale.Crop)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                                if (post.voteCountText.isNotBlank()) Text("♥ ${post.voteCountText}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (post.voteCountLabel.isNotBlank()) Text(post.voteCountLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            } else if (!state.isPostsLoading) { item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No posts", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                            if (state.isPostsLoading && state.postsList.isNotEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } } }
                            state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                            if (state.postsContinuation.isBlank() && state.postsList.isNotEmpty() && !state.isPostsLoading) { item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("You've reached the end", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                        }
                        "Store" -> {
                            if (state.isStoreLoading && state.storeList.isEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } } }
                            if (state.storeList.isNotEmpty()) {
                                items(state.storeList.size) { idx ->
                                    val prod = state.storeList[idx]
                                    Row(Modifier.fillMaxWidth().clickable {
                                        if (prod.productUrl.isNotBlank()) {
                                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, prod.productUrl.toUri())) } catch (_: Exception) {}
                                        }
                                    }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111))) {
                                            AsyncImage(model = bestThumbUrl(prod.thumbsJson, prod.thumbUrl, "high"), contentDescription = prod.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(prod.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                            Spacer(Modifier.height(2.dp))
                                            if (prod.priceText.isNotBlank()) Text(prod.priceText, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                                            if (prod.fromText.isNotBlank()) Text(prod.fromText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (prod.merchantName.isNotBlank()) Text(prod.merchantName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            } else if (!state.isStoreLoading) { item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No products", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                            if (state.isStoreLoading && state.storeList.isNotEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } } }
                            state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                            if (state.storeContinuation.isBlank() && state.storeList.isNotEmpty() && !state.isStoreLoading) { item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("You've reached the end", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                        }
                        "Courses" -> {
                            if (state.isCoursesLoading && state.coursesList.isEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } } }
                            if (state.coursesList.isNotEmpty()) {
                                items(state.coursesList.size) { idx ->
                                    val c = state.coursesList[idx]
                                    Row(Modifier.fillMaxWidth().clickable { onCourseClick(c.browseId.ifBlank { c.playlistId }) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111))) {
                                            AsyncImage(model = bestThumbUrl(c.thumbsJson, c.thumbUrl, "high"), contentDescription = c.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            if (c.videoCountText.isNotBlank()) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(c.videoCountText, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(c.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                            Spacer(Modifier.height(4.dp))
                                            Text(c.videoCountText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            } else if (!state.isCoursesLoading) { item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No courses", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                            if (state.isCoursesLoading && state.coursesList.isNotEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } } }
                            state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                            if (state.coursesContinuation.isBlank() && state.coursesList.isNotEmpty() && !state.isCoursesLoading) { item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("You've reached the end", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                        }
                        "Shows" -> {
                            if (state.isShowsLoading && state.showsList.isEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) } } }
                            if (state.showsList.isNotEmpty()) {
                                items(state.showsList.size) { idx ->
                                    val s = state.showsList[idx]
                                    Row(Modifier.fillMaxWidth().clickable { onShowClick(s.browseId.ifBlank { s.showId }) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111))) {
                                            AsyncImage(model = bestThumbUrl(s.thumbsJson, s.thumbUrl, "high"), contentDescription = s.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                            if (s.episodeCountText.isNotBlank()) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(s.episodeCountText, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(s.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                            Spacer(Modifier.height(2.dp))
                                            if (s.subtitle.isNotBlank()) Text(s.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (s.episodeCountText.isNotBlank()) Text(s.episodeCountText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            } else if (!state.isShowsLoading) { item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No shows", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                            if (state.isShowsLoading && state.showsList.isNotEmpty()) { item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } } }
                            state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                            if (state.showsContinuation.isBlank() && state.showsList.isNotEmpty() && !state.isShowsLoading) { item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("You've reached the end", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                        }
                        "About" -> {
                            if (state.isAboutLoading && state.aboutData == null) {
                                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(32.dp)) } }
                            } else if (state.aboutData != null) {
                                val about = state.aboutData!!
                                item {
                                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("Description", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                        Text(about.description.ifBlank { "No description" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                        Text("Details", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            if (about.country.isNotBlank()) Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text("Location", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)); Text(about.country, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                            if (about.joinedDateText.isNotBlank()) Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text("Joined", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)); Text(about.joinedDateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                            if (about.subscriberCountText.isNotBlank()) Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text("Subscribers", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)); Text(about.subscriberCountText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                            if (about.viewCountText.isNotBlank()) Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text("Views", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)); Text(about.viewCountText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                            if (about.videoCountText.isNotBlank()) Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text("Videos", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)); Text(about.videoCountText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                            if (about.canonicalUrl.isNotBlank()) Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Text("Channel URL", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)); Text(about.displayUrl.ifBlank { about.canonicalUrl }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, about.canonicalUrl.toUri())) } catch (_: Exception) {} }) }
                                        }
                                        if (about.links.isNotEmpty()) {
                                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                            Text("Links", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                            about.links.forEach { link ->
                                                Row(Modifier.fillMaxWidth().clickable {
                                                    val url = link.url.ifBlank { link.linkText }
                                                    if (url.isNotBlank()) {
                                                        val finalUrl = if (url.startsWith("http")) url else "https://$url"
                                                        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, finalUrl.toUri())) } catch (_: Exception) {}
                                                    }
                                                }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    val fav = bestThumbUrl(link.faviconsJson, link.faviconUrl, "high")
                                                    if (fav.isNotBlank()) {
                                                        AsyncImage(model = fav, contentDescription = link.title, modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEEEEE)), contentScale = ContentScale.Crop)
                                                    } else {
                                                        Box(Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFEEEEEE)), contentAlignment = Alignment.Center) { Text(link.title.take(1), style = MaterialTheme.typography.labelSmall) }
                                                    }
                                                    Spacer(Modifier.width(12.dp))
                                                    Column(Modifier.weight(1f)) {
                                                        Text(link.title, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Text(link.linkText.ifBlank { link.url }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No about info", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                            }
                            state.error?.let { e -> item { Card(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) { Text(e, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(16.dp)) } } }
                        }
                        else -> {
                            items(home.shelves.size) { idx ->
                                val shelf = home.shelves[idx]
                                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                    Row(Modifier.fillMaxWidth().clickable { vm.openShelf(shelf.title) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${shelf.title} ›", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.clickable { vm.openShelf(shelf.title) })
                                    }
                                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(shelf.videos) { v ->
                                            Column(Modifier.width(180.dp).clickable { onVideoClick(v.id) }) {
                                                Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF111111))) {
                                                    AsyncImage(model = bestThumbUrl(v.thumbnailsJson, v.thumbnailUrl, thumbQ), contentDescription = v.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                                    if (v.durationText.isNotBlank()) Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) { Text(v.durationText, color = Color.White, style = MaterialTheme.typography.labelSmall) }
                                                }
                                                Spacer(Modifier.height(6.dp))
                                                Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                                                val meta = listOf(v.viewCountText, v.publishedText).filter { it.isNotBlank() }.joinToString(" • ")
                                                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                    if (idx < home.shelves.size - 1) HorizontalDivider(Modifier.padding(top = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                            if (home.shelves.isEmpty()) { item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No videos", color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                        }
                    }
                }
            }
        }
    }
}
