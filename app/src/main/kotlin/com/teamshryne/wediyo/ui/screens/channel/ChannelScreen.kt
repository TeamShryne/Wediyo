package com.teamshryne.wediyo.ui.screens.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.util.bestThumbUrl
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(browseId: String, onBack: () -> Unit, vm: ChannelViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { SettingsManager(ctx) }
    var thumbQ by remember { mutableStateOf("high") }
    var avatarQ by remember { mutableStateOf("high") }

    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(Unit) { settings.avatarQuality.collectLatest { avatarQ = it } }
    LaunchedEffect(browseId) { vm.load(browseId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.home?.header?.title ?: "Channel", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            state.error != null -> {
                Column(Modifier.fillMaxSize().padding(pad).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { vm.retry() }) { Text("Retry") }
                }
            }
            state.home != null -> {
                val home = state.home!!
                LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                    // Header
                    item {
                        val h = home.header
                        if (h != null) {
                            Column(Modifier.fillMaxWidth()) {
                                // Banner
                                if (h.bannerUrl.isNotBlank() || h.bannersJson != "[]") {
                                    val bannerUrl = bestThumbUrl(h.bannersJson, h.bannerUrl, "high")
                                    AsyncImage(
                                        model = bannerUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth().height(140.dp).background(Color(0xFF111111)),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(Modifier.fillMaxWidth().height(100.dp).background(Color(0xFF1A1A1A)))
                                }
                                // Avatar + title row
                                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                                    val avatarUrl = bestThumbUrl(h.avatarsJson, h.avatarUrl, avatarQ)
                                    AsyncImage(
                                        model = avatarUrl,
                                        contentDescription = h.title,
                                        modifier = Modifier.size(80.dp).clip(CircleShape).background(Color(0xFF222222)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(h.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 2)
                                            if (h.verified) Text("  ✓", color = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(h.handle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            listOf(h.subs, h.videoCount).filter { it.isNotBlank() }.joinToString(" • "),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (h.description.isNotBlank()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                h.description,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                // Tabs
                                if (home.tabs.isNotEmpty()) {
                                    ScrollableTabRow(
                                        selectedTabIndex = home.tabs.indexOfFirst { it.selected }.coerceAtLeast(0),
                                        edgePadding = 16.dp,
                                        indicator = {},
                                        divider = {}
                                    ) {
                                        home.tabs.forEach { tab ->
                                            Tab(
                                                selected = tab.selected,
                                                onClick = { /* TODO: switch tabs when videos/live implemented */ },
                                                text = {
                                                    Text(
                                                        tab.title,
                                                        style = MaterialTheme.typography.labelLarge.copy(
                                                            fontWeight = if (tab.selected) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        color = if (tab.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                    // Shelves
                    items(home.shelves.size) { idx ->
                        val shelf = home.shelves[idx]
                        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(shelf.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            }
                            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(shelf.videos) { v ->
                                    Column(Modifier.width(180.dp).clickable { /* TODO play */ }) {
                                        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF111111))) {
                                            AsyncImage(
                                                model = bestThumbUrl(v.thumbnailsJson, v.thumbnailUrl, thumbQ),
                                                contentDescription = v.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            if (v.durationText.isNotBlank()) {
                                                Box(
                                                    Modifier.align(Alignment.BottomEnd).padding(4.dp)
                                                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                                ) {
                                                    Text(v.durationText, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium))
                                        Text(
                                            "${v.viewCountText} • ${v.publishedText}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (idx < home.shelves.size - 1) {
                                HorizontalDivider(Modifier.padding(top = 12.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                    if (home.shelves.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("No videos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
