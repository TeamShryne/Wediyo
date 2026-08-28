package com.teamshryne.wediyo.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.teamshryne.wediyo.data.prefs.SettingsManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mgr = remember { SettingsManager(ctx) }
    val scope = rememberCoroutineScope()
    var thumbQ by remember { mutableStateOf("high") }
    var avatarQ by remember { mutableStateOf("high") }
    var theme by remember { mutableStateOf("system") }
    LaunchedEffect(Unit) {
        mgr.thumbQuality.collect { thumbQ = it }
    }
    LaunchedEffect(Unit) {
        mgr.avatarQuality.collect { avatarQ = it }
    }
    LaunchedEffect(Unit) {
        mgr.theme.collect { theme = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") }, navigationIcon = { TextButton(onClick = onBack) { Text("←") } })
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Data & Quality", style = MaterialTheme.typography.titleMedium)
            Text("These affect how Go engine thumbnails are displayed (all qualities are fetched, setting picks which URL Coil loads).", style = MaterialTheme.typography.bodySmall)
            SettingDropdown("Thumbnail quality", listOf("high","720p","360p","low"), thumbQ) { v -> scope.launch { mgr.setThumbQuality(v) } }
            SettingDropdown("Channel avatar quality", listOf("high","720p","360p","low"), avatarQ) { v -> scope.launch { mgr.setAvatarQuality(v) } }
            Divider()
            Text("Theme", style = MaterialTheme.typography.titleMedium)
            SettingDropdown("Theme", listOf("system","light","dark"), theme) { v -> scope.launch { mgr.setTheme(v) } }
            Divider()
            Text("Engine", style = MaterialTheme.typography.titleMedium)
            Text("Go gomobile 1.26 • minSdk 21 • Innertube WEB client 2.202... • chips + filter groups (TYPE/DURATION/UPLOAD DATE/FEATURES/PRIORITIZE) via BuildSearchParams • pagination via continuation tokens • aar via go.yml", style = MaterialTheme.typography.bodySmall)
            Text("All Go features are exposed: videos/shorts/playlists/channels/topicCard/chips/filterGroups/continuation/estimatedResults/thumbnails/channelAvatars/badges", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingDropdown(label: String, options: List<String>, selected: String, onSelect: (String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { expanded = true }) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); expanded = false })
                }
            }
        }
    }
}
