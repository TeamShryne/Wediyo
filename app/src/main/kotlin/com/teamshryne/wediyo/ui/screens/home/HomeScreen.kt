package com.teamshryne.wediyo.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teamshryne.wediyo.ui.components.HomeTopBar

@Composable
fun HomeScreen(onSearch: () -> Unit, onSettings: () -> Unit) {
    Scaffold(
        topBar = { HomeTopBar(onSearch = onSearch, onSettings = onSettings) }
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Welcome to Wediyo", style = MaterialTheme.typography.titleLarge)
                        Text("YouTube-like metadata client — search, filters, chips, thumbnails", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onSearch) { Text("Search videos") }
                    }
                }
            }
            item { Text("Discover", style = MaterialTheme.typography.titleMedium) }
            item {
                Text("• Search exposes all Go engine features: videos, shorts, playlists, channels, topic cards, chips, filter groups (TYPE/DURATION/UPLOAD DATE/FEATURES/PRIORITIZE), pagination, estimatedResults, thumbnail qualities, channel avatars, badges", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Text("Settings → thumbnail quality (high/720p/360p/low) and avatar quality affect all cards via Coil. Change in Settings and re-search to see effect.", style = MaterialTheme.typography.bodySmall)
            }
            item {
                Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
                    Text("Home feed — coming soon (subscriptions/personalized)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
