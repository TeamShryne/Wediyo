package com.teamshryne.wediyo.ui.screens.subscriptions

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SubscriptionsScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Subscriptions", style = MaterialTheme.typography.titleLarge)
            Text("Coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Will show channelRenderer subscriptions + browse. Go engine already parses channels (verified, handle, subs).", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}
