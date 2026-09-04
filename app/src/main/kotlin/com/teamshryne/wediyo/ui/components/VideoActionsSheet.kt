package com.teamshryne.wediyo.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamshryne.wediyo.data.local.LibraryRepository
import com.teamshryne.wediyo.data.model.UiVideo
import com.teamshryne.wediyo.util.rememberHaptics
import kotlinx.coroutines.launch

/**
 * One sheet for every video: Like / Watch Later / Add to playlist / New playlist.
 * Called from Library lists, Search, Channel, Related. Haptic on every action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoActionsSheet(
    video: UiVideo,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    remember { LibraryRepository.init(ctx); true }
    val scope = rememberCoroutineScope()
    val h = rememberHaptics()
    val vid = video.id
    val likedFlow = remember(vid) {
        try { LibraryRepository.isLiked(vid) } catch (_: Exception) { kotlinx.coroutines.flow.flowOf(false) }
    }
    val savedFlow = remember(vid) {
        try { LibraryRepository.isWatchLater(vid) } catch (_: Exception) { kotlinx.coroutines.flow.flowOf(false) }
    }
    val playlistsFlow = remember {
        try { LibraryRepository.playlists() } catch (_: Exception) { kotlinx.coroutines.flow.flowOf(emptyList()) }
    }
    val liked by likedFlow.collectAsState(initial = false)
    val saved by savedFlow.collectAsState(initial = false)
    val playlists by playlistsFlow.collectAsState(initial = emptyList())
    var showNew by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                video.title.ifBlank { "Video options" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2
            )
            if (video.author.isNotBlank()) {
                Text(video.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))

            SheetRow(
                icon = { Icon(if (liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                title = if (liked) "Liked — tap to remove" else "Like",
                onClick = {
                    h.toggle(!liked)
                    scope.launch {
                        LibraryRepository.cacheVideo(video)
                        LibraryRepository.setLiked(vid, !liked)
                    }
                }
            )
            SheetRow(
                icon = { Icon(if (saved) Icons.Filled.Schedule else Icons.Outlined.Schedule, null) },
                title = if (saved) "Saved to Watch Later — tap to remove" else "Save to Watch Later",
                onClick = {
                    h.toggle(!saved)
                    scope.launch {
                        if (saved) LibraryRepository.removeWatchLater(vid)
                        else LibraryRepository.addWatchLater(video)
                    }
                }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Add to playlist", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                TextButton(onClick = { h.tap(); showNew = !showNew }) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
            if (showNew) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Playlist name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            h.confirm()
                            scope.launch {
                                val pid = LibraryRepository.createPlaylist(newName)
                                LibraryRepository.addToPlaylist(pid, video)
                                newName = ""
                                showNew = false
                            }
                        }
                    ) { Text("Create") }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (playlists.isEmpty() && !showNew) {
                Text("No playlists yet — tap New to create one.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(modifier = Modifier.height(240.dp)) {
                    items(playlists, key = { it.playlistId }) { pl ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                h.tap()
                                scope.launch { LibraryRepository.addToPlaylist(pl.playlistId, video) }
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(Icons.Filled.PlaylistAdd, null, modifier = Modifier.padding(8.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pl.title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                                if (pl.description.isNotBlank()) Text(pl.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SheetRow(icon: @Composable () -> Unit, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
    }
}
