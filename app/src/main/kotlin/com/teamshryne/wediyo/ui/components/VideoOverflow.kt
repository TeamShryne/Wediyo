package com.teamshryne.wediyo.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teamshryne.wediyo.data.model.UiVideo
import com.teamshryne.wediyo.util.rememberHaptics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Global overflow sheet: any ⋮ on any video opens the same
 * Like / Watch Later / Add-to-playlist sheet. Host once in MainActivity.
 */
object VideoSheetManager {
    private val _sheet = MutableStateFlow<UiVideo?>(null)
    val sheet: StateFlow<UiVideo?> = _sheet

    fun show(video: UiVideo) {
        if (video.id.isBlank()) return
        _sheet.value = video
    }

    fun hide() {
        _sheet.value = null
    }
}

@Composable
fun VideoSheetHost() {
    val video by VideoSheetManager.sheet.collectAsState()
    if (video != null) {
        VideoActionsSheet(video = video!!, onDismiss = { VideoSheetManager.hide() })
    }
}

@Composable
fun VideoOverflowButton(video: UiVideo) {
    val h = rememberHaptics()
    IconButton(
        onClick = { h.longPress(); VideoSheetManager.show(video) },
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = "More options for ${video.title}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
