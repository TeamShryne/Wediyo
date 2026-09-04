package com.teamshryne.wediyo.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamshryne.wediyo.data.local.LibraryRepository
import com.teamshryne.wediyo.util.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Local-only Subscribe pill. Simple, human, haptic.
 * Caches channel snapshot so Subscriptions tab works offline.
 */
@Composable
fun SubscribeButton(
    channelId: String,
    title: String,
    handle: String = "",
    avatarUrl: String = "",
    avatarsJson: String = "[]",
    subsText: String = "",
    verified: Boolean = false,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (channelId.isBlank()) return
    val ctx = LocalContext.current
    remember { LibraryRepository.init(ctx); true }
    val scope = rememberCoroutineScope()
    val h = rememberHaptics()
    val subFlow = remember(channelId) {
        try { LibraryRepository.isSubscribed(channelId) } catch (_: Exception) { kotlinx.coroutines.flow.flowOf(false) }
    }
    val subscribed by subFlow.collectAsState(initial = false)

    if (subscribed) {
        OutlinedButton(
            onClick = {
                h.toggle(false)
                scope.launch { LibraryRepository.unsubscribe(channelId) }
            },
            modifier = modifier,
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            if (!compact) {
                Spacer(Modifier.width(6.dp))
                Text("Subscribed", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    } else {
        Button(
            onClick = {
                h.confirm()
                scope.launch {
                    LibraryRepository.subscribe(channelId, title, handle, avatarUrl, avatarsJson, subsText, verified)
                }
            },
            modifier = modifier,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.onBackground,
                contentColor = MaterialTheme.colorScheme.background
            )
        ) {
            Icon(Icons.Outlined.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
            if (!compact) {
                Spacer(Modifier.width(6.dp))
                Text("Subscribe", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold))
            }
        }
    }
}

@Composable
fun BellIcon() {
    Icon(Icons.Filled.Notifications, contentDescription = "Subscribed")
}
