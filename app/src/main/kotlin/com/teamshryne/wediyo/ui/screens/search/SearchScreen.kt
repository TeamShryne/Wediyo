package com.teamshryne.wediyo.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.ui.components.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, vm: SearchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { SettingsManager(ctx) }
    var thumbQ by remember { mutableStateOf("high") }
    var avatarQ by remember { mutableStateOf("high") }
    var showFilters by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        settings.thumbQuality.collectLatest { thumbQ = it }
    }
    LaunchedEffect(Unit) {
        settings.avatarQuality.collectLatest { avatarQ = it }
    }
    var queryInput by remember { mutableStateOf(state.query) }
    val listState = rememberLazyListState()

    // pagination trigger
    LaunchedEffect(listState.firstVisibleItemIndex, state.result?.continuation) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 4) vm.loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = queryInput,
                        onValueChange = { queryInput = it; vm.setQuery(it) },
                        placeholder = { Text("Search") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                actions = {
                    TextButton(onClick = { vm.search(queryInput, state.params) }) { Text("Go") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // chips row if available
            state.result?.chips?.let { chips ->
                if (chips.isNotEmpty()) {
                    ChipsRow(chips = chips, onChipClick = { vm.searchChip(it.token) }, onFilterClick = { showFilters = true })
                }
            } ?: run {
                // show filter button even without chips
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Button(onClick = { showFilters = true }) { Text("Filters") }
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
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            state.error?.let { e ->
                Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }

            val r = state.result
            if (r != null) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    // topic card
                    r.topicTitle?.let { t ->
                        item { Card(Modifier.fillMaxWidth().padding(12.dp)) { Column(Modifier.padding(12.dp)) { Text(t, style = MaterialTheme.typography.titleMedium); Text(r.topicBrowseId ?: "", style = MaterialTheme.typography.bodySmall) } } }
                    }
                    // metrics
                    item { Text("${r.estimated} results • ${r.videos.size} videos • ${r.channels.size} channels • ${r.shorts.size} shorts • ${r.playlists.size} playlists", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(12.dp)) }

                    // channels
                    items(r.channels.size) { idx -> ChannelCard(r.channels[idx], thumbQ) {} }
                    // playlists
                    items(r.playlists.size) { idx -> PlaylistCard(r.playlists[idx], thumbQ) {} }
                    // shorts shelf
                    if (r.shorts.isNotEmpty()) {
                        item { ShortsShelf(r.shorts, thumbQ) {} }
                    }
                    // videos
                    items(r.videos.size) { idx -> VideoCard(r.videos[idx], thumbQ, avatarQ) {} }

                    if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(12.dp)) }
                    if (r.continuation.isBlank()) item { Text("End of results", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall) }
                }
            } else if (!state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("Search for videos, shorts, playlists, channels", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
