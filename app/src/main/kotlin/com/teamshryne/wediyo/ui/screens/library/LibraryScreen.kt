package com.teamshryne.wediyo.ui.screens.library

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.teamshryne.wediyo.data.local.HistoryWithVideo
import com.teamshryne.wediyo.data.local.LocalPlaylistEntity
import com.teamshryne.wediyo.data.local.SavedVideoRow
import com.teamshryne.wediyo.ui.components.VideoSheetManager
import com.teamshryne.wediyo.util.bestThumbUrl
import com.teamshryne.wediyo.util.rememberHaptics
import java.text.DateFormat
import java.util.Date

sealed interface LocalListKind {
    data class Custom(val playlist: LocalPlaylistEntity) : LocalListKind
    data object History : LocalListKind
    data object WatchLater : LocalListKind
    data object Liked : LocalListKind
}

data class DetailRow(
    val videoId: String,
    val title: String,
    val author: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val durationText: String,
    val meta: String
)

private fun formatWatchTime(ms: Long): String {
    val mins = (ms / 60000).toInt()
    return when {
        mins < 1 -> "just started"
        mins < 60 -> "$mins min watched"
        else -> {
            val h = mins / 60
            val m = mins % 60
            if (m == 0) "${h}h watched" else "${h}h ${m}m watched"
        }
    }
}

