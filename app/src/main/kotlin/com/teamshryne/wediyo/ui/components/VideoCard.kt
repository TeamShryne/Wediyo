package com.teamshryne.wediyo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.teamshryne.wediyo.data.model.UiVideo
import com.teamshryne.wediyo.util.bestThumbUrl
@Composable
fun VideoCard(video: UiVideo, thumbQuality: String, avatarQuality: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111))) {
            AsyncImage(
                model = bestThumbUrl(video.thumbnailsJson, video.thumbnailUrl, thumbQuality),
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (video.durationText.isNotBlank()) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(video.durationText, color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (video.isLive) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(6.dp)
                        .background(Color.Red, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) { Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(
                model = bestThumbUrl(video.avatarsJson, video.avatarUrl, avatarQuality),
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF222222)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                val meta = buildList {
                    if (video.author.isNotBlank()) add(video.author)
                    if (video.viewCountText.isNotBlank()) add(video.viewCountText)
                    if (video.publishedText.isNotBlank() && video.publishedText != video.viewCountText) add(video.publishedText)
                }.joinToString(" • ")
                if (meta.isNotBlank()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                if (video.badges.isNotEmpty()) {
                    Row(Modifier.padding(top = 4.dp)) {
                        video.badges.take(2).forEach { b ->
                            Box(Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                Text(b, style = MaterialTheme.typography.labelSmall)
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }
            VideoOverflowButton(video)
        }
    }
}

@Composable
fun ChannelVideoListCard(video: UiVideo, thumbQuality: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color(0xFF111111))
        ) {
            AsyncImage(
                model = bestThumbUrl(video.thumbnailsJson, video.thumbnailUrl, thumbQuality),
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (video.durationText.isNotBlank()) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(video.durationText, color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            if (video.isLive) {
                Box(
                    Modifier.align(Alignment.BottomStart).padding(4.dp)
                        .background(Color.Red, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) { Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            // no channel name/logo for channel videos — views first, no leading dot
            val meta = listOf(video.viewCountText, video.publishedText).filter { it.isNotBlank() }.joinToString(" • ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (video.badges.isNotEmpty()) {
                Row(Modifier.padding(top = 4.dp)) {
                    video.badges.take(2).forEach { b ->
                        Box(Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            Text(b, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                }
            }
        }
        VideoOverflowButton(video)
    }
}

@Composable
fun ChannelCard(channel: com.teamshryne.wediyo.data.model.UiChannel, thumbQuality: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = com.teamshryne.wediyo.util.bestThumbUrl(channel.thumbsJson, channel.thumbUrl, thumbQuality),
            contentDescription = channel.title,
            modifier = Modifier.size(64.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(channel.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                if (channel.verified) Text("  ✓", color = MaterialTheme.colorScheme.primary)
            }
            Text(channel.handle.ifBlank { channel.subs }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (channel.desc.isNotBlank()) Text(channel.desc, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SubscribeButton(
            channelId = channel.channelId,
            title = channel.title,
            handle = channel.handle,
            avatarUrl = channel.thumbUrl,
            avatarsJson = channel.thumbsJson,
            subsText = channel.subs,
            verified = channel.verified,
            compact = true
        )
    }
}

@Composable
fun PlaylistCard(pl: com.teamshryne.wediyo.data.model.UiPlaylist, thumbQuality: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f/9f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A1A))) {
            AsyncImage(
                model = com.teamshryne.wediyo.util.bestThumbUrl(pl.thumbsJson, pl.thumbUrl, thumbQuality),
                contentDescription = pl.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(Modifier.align(Alignment.BottomEnd).fillMaxWidth().background(Color.Black.copy(0.7f)).padding(8.dp)) {
                Text(pl.countText, color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            if (pl.isCourse) {
                Box(Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xFF6060FF), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text("Course", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(pl.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text("${pl.channelName} • ${pl.countText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ShortsShelf(shorts: List<com.teamshryne.wediyo.data.model.UiShort>, thumbQuality: String, onShortClick: (String) -> Unit) {
    if (shorts.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text("Shorts", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shorts.size) { idx ->
                val s = shorts[idx]
                Column(Modifier.width(140.dp).clickable { onShortClick(s.videoId) }) {
                    Box(Modifier.fillMaxWidth().aspectRatio(9f/16f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF111111))) {
                        AsyncImage(
                            model = com.teamshryne.wediyo.util.bestThumbUrl(s.thumbsJson, s.thumbUrl, thumbQuality),
                            contentDescription = s.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(s.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    Text(s.views, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
