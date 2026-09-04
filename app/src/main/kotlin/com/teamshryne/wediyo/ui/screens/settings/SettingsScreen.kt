package com.teamshryne.wediyo.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.teamshryne.wediyo.data.local.LibraryRepository
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.util.rememberHaptics
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mgr = remember { SettingsManager(ctx) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var thumbQ by remember { mutableStateOf("high") }
    var avatarQ by remember { mutableStateOf("high") }
    var theme by remember { mutableStateOf("system") }
    var shortsQ by remember { mutableStateOf("auto") }
    var videoQ by remember { mutableStateOf("auto") }
    LaunchedEffect(Unit) { mgr.thumbQuality.collect { thumbQ = it } }
    LaunchedEffect(Unit) { mgr.avatarQuality.collect { avatarQ = it } }
    LaunchedEffect(Unit) { mgr.theme.collect { theme = it } }
    LaunchedEffect(Unit) { mgr.shortsQuality.collect { shortsQ = it } }
    LaunchedEffect(Unit) { mgr.videoQuality.collect { videoQ = it } }
    LaunchedEffect(Unit) { try { LibraryRepository.init(ctx) } catch (_: Exception) {} }
    val historyPaused by mgr.historyPaused.collectAsState(initial = false)
    var confirmClear by remember { mutableStateOf<String?>(null) }

    if (confirmClear != null) {
        val key = confirmClear!!
        val (title, body) = when (key) {
            "history" -> "Clear watch history?" to "Removes all watched videos and resume positions on this device. Playlists, likes and subscriptions stay."
            "watchlater" -> "Clear Watch Later?" to "Empties your saved-for-later queue on this device."
            "liked" -> "Clear liked videos?" to "Removes all likes on this device."
            else -> "Clear search history?" to "Removes past searches stored on this device."
        }
        AlertDialog(
            onDismissRequest = { confirmClear = null },
            title = { Text(title) },
            text = { Text(body) },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.toggle(false)
                        scope.launch {
                            try {
                                when (key) {
                                    "history" -> LibraryRepository.clearHistory()
                                    "watchlater" -> LibraryRepository.clearWatchLater()
                                    "liked" -> LibraryRepository.clearLiked()
                                    else -> LibraryRepository.clearSearchHistory()
                                }
                            } catch (_: Exception) {}
                        }
                        confirmClear = null
                    }
                ) { Text("Clear", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = null }) { Text("Keep") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                windowInsets = WindowInsets.statusBars
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Quality section
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Playback & Quality", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        "Choose how crisp thumbnails and avatars appear. Higher quality uses more data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SettingDropdown("Thumbnail quality", listOf("high", "720p", "360p", "low"), thumbQ) { v -> scope.launch { mgr.setThumbQuality(v) } }
                    SettingDropdown("Channel avatar quality", listOf("high", "720p", "360p", "low"), avatarQ) { v -> scope.launch { mgr.setAvatarQuality(v) } }
                    SettingDropdown("Video quality", listOf("auto", "1080p", "720p", "480p", "360p"), videoQ) { v -> scope.launch { mgr.setVideoQuality(v) } }
                    SettingDropdown("Shorts quality", listOf("auto", "1080p", "720p", "480p", "360p"), shortsQ) { v -> scope.launch { mgr.setShortsQuality(v) } }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Appearance", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    SettingDropdown("Theme", listOf("system", "light", "dark"), theme) { v -> scope.launch { mgr.setTheme(v) } }
                }
            }

            // History & privacy — the only place that pauses or wipes library data
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                elevation = CardDefaults.cardElevation(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("History & privacy", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        "Your library lives only on this device — no account, no cloud.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Pause watch history", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Text(
                                if (historyPaused) "Paused — new watches aren't saved"
                                else "New watches are saved to history",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = historyPaused,
                            onCheckedChange = { paused ->
                                haptics.toggle(!paused)
                                scope.launch { mgr.setHistoryPaused(paused) }
                            }
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    PrivacyRow("Clear watch history", "History + resume positions") { haptics.longPress(); confirmClear = "history" }
                    PrivacyRow("Clear Watch Later", "Your saved-for-later queue") { haptics.longPress(); confirmClear = "watchlater" }
                    PrivacyRow("Clear liked videos", "All likes on this device") { haptics.longPress(); confirmClear = "liked" }
                    PrivacyRow("Clear search history", "Past searches on this device") { haptics.longPress(); confirmClear = "searches" }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("About", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        "Wediyo · Metadata client powered by Go & Innertube",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "YouTube is a trademark of Google LLC.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text("Clear", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SettingDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface)
            ) { Text(selected.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    val isSel = opt == selected
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(opt.replaceFirstChar { it.uppercase() })
                                if (isSel) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        onClick = { onSelect(opt); expanded = false }
                    )
                }
            }
        }
    }
}
