package com.teamshryne.wediyo.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.teamshryne.wediyo.data.local.SubscriptionRow
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.ui.components.ShortsShelf
import com.teamshryne.wediyo.ui.components.VideoCard
import com.teamshryne.wediyo.util.bestThumbUrl
import com.teamshryne.wediyo.util.rememberHaptics
import kotlinx.coroutines.flow.collectLatest

/**
 * YouTube-style Subscriptions tab.
 *
 * Mirrors YouTube Android: channel avatar bar (All + per-channel filter) →
 * filter chips (All / Videos / Shorts / Live) → reverse-chron feed with a
 * Shorts shelf and LIVE badges, pull-to-refresh, and a Manage sheet for
 * unsubscribing. Subs themselves stay device-local.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onChannelClick: (String) -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    onShortClick: (String) -> Unit = {},
    vm: SubscriptionsViewModel = viewModel()
) {
    val subs by vm.subscriptions.collectAsState()
    val feed by vm.feed.collectAsState()
    val filter by vm.filter.collectAsState()
    val selectedChannel by vm.selectedChannelId.collectAsState()
    val h = rememberHaptics()
    val ctx = LocalContext.current
    val settings = remember { SettingsManager(ctx) }
    var thumbQ by remember { mutableStateOf("high") }
    var avatarQ by remember { mutableStateOf("high") }
    var showManage by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(Unit) { settings.avatarQuality.collectLatest { avatarQ = it } }

    // Auto-load on first entry + when the sub list changes (subscribe/unsubscribe).
    // Error guard: a failed auto-load must not loop — user retries manually.
    LaunchedEffect(subs, feed.isLoading, feed.channelCount, feed.error) {
        if (subs.isNotEmpty() && !feed.isLoading && feed.error == null && feed.channelCount != subs.size) {
            vm.refresh()
        }
    }
    // Clear a stale per-channel selection if that channel was removed
    LaunchedEffect(subs, selectedChannel) {
        if (selectedChannel != null && subs.none { it.channelId == selectedChannel }) {
            vm.selectChannel(null)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Subscriptions", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        if (subs.isNotEmpty()) {
                            Text(
                                "${subs.size} channel${if (subs.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (subs.isNotEmpty()) {
                        IconButton(onClick = { h.tap(); vm.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh subscriptions")
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        if (subs.isEmpty()) {
            EmptySubs(Modifier.fillMaxSize().padding(pad))
            return@Scaffold
        }

        Column(Modifier.fillMaxSize().padding(pad)) {
            // ── Channel bar: All + avatars (YouTube filters feed on tap) ──
            ChannelBar(
                subs = subs,
                selected = selectedChannel,
                avatarQ = avatarQ,
                onSelectAll = { h.tap(); vm.selectChannel(null) },
                onSelect = { id -> h.tap(); vm.selectChannel(if (selectedChannel == id) null else id) },
                onManage = { h.tap(); showManage = true }
            )
            // ── Filter chips ──
            FilterRow(filter = filter, onPick = { h.tap(); vm.selectFilter(it) })

            val videos = vm.filteredVideos(feed, selectedChannel, filter)
            val shorts = vm.filteredShorts(feed, selectedChannel)
            val isEmptyFeed = when (filter) {
                SubFilter.SHORTS -> shorts.isEmpty()
                SubFilter.ALL -> videos.isEmpty() && shorts.isEmpty()
                else -> videos.isEmpty()
            }

            PullToRefreshBox(
                isRefreshing = feed.isLoading,
                onRefresh = { vm.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    feed.error != null && videos.isEmpty() && shorts.isEmpty() -> {
                        FeedError(feed.error ?: "Couldn't load", onRetry = { vm.retry() })
                    }
                    !feed.isLoading && feed.refreshedAt > 0 && isEmptyFeed -> {
                        EmptyFeed(
                            channelName = subs.firstOrNull { it.channelId == selectedChannel }?.title,
                            onRefresh = { vm.refresh() },
                            onShowAll = { vm.selectChannel(null); vm.selectFilter(SubFilter.ALL) }
                        )
                    }
                    filter == SubFilter.SHORTS -> {
                        ShortsGrid(shorts = shorts, thumbQ = thumbQ, onShortClick = onShortClick)
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            if (feed.failedChannels > 0 && !feed.isLoading) {
                                item {
                                    Text(
                                        "Couldn't reach ${feed.failedChannels} channel${if (feed.failedChannels == 1) "" else "s"} — pull to retry",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            // Shorts shelf on top for All (YouTube splits Shorts out of the main list)
                            if (filter == SubFilter.ALL && shorts.isNotEmpty()) {
                                item {
                                    ShortsShelf(
                                        shorts = shorts.map { it.short },
                                        thumbQuality = thumbQ,
                                        onShortClick = onShortClick
                                    )
                                }
                            }
                            items(videos, key = { it.id }) { v ->
                                VideoCard(
                                    video = v,
                                    thumbQuality = thumbQ,
                                    avatarQuality = avatarQ,
                                    onClick = { onVideoClick(v.id) },
                                    showAvatar = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showManage) {
        ModalBottomSheet(onDismissRequest = { showManage = false }) {
            ManageSheet(
                subs = subs,
                avatarQ = avatarQ,
                onChannelClick = { showManage = false; onChannelClick(it) },
                onUnsubscribe = { h.toggle(false); vm.unsubscribe(it) }
            )
        }
    }
}

// ── Channel bar ──────────────────────────────────────────────

@Composable
private fun ChannelBar(
    subs: List<SubscriptionRow>,
    selected: String?,
    avatarQ: String,
    onSelectAll: () -> Unit,
    onSelect: (String) -> Unit,
    onManage: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        item {
            ChannelAvatar(
                label = "All",
                isSelected = selected == null,
                avatarUrl = null,
                avatarsJson = null,
                avatarQ = avatarQ,
                onClick = onSelectAll
            )
        }
        items(subs, key = { it.channelId }) { s ->
            ChannelAvatar(
                label = s.title,
                isSelected = selected == s.channelId,
                avatarUrl = s.avatarUrl,
                avatarsJson = s.avatarsJson,
                avatarQ = avatarQ,
                verified = s.verified,
                onClick = { onSelect(s.channelId) }
            )
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp).clickable(onClick = onManage)
            ) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.NotificationsOff, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Manage", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ChannelAvatar(
    label: String,
    isSelected: Boolean,
    avatarUrl: String?,
    avatarsJson: String?,
    avatarQ: String,
    verified: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(60.dp).clickable(onClick = onClick)
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape)
                .then(
                    if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                )
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl == null) {
                // "All" tile
                Box(
                    Modifier.fillMaxSize().background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(
                            "All",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                AsyncImage(
                    model = bestThumbUrl(avatarsJson ?: "[]", avatarUrl, avatarQ),
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Filter chips ─────────────────────────────────────────────

@Composable
private fun FilterRow(filter: SubFilter, onPick: (SubFilter) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            SubFilter.ALL to "All",
            SubFilter.VIDEOS to "Videos",
            SubFilter.SHORTS to "Shorts",
            SubFilter.LIVE to "Live"
        ).forEach { (f, label) ->
            item {
                FilterChip(
                    selected = filter == f,
                    onClick = { if (filter != f) onPick(f) },
                    label = { Text(label) },
                    shape = RoundedCornerShape(8.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = null
                )
            }
        }
    }
}

// ── Shorts grid (Shorts chip) ────────────────────────────────

@Composable
private fun ShortsGrid(
    shorts: List<SubShort>,
    thumbQ: String,
    onShortClick: (String) -> Unit
) {
    if (shorts.isEmpty()) return
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(shorts, key = { it.short.videoId }) { s ->
            Column(Modifier.clickable { onShortClick(s.short.videoId) }) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(9f / 16f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF111111))
                ) {
                    AsyncImage(
                        model = bestThumbUrl(s.short.thumbsJson, s.short.thumbUrl, thumbQ),
                        contentDescription = s.short.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    s.short.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                )
                Text(
                    listOf(s.channelTitle, s.short.views).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── States ───────────────────────────────────────────────────

@Composable
private fun EmptySubs(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.NotificationsOff, null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "No subscriptions yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center
            )
            Text(
                "Tap Subscribe on any channel and its latest uploads will appear here — on this device, no login needed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyFeed(channelName: String?, onRefresh: () -> Unit, onShowAll: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (channelName != null) "No recent uploads from $channelName"
                else "You're all caught up",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center
            )
            Text(
                "New videos from your channels will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (channelName != null) {
                    TextButton(onClick = onShowAll) { Text("Show all") }
                }
                Button(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
private fun FeedError(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

// ── Manage sheet ─────────────────────────────────────────────

@Composable
private fun ManageSheet(
    subs: List<SubscriptionRow>,
    avatarQ: String,
    onChannelClick: (String) -> Unit,
    onUnsubscribe: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            "Manage subscriptions",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
        Text(
            "${subs.size} channel${if (subs.size == 1) "" else "s"} on this device",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.height(360.dp)) {
            items(subs, key = { it.channelId }) { s ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChannelClick(s.channelId) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = bestThumbUrl(s.avatarsJson, s.avatarUrl, avatarQ),
                        contentDescription = s.title,
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF222222)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                s.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (s.verified) Text("  ✓", color = MaterialTheme.colorScheme.primary)
                        }
                        val sub = listOf(s.handle, s.subsText).filter { it.isNotBlank() }.joinToString(" • ")
                        if (sub.isNotBlank()) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = { onUnsubscribe(s.channelId) }) {
                        Icon(
                            Icons.Filled.NotificationsOff,
                            contentDescription = "Unsubscribe from ${s.title}",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
