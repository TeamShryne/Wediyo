package com.teamshryne.wediyo.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LibraryScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Library", style = MaterialTheme.typography.titleLarge)
            Text("Coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Will show playlists/courses (lockupViewModel) + history. Go engine already parses playlists (isCourse, videoCount).", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}
