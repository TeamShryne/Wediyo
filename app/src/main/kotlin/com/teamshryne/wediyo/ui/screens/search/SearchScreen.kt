package com.teamshryne.wediyo.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.ui.components.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onChannelClick: (String) -> Unit = {}, vm: SearchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { SettingsManager(ctx) }
    var thumbQ by remember { mutableStateOf("high") }
    var avatarQ by remember { mutableStateOf("high") }
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(Unit) { settings.avatarQuality.collectLatest { avatarQ = it } }

    var queryInput by remember { mutableStateOf(state.query) }
    val listState = rememberLazyListState()

    LaunchedEffect(listState.firstVisibleItemIndex, state.result?.continuation) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 4) vm.loadMore()
    }

    Scaffold(
        topBar = {
            SearchTopBar(
                query = queryInput,
                onQueryChange = { queryInput = it; vm.setQuery(it) },
                onBack = onBack,
                onSearch = { vm.search(queryInput, state.params) },
                onClear = { queryInput = ""; vm.setQuery("") }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            // Chips row — always visible when result exists, else subtle filter entry
            if (state.result?.chips?.isNotEmpty() == true) {
                ChipsRow(
                    chips = state.result!!.chips,
                    onChipClick = { vm.searchChip(it.token) },
                    onFilterClick = { showFilters = true }
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            } else if (state.result != null || !state.isLoading) {
                // Show filter entry only when we have a result, keep empty state clean
                if (state.result != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { showFilters = true },
                            label = { Text("Filters") },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = null
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            if (showFilters && state.result?.filterGroups?.isNotEmpty() == true) {
                FilterDialog(
                    groups = state.result!!.filterGroups,
                    initialSelected = emptyMap(),
                    onDismiss = { showFilters = false },
                    onApply = { params ->
                        showFilters = false
                        vm.setParams(params)
                        vm.search(queryInput, params)
                    }
                )
            }

            if (state.isLoading && state.result == null) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onBackground,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            state.error?.let { e ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        e,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            val r = state.result
            if (r != null) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    // Topic card — polished
                    r.topicTitle?.let { t ->
                        item {
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(40.dp)
                                            .padding(0.dp),
                                        contentAlignment = Alignment.Center
                                    ) { Text("◉", color = MaterialTheme.colorScheme.tertiary) }
                                    Column(Modifier.weight(1f)) {
                                        Text(t, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                                        if (!r.topicBrowseId.isNullOrBlank()) {
                                            Text(r.topicBrowseId, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Subtle results meta — single line, muted
                    item {
                        Text(
                            "${r.estimated.ifBlank { "${r.videos.size + r.channels.size + r.playlists.size + r.shorts.size}" }} results  •  ${r.videos.size} videos",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // Channels
                    items(r.channels.size) { idx -> ChannelCard(r.channels[idx], thumbQ) { onChannelClick(r.channels[idx].channelId) } }

                    // Playlists
                    items(r.playlists.size) { idx -> PlaylistCard(r.playlists[idx], thumbQ) {} }

                    // Shorts shelf
                    if (r.shorts.isNotEmpty()) {
                        item { ShortsShelf(r.shorts, thumbQ) {} }
                    }

                    // Videos
                    items(r.videos.size) { idx -> VideoCard(r.videos[idx], thumbQ, avatarQ) {} }

                    if (state.isLoading) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }

                    if (r.continuation.isBlank()) item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "You've reached the end",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (!state.isLoading) {
                // Empty state — beautiful, minimal
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        Box(
                            Modifier
                                .size(80.dp)
                                .padding(0.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Search anything",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Videos, Shorts, playlists, channels — find it all. Try typing a topic above.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        AssistChip(
                            onClick = { /* hint */ },
                            label = { Text("Popular: lofi • comedy • news • tutorials") },
                            shape = RoundedCornerShape(24.dp),
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            border = null
                        )
                    }
                }
            }
        }
    }
}
