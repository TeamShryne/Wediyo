package com.teamshryne.wediyo.ui.screens.shorts

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ShortsScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Shorts", style = MaterialTheme.typography.titleLarge)
            Text("Coming soon", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Will use Go engine Shorts results (shortsLockupViewModel) + vertical feed. For now search → Shorts chip shows 25 shorts.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 32.dp))
        }
    }
}