private fun formatTimeAgo(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val mins = (diff / 60000).toInt()
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        mins < 1440 -> "${mins / 60}h ago"
        mins < 10080 -> "${mins / 1440}d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(ts))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onVideoClick: (String) -> Unit = {},
    onChannelClick: (String) -> Unit = {},
    onSubscriptionsClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    vm: LibraryViewModel = viewModel()
) {
    val h = rememberHaptics()

    val history by vm.history.collectAsState()
    val watchLater by vm.watchLater.collectAsState()
    val liked by vm.liked.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val subs by vm.subscriptions.collectAsState()
    val distinctVideos by vm.distinctVideos.collectAsState()
    val totalWatchMs by vm.totalWatchMs.collectAsState()

    var section by remember { mutableStateOf("All") }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var openList by remember { mutableStateOf<LocalListKind?>(null) }

    // ── Detail view (custom or system list, PlaylistScreen-style) ──
    openList?.let { kind ->
        LocalListDetail(
            kind = kind,
            vm = vm,
            onBack = { openList = null },
            onVideoClick = onVideoClick
        )
        return
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text("e.g. Guitar practice") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        h.confirm()
                        vm.createPlaylist(newName)
                        newName = ""
                        showCreate = false
                    }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } }
        )
    }

    val libraryEmpty = history.isEmpty() && watchLater.isEmpty() && liked.isEmpty() && playlists.isEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("You", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                        val statLine = buildList {
                            if (distinctVideos > 0) add("$distinctVideos videos")
                            if (totalWatchMs > 0) add(formatWatchTime(totalWatchMs))
                            if (subs.isNotEmpty()) add("${subs.size} channels")
                        }.joinToString(" • ")
                        if (statLine.isNotBlank()) {
                            Text(statLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        // Whole library empty → one friendly placeholder, nothing else
        if (libraryEmpty) {
            EmptyLibrary(
                modifier = Modifier.fillMaxSize().padding(pad),
                onSearchClick = { h.tap(); onSearchClick() },
                onCreatePlaylist = { h.tap(); showCreate = true }
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Quick chips
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("All", "History", "Watch Later", "Liked", "Playlists").forEach { s ->
                        item {
                            FilterChip(
                                selected = section == s,
                                onClick = { h.tap(); section = s },
                                label = { Text(s) }
                            )
                        }
                    }
                }
            }

            // Subscriptions strip (hidden when empty)
            if (section == "All" && subs.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Subscriptions",
                        count = subs.size,
                        onMore = { h.tap(); onSubscriptionsClick() }
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(subs.take(20), key = { it.channelId }) { s ->
                            Column(
                                Modifier.width(64.dp).clickable { h.tap(); onChannelClick(s.channelId) },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = bestThumbUrl(s.avatarsJson, s.avatarUrl, "high"),
                                    contentDescription = s.title,
                                    modifier = Modifier.size(56.dp).clip(CircleShape).background(Color(0xFF222222)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    s.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }

            // ── Playlists section: system lists (non-empty only) + custom ──
            if (section == "All" || section == "Playlists") {
                item {
                    SectionHeader(
                        title = "Playlists",
                        count = playlists.size,
                        trailing = {
                            TextButton(onClick = { h.tap(); showCreate = true }) {
                                Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New")
                            }
                        }
                    )
                }
                if (section == "All") {
                    if (history.isNotEmpty()) {
                        item {
                            SystemPlaylistCard(
                                title = "History",
                                subtitle = "Recently watched",
                                count = history.size,
                                icon = Icons.Filled.History,
                                coverUrl = bestThumbUrl(
                                    history.firstOrNull()?.thumbnailsJson ?: "[]",
                                    history.firstOrNull()?.thumbnailUrl ?: "", "high"
                                ),
                                onClick = { openList = LocalListKind.History }
                            )
                        }
                    }
                    if (watchLater.isNotEmpty()) {
                        item {
                            SystemPlaylistCard(
                                title = "Watch Later",
                                subtitle = "Saved for later",
                                count = watchLater.size,
                                icon = Icons.Filled.Schedule,
                                coverUrl = bestThumbUrl(
                                    watchLater.firstOrNull()?.thumbnailsJson ?: "[]",
                                    watchLater.firstOrNull()?.thumbUrlSafe(), "high"
                                ),
                                onClick = { openList = LocalListKind.WatchLater }
                            )
                        }
                    }
                    if (liked.isNotEmpty()) {
                        item {
                            SystemPlaylistCard(
                                title = "Liked videos",
                                subtitle = "Videos you liked",
                                count = liked.size,
                                icon = Icons.Filled.Favorite,
                                coverUrl = bestThumbUrl(
                                    liked.firstOrNull()?.thumbnailsJson ?: "[]",
                                    liked.firstOrNull()?.thumbUrlSafe(), "high"
                                ),
                                onClick = { openList = LocalListKind.Liked }
                            )
                        }
                    }
                }
                val customs = if (section == "Playlists") playlists else playlists.take(5)
                items(customs, key = { "p${it.playlistId}" }) { pl ->
                    PlaylistRow(
                        playlist = pl,
                        onClick = { openList = LocalListKind.Custom(pl) }
                    )
                }
                if (playlists.isEmpty() && section == "Playlists") {
                    item { EmptyHint(icon = Icons.Filled.PlaylistPlay, text = "No playlists yet — tap New to create one") }
                }
                if (section == "All" && playlists.size > 5) {
                    item { MoreRow("Show all ${playlists.size} playlists") { h.tap(); section = "Playlists" } }
                }
            }

            // ── Filtered list views (hidden when empty → single hint) ──
            if (section == "History") {
                if (history.isEmpty()) {
                    item { EmptyHint(icon = Icons.Filled.History, text = "Nothing watched yet — history will appear here") }
                } else {
                    items(history, key = { "h${it.id}" }) { row ->
                        HistoryRow(
                            row = row,
                            onClick = { h.tap(); onVideoClick(row.videoId) },
                            onMore = { h.longPress(); VideoSheetManager.show(vm.historyToUiVideo(row)) },
                            onRemove = { h.toggle(false); vm.removeHistoryVideo(row.videoId) }
                        )
                    }
                }
            }
            if (section == "Watch Later") {
                if (watchLater.isEmpty()) {
                    item { EmptyHint(icon = Icons.Filled.Schedule, text = "Nothing saved — tap ⋮ on any video to save it here") }
                } else {
                    items(watchLater, key = { "w${it.videoId}" }) { row ->
                        SavedRow(
                            row = row,
                            subtitle = "Saved ${formatTimeAgo(row.addedAt)}",
                            onClick = { h.tap(); onVideoClick(row.videoId) },
                            onMore = { h.longPress(); VideoSheetManager.show(vm.toUiVideo(row)) },
                            onRemove = { h.toggle(false); vm.removeWatchLater(row.videoId) }
                        )
                    }
                }
            }
            if (section == "Liked") {
                if (liked.isEmpty()) {
                    item { EmptyHint(icon = Icons.Filled.Favorite, text = "Nothing liked yet — tap Like on any video") }
                } else {
                    items(liked, key = { "l${it.videoId}" }) { row ->
                        SavedRow(
                            row = row,
                            subtitle = "Liked ${formatTimeAgo(row.addedAt)}",
                            onClick = { h.tap(); onVideoClick(row.videoId) },
                            onMore = { h.longPress(); VideoSheetManager.show(vm.toUiVideo(row)) },
                            onRemove = { h.toggle(false); vm.unlike(row.videoId) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

private fun SavedVideoRow.thumbUrlSafe(): String = thumbnailUrl ?: ""

// ─────────────────────────────────────────────────────────────
// Local list detail — mirrors PlaylistScreen (hero + Play all /
// Shuffle + indexed rows). Works for custom + system lists.
// ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocalListDetail(
    kind: LocalListKind,
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val h = rememberHaptics()
    var menuOpen by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }

    val title: String
    val subtitle: String
    val rows: List<DetailRow>
    val onRemove: ((String) -> Unit)?

    when (kind) {
        is LocalListKind.Custom -> {
            val pid = kind.playlist.playlistId
            val itemsFlow = remember(pid) { vm.playlistItems(pid) }
            val items by itemsFlow.collectAsState()
            title = kind.playlist.title
            subtitle = kind.playlist.description.ifBlank { "Private playlist • on this device" }
            rows = items.map {
                DetailRow(
                    videoId = it.videoId,
                    title = it.title ?: "Untitled",
                    author = it.author ?: "",
                    thumbUrl = it.thumbnailUrl ?: "",
                    thumbsJson = it.thumbnailsJson ?: "[]",
                    durationText = it.durationText ?: "",
                    meta = listOfNotNull(
                        it.author?.takeIf { s -> s.isNotBlank() },
                        it.viewCountText?.takeIf { s -> s.isNotBlank() }
                    ).joinToString(" • ")
                )
            }
            onRemove = { vid -> vm.removeFromPlaylist(pid, vid) }
        }
        LocalListKind.History -> {
            val history by vm.history.collectAsState()
            title = "History"
            subtitle = "Recently watched • on this device"
            rows = history.take(200).map {
                DetailRow(
                    videoId = it.videoId,
                    title = it.title ?: "Untitled",
                    author = "",
                    thumbUrl = it.thumbnailUrl ?: "",
                    thumbsJson = it.thumbnailsJson ?: "[]",
                    durationText = it.durationText ?: "",
                    meta = listOfNotNull(
                        it.viewCountText?.takeIf { s -> s.isNotBlank() },
                        "Watched ${formatTimeAgo(it.watchedAt)}"
                    ).joinToString(" • ")
                )
            }
            onRemove = { vid -> vm.removeHistoryVideo(vid) }
        }
        LocalListKind.WatchLater -> {
            val queue by vm.watchLater.collectAsState()
            title = "Watch Later"
            subtitle = "Saved for later • on this device"
            rows = queue.map {
                DetailRow(
                    videoId = it.videoId,
                    title = it.title ?: "Untitled",
                    author = it.author ?: "",
                    thumbUrl = it.thumbnailUrl ?: "",
                    thumbsJson = it.thumbnailsJson ?: "[]",
                    durationText = it.durationText ?: "",
                    meta = listOfNotNull(
                        it.author?.takeIf { s -> s.isNotBlank() },
                        "Saved ${formatTimeAgo(it.addedAt)}"
                    ).joinToString(" • ")
                )
            }
            onRemove = { vid -> vm.removeWatchLater(vid) }
        }
        LocalListKind.Liked -> {
            val liked by vm.liked.collectAsState()
            title = "Liked videos"
            subtitle = "Videos you liked • on this device"
            rows = liked.map {
                DetailRow(
                    videoId = it.videoId,
                    title = it.title ?: "Untitled",
                    author = it.author ?: "",
                    thumbUrl = it.thumbnailUrl ?: "",
                    thumbsJson = it.thumbnailsJson ?: "[]",
                    durationText = it.durationText ?: "",
                    meta = listOfNotNull(
                        it.author?.takeIf { s -> s.isNotBlank() },
                        "Liked ${formatTimeAgo(it.addedAt)}"
                    ).joinToString(" • ")
                )
            }
            onRemove = { vid -> vm.unlike(vid) }
        }
    }

    val showMenu = when (kind) {
        is LocalListKind.Custom -> true
        LocalListKind.WatchLater -> true
        LocalListKind.Liked -> true
        LocalListKind.History -> false // history controls live in Settings → History & privacy
    }

    if (showRename && kind is LocalListKind.Custom) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = renameText.isNotBlank(),
                    onClick = {
                        h.confirm()
                        vm.renamePlaylist(kind.playlist.playlistId, renameText)
                        showRename = false
                        onBack()
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }
    if (showDelete && kind is LocalListKind.Custom) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete \"${kind.playlist.title}\"?") },
            text = { Text("The playlist will be removed from this device. Videos stay in history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        h.toggle(false)
                        vm.deletePlaylist(kind.playlist.playlistId)
                        showDelete = false
                        onBack()
                    }
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Keep") } }
        )
    }
    if (showClear) {
        val label = when (kind) {
            LocalListKind.WatchLater -> "Watch Later"
            LocalListKind.Liked -> "Liked videos"
            else -> "this list"
        }
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("Clear $label?") },
            text = { Text("Removes all videos from $label on this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        h.toggle(false)
                        when (kind) {
                            LocalListKind.WatchLater -> vm.clearWatchLater()
                            LocalListKind.Liked -> vm.clearLiked()
                            else -> {}
                        }
                        showClear = false
                    }
                ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("Keep") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { h.tap(); onBack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    if (showMenu) {
                        Box {
                            IconButton(onClick = { h.tap(); menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "List options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                if (kind is LocalListKind.Custom) {
                                    DropdownMenuItem(
                                        text = { Text("Rename") },
                                        onClick = {
                                            menuOpen = false
                                            renameText = kind.playlist.title
                                            showRename = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Delete playlist", color = MaterialTheme.colorScheme.error) },
                                        onClick = { menuOpen = false; showDelete = true }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (kind == LocalListKind.WatchLater) "Clear Watch Later"
                                                else "Clear liked videos",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = { menuOpen = false; showClear = true }
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.PlaylistPlay, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Nothing here yet", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        "Tap ⋮ on any video to save it here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            return@Scaffold
        }
        val cover = rows.firstOrNull()
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 16.dp)) {
            // Hero — mirrors PlaylistScreen
            item {
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF0F0F0F))) {
                        AsyncImage(
                            model = bestThumbUrl(cover?.thumbsJson ?: "[]", cover?.thumbUrl ?: "", "high"),
                            contentDescription = title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text(
                                "${rows.size} video${if (rows.size == 1) "" else "s"}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Button(
                                onClick = { h.confirm(); rows.firstOrNull()?.let { onVideoClick(it.videoId) } },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play all", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilledTonalButton(
                                onClick = { h.confirm(); rows.firstOrNull()?.let { onVideoClick(it.videoId) } },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play all")
                            }
                            OutlinedButton(
                                onClick = {
                                    h.confirm()
                                    rows.randomOrNull()?.let { onVideoClick(it.videoId) }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Shuffle")
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            // Indexed rows — mirrors PlaylistScreen
            itemsIndexed(rows, key = { idx, r -> "${idx}_${r.videoId}" }) { idx, r ->
                var rowMenu by remember(r.videoId) { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clickable { h.tap(); onVideoClick(r.videoId) }.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(Modifier.width(28.dp).padding(top = 28.dp), contentAlignment = Alignment.TopCenter) {
                        Text("${idx + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.size(width = 150.dp, height = 84.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0F0F0F))) {
                        AsyncImage(
                            model = bestThumbUrl(r.thumbsJson, r.thumbUrl, "high"),
                            contentDescription = r.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (r.durationText.isNotBlank()) {
                            Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Text(r.durationText, color = Color.White, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                        Spacer(Modifier.height(4.dp))
                        val metaLine = listOfNotNull(r.author.takeIf { it.isNotBlank() }, r.meta.takeIf { it.isNotBlank() }).joinToString(" • ")
                        if (metaLine.isNotBlank()) {
                            Text(metaLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Box {
                        IconButton(onClick = { h.longPress(); rowMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Remove from $title", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    rowMenu = false
                                    h.toggle(false)
                                    onRemove?.invoke(r.videoId)
                                }
                            )
                        }
                    }
                }
                if (idx < rows.size - 1) {
                    HorizontalDivider(Modifier.padding(start = 40.dp, end = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("End • ${rows.size} videos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(
    modifier: Modifier = Modifier,
    onSearchClick: () -> Unit,
    onCreatePlaylist: () -> Unit
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "Nothing here yet",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center
            )
            Text(
                "Videos you watch, like and save will live here. Start exploring to fill up your library.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(onClick = onSearchClick, shape = RoundedCornerShape(24.dp)) {
                Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Search videos")
            }
            TextButton(onClick = onCreatePlaylist) { Text("Or create a playlist") }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int = 0,
    onMore: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (count > 0) {
            Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
        }
        if (trailing != null) trailing()
        else if (onMore != null) {
            IconButton(onClick = onMore, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "See all $title")
            }
        }
    }
}

@Composable
private fun MoreRow(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(text)
    }
}

@Composable
private fun EmptyHint(icon: ImageVector, text: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(shape = CircleShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Icon(icon, null, modifier = Modifier.padding(10.dp).size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SystemPlaylistCard(
    title: String,
    subtitle: String,
    count: Int,
    icon: ImageVector,
    coverUrl: String,
    onClick: () -> Unit
) {
    val h = rememberHaptics()
    Card(
        onClick = { h.tap(); onClick() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111)),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(model = coverUrl, contentDescription = title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(icon, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(26.dp))
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(3.dp).background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                    Text("$count", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "$subtitle • $count video${if (count == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PlaylistRow(playlist: LocalPlaylistEntity, onClick: () -> Unit) {
    val h = rememberHaptics()
    Card(
        onClick = { h.tap(); onClick() },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Icon(Icons.Filled.PlaylistPlay, null, modifier = Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(playlist.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (playlist.description.isNotBlank()) playlist.description else "Tap to view videos",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryRow(row: HistoryWithVideo, onClick: () -> Unit, onMore: () -> Unit, onRemove: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.width(120.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111111))
        ) {
            AsyncImage(
                model = bestThumbUrl(row.thumbnailsJson ?: "[]", row.thumbnailUrl ?: "", "high"),
                contentDescription = row.title,
                modifier = Modifier.fillMaxWidth().height(68.dp),
                contentScale = ContentScale.Crop
            )
            if (row.completed) {
                Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(0.7f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(12.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title ?: "Untitled", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(2.dp))
            Text(
                formatTimeAgo(row.watchedAt),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (row.progress > 0.02f && !row.completed) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { row.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                )
            }
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Manage / Save") }, onClick = { showMenu = false; onMore() })
                DropdownMenuItem(text = { Text("Remove", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun SavedRow(row: SavedVideoRow, subtitle: String, onClick: () -> Unit, onMore: () -> Unit, onRemove: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.width(120.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111111))
        ) {
            AsyncImage(
                model = bestThumbUrl(row.thumbnailsJson ?: "[]", row.thumbnailUrl ?: "", "high"),
                contentDescription = row.title,
                modifier = Modifier.fillMaxWidth().height(68.dp),
                contentScale = ContentScale.Crop
            )
            if (!row.durationText.isNullOrBlank()) {
                Box(Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.Black.copy(0.8f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Text(row.durationText!!, color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(row.title ?: "Untitled", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(2.dp))
            val meta = listOfNotNull(row.author?.takeIf { it.isNotBlank() }, subtitle).joinToString(" • ")
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Options", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Manage / Save") }, onClick = { showMenu = false; onMore() })
                DropdownMenuItem(text = { Text("Remove", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onRemove() })
            }
        }
    }
}
