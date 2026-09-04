package com.teamshryne.wediyo.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.teamshryne.wediyo.data.local.HistoryWithVideo
import com.teamshryne.wediyo.data.local.LocalPlaylistEntity
import com.teamshryne.wediyo.data.local.SavedVideoRow
import com.teamshryne.wediyo.data.model.UiVideo
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.ui.components.VideoActionsSheet
import com.teamshryne.wediyo.util.bestThumbUrl
import com.teamshryne.wediyo.util.rememberHaptics
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

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
    vm: LibraryViewModel = viewModel()
) {
    val ctx = LocalContext.current
    val settings = remember { SettingsManager(ctx) }
    val scope = rememberCoroutineScope()
    val h = rememberHaptics()

    val history by vm.history.collectAsState()
    val watchLater by vm.watchLater.collectAsState()
    val liked by vm.liked.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val subs by vm.subscriptions.collectAsState()
    val distinctVideos by vm.distinctVideos.collectAsState()
    val totalWatchMs by vm.totalWatchMs.collectAsState()
    val historyPaused by settings.historyPaused.collectAsState(initial = false)

    var section by remember { mutableStateOf("All") }
    var sheetVideo by remember { mutableStateOf<UiVideo?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var openPlaylist by remember { mutableStateOf<LocalPlaylistEntity?>(null) }
    var showClearHistory by remember { mutableStateOf(false) }
    var showClearWatchLater by remember { mutableStateOf(false) }

    // ── Playlist detail view ──
    val open = openPlaylist
    if (open != null) {
        PlaylistDetailView(
            playlist = open,
            vm = vm,
            onBack = { openPlaylist = null },
            onVideoClick = onVideoClick
        )
        return
    }

    if (sheetVideo != null) {
        VideoActionsSheet(video = sheetVideo!!, onDismiss = { sheetVideo = null })
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
    if (showClearHistory) {
        AlertDialog(
            onDismissRequest = { showClearHistory = false },
            title = { Text("Clear watch history?") },
            text = { Text("This removes all history and resume positions on this device. Playlists, likes and subscriptions stay.") },
            confirmButton = {
                TextButton(onClick = { h.toggle(false); vm.clearHistory(); showClearHistory = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearHistory = false }) { Text("Keep") } }
        )
    }
    if (showClearWatchLater) {
        AlertDialog(
            onDismissRequest = { showClearWatchLater = false },
            title = { Text("Clear Watch Later?") },
            text = { Text("Removes all videos from your Watch Later queue.") },
            confirmButton = {
                TextButton(onClick = { h.toggle(false); vm.clearWatchLater(); showClearWatchLater = false }) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showClearWatchLater = false }) { Text("Keep") } }
        )
    }

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

            // Subscriptions strip
            if ((section == "All") && subs.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Subscriptions",
                        count = subs.size,
                        onMore = onSubscriptionsClick
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

            // History
            if (section == "All" || section == "History") {
                item {
                    SectionHeader(
                        title = "History",
                        count = history.size,
                        trailing = {
                            Row {
                                IconButton(onClick = {
                                    h.toggle(!historyPaused)
                                    scope.launch { settings.setHistoryPaused(!historyPaused) }
                                }) {
                                    Icon(
                                        if (historyPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                        contentDescription = if (historyPaused) "Resume history" else "Pause history",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (history.isNotEmpty()) {
                                    IconButton(onClick = { h.longPress(); showClearHistory = true }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Clear history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    )
                }
                if (historyPaused) {
                    item {
                        HistoryPausedCard(onResume = {
                            h.toggle(true)
                            scope.launch { settings.setHistoryPaused(false) }
                        })
                    }
                } else if (history.isEmpty()) {
                    item { EmptyHint(icon = Icons.Filled.History, text = "Videos you watch will show up here") }
                } else {
                    val list = if (section == "History") history else history.take(5)
                    items(list, key = { "h${it.id}" }) { row ->
                        HistoryRow(
                            row = row,
                            onClick = { h.tap(); onVideoClick(row.videoId) },
                            onMore = { h.longPress(); sheetVideo = vm.historyToUiVideo(row) },
                            onRemove = { h.toggle(false); vm.removeHistoryVideo(row.videoId) }
                        )
                    }
                    if (section == "All" && history.size > 5) {
                        item {
                            MoreRow("Show all ${history.size} watched") { h.tap(); section = "History" }
                        }
                    }
                }
            }

            // Watch Later
            if (section == "All" || section == "Watch Later") {
                item {
                    SectionHeader(
                        title = "Watch Later",
                        count = watchLater.size,
                        trailing = {
                            if (watchLater.isNotEmpty()) {
                                TextButton(onClick = { h.longPress(); showClearWatchLater = true }) { Text("Clear") }
                            }
                        }
                    )
                }
                if (watchLater.isEmpty()) {
                    item { EmptyHint(icon = Icons.Filled.Schedule, text = "Save videos to watch when you have time") }
                } else {
                    val list = if (section == "Watch Later") watchLater else watchLater.take(5)
                    items(list, key = { "w${it.videoId}" }) { row ->
                        SavedRow(
                            row = row,
                            subtitle = "Saved ${formatTimeAgo(row.addedAt)}",
                            onClick = { h.tap(); onVideoClick(row.videoId) },
                            onMore = { h.longPress(); sheetVideo = vm.toUiVideo(row) },
                            onRemove = { h.toggle(false); vm.removeWatchLater(row.videoId) }
                        )
                    }
                    if (section == "All" && watchLater.size > 5) {
                        item { MoreRow("Show all ${watchLater.size} saved") { h.tap(); section = "Watch Later" } }
                    }
                }
            }

            // Liked
            if (section == "All" || section == "Liked") {
                item { SectionHeader(title = "Liked", count = liked.size) }
                if (liked.isEmpty()) {
                    item { EmptyHint(icon = Icons.Filled.Favorite, text = "Tap the heart on any video to keep it here") }
                } else {
                    val list = if (section == "Liked") liked else liked.take(5)
                    items(list, key = { "l${it.videoId}" }) { row ->
                        SavedRow(
                            row = row,
                            subtitle = "Liked ${formatTimeAgo(row.addedAt)}",
                            onClick = { h.tap(); onVideoClick(row.videoId) },
                            onMore = { h.longPress(); sheetVideo = vm.toUiVideo(row) },
                            onRemove = { h.toggle(false); vm.unlike(row.videoId) }
                        )
                    }
                    if (section == "All" && liked.size > 5) {
                        item { MoreRow("Show all ${liked.size} liked") { h.tap(); section = "Liked" } }
                    }
                }
            }

            // Playlists
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
                if (playlists.isEmpty()) {
                    item { EmptyHint(icon = Icons.Filled.PlaylistPlay, text = "Group videos for practice, trips, kids — anything") }
                } else {
                    val list = if (section == "Playlists") playlists else playlists.take(5)
                    items(list, key = { "p${it.playlistId}" }) { pl ->
                        PlaylistRow(
                            playlist = pl,
                            onClick = { h.tap(); openPlaylist = pl }
                        )
                    }
                    if (section == "All" && playlists.size > 5) {
                        item { MoreRow("Show all ${playlists.size} playlists") { h.tap(); section = "Playlists" } }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
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
private fun HistoryPausedCard(onResume: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Pause, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("History is paused", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text("New watches won't be saved until you resume.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onResume) { Text("Resume") }
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
            androidx.compose.material3.DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("Manage / Save") }, onClick = { showMenu = false; onMore() })
                androidx.compose.material3.DropdownMenuItem(text = { Text("Remove", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onRemove() })
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
            androidx.compose.material3.DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("Manage / Save") }, onClick = { showMenu = false; onMore() })
                androidx.compose.material3.DropdownMenuItem(text = { Text("Remove", color = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; onRemove() })
            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailView(
    playlist: LocalPlaylistEntity,
    vm: LibraryViewModel,
    onBack: () -> Unit,
    onVideoClick: (String) -> Unit
) {
    val itemsFlow = remember(playlist.playlistId) { vm.playlistItems(playlist.playlistId) }
    val items by itemsFlow.collectAsState()
    val h = rememberHaptics()
    var sheetVideo by remember { mutableStateOf<UiVideo?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf(playlist.title) }

    if (sheetVideo != null) VideoActionsSheet(video = sheetVideo!!, onDismiss = { sheetVideo = null })
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete \"${playlist.title}\"?") },
            text = { Text("The playlist will be removed from this device. Videos stay in history.") },
            confirmButton = {
                TextButton(onClick = { h.toggle(false); vm.deletePlaylist(playlist.playlistId); showDelete = false; onBack() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Keep") } }
        )
    }
    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(enabled = renameText.isNotBlank(), onClick = { h.confirm(); vm.renamePlaylist(playlist.playlistId, renameText); showRename = false; onBack() }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(playlist.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                        Text("${items.size} videos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = { h.tap(); onBack() }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") } },
                actions = {
                    TextButton(onClick = { renameText = playlist.title; showRename = true }) { Text("Rename") }
                    IconButton(onClick = { h.longPress(); showDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete playlist", tint = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.PlaylistPlay, null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No videos yet", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text("Use ••• on any video → Add to playlist.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(items, key = { "pi${it.videoId}" }) { row ->
                    SavedRow(
                        row = row,
                        subtitle = row.author ?: "",
                        onClick = { h.tap(); onVideoClick(row.videoId) },
                        onMore = { h.longPress(); sheetVideo = vm.toUiVideo(row) },
                        onRemove = { h.toggle(false); vm.removeFromPlaylist(playlist.playlistId, row.videoId) }
                    )
                }
            }
        }
    }
}
