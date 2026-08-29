package com.teamshryne.wediyo.ui.screens.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbUp
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
import com.teamshryne.wediyo.ui.components.ChannelVideoListCard
import com.teamshryne.wediyo.util.bestThumbUrl
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
    videoId: String,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    onVideoClick: (String) -> Unit = {},
    vm: VideoViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { SettingsManager(ctx) }
    var thumbQ by remember { mutableStateOf("high") }
    var avatarQ by remember { mutableStateOf("high") }

    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(Unit) { settings.avatarQuality.collectLatest { avatarQ = it } }
    LaunchedEffect(videoId) { vm.load(videoId) }

    val listState = rememberLazyListState()

    // pagination triggers for related + comments
    LaunchedEffect(listState.firstVisibleItemIndex, state.relatedContinuation, state.commentsContinuation) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 6) {
            if (state.relatedContinuation != null && !state.relatedLoading) vm.loadMoreRelated()
            if (state.commentsContinuation != null && !state.commentsLoading) vm.loadMoreComments()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Video", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(pad).padding(16.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { vm.retry() }) { Text("Retry") }
                    }
                }
            }
            state.detail != null -> {
                val d = state.detail!!

                // Bottom sheet for title details (views/likes/duration/details)
                if (state.showDetailsSheet) {
                    ModalBottomSheet(onDismissRequest = { vm.setDetailsSheet(false) }) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(d.title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            // quick meta
                            val meta = buildList {
                                if (d.viewCountText.isNotBlank()) add(d.viewCountText) else if (d.viewCount > 0) add("${d.viewCount} views")
                                if (d.publishDate.isNotBlank()) add(d.publishDate) else if (d.uploadDate.isNotBlank()) add(d.uploadDate)
                                if (d.category.isNotBlank()) add(d.category)
                                if (d.isLiveContent) add("Live")
                            }.joinToString(" • ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Stats grid moved inside sheet
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                StatChip("Views", d.viewCountText.ifBlank { if (d.viewCount > 0) d.viewCount.toString() else "—" }, Modifier.weight(1f))
                                StatChip("Likes", d.likeCountText.ifBlank { d.likeCount.takeIf { it > 0 }?.toString() ?: "—" }, Modifier.weight(1f))
                                StatChip("Duration", d.durationText.ifBlank { formatDuration(d.lengthSeconds) }, Modifier.weight(1f))
                            }

                            // Captions
                            if (d.captionTracks.isNotEmpty()) {
                                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text("Captions • ${d.captionTracks.size}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Spacer(Modifier.height(6.dp))
                                        d.captionTracks.take(6).forEach { ct ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Text(ct.name.ifBlank { ct.languageCode }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                Text(ct.kind.ifBlank { "standard" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        if (d.translationLanguages.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp))
                                            Text("${d.translationLanguages.size} translation languages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            // Streams
                            if (d.formats.isNotEmpty() || d.adaptiveFormats.isNotEmpty()) {
                                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text("Streams", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "${d.formats.size} progressive • ${d.adaptiveFormats.size} adaptive • expires in ${d.expiresInSeconds}s",
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (d.hlsManifestUrl.isNotBlank() || d.dashManifestUrl.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text("HLS/DASH manifests available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        if (d.storyboardSpec.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text("Storyboards: ${d.storyboardSpec.take(90)}…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                        val top = d.adaptiveFormats.filter { !it.isAudio }.sortedByDescending { it.bitrate }.take(3)
                                        if (top.isNotEmpty()) {
                                            Spacer(Modifier.height(6.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                top.forEach { f ->
                                                    AssistChip(onClick = {}, label = { Text(f.qualityLabel.ifBlank { "${f.height}p" }) })
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Details
                            Card(shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("Details", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Spacer(Modifier.height(6.dp))
                                    DetailRow("Video ID", d.videoId)
                                    DetailRow("Channel ID", d.channelId)
                                    DetailRow("Channel", d.channelTitle.ifBlank { d.author })
                                    DetailRow("Category", d.category)
                                    DetailRow("Published", d.publishDate.ifBlank { d.uploadDate })
                                    DetailRow("Family safe", if (d.isFamilySafe) "Yes" else "No")
                                    DetailRow("Playable in embed", if (d.playableInEmbed) "Yes" else "No")
                                    DetailRow("Countries", if (d.availableCountries.isNotEmpty()) "${d.availableCountries.size} • ${d.availableCountries.take(5).joinToString(", ")}…" else "—")
                                    if (d.keywords.isNotEmpty()) DetailRow("Keywords", d.keywords.take(8).joinToString(", "))
                                    if (d.paidPromotionText.isNotBlank()) DetailRow("Paid promotion", d.paidPromotionText)
                                    if (d.canonicalUrl.isNotBlank()) DetailRow("Canonical", d.canonicalUrl)
                                    if (d.embedUrl.isNotBlank()) DetailRow("Embed", d.embedUrl)
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Hero thumbnail — 16:9 black placeholder with play icon (no playback yet)
                    item {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = bestThumbUrl(d.thumbnailsJson, d.thumbnailUrl, thumbQ),
                                contentDescription = d.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                Modifier
                                    .align(Alignment.Center)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                            }
                            if (d.durationText.isNotBlank()) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text(d.durationText, color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (d.isLive) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .background(Color.Red, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) { Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }

                    // Title — clickable opens sheet (whole details part lives in sheet now)
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.setDetailsSheet(true) }
                                .padding(16.dp)
                        ) {
                            Text(d.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 4)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("•", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                val quick = buildList {
                                    if (d.viewCountText.isNotBlank()) add(d.viewCountText) else if (d.viewCount > 0) add("${d.viewCount} views")
                                    if (d.publishDate.isNotBlank()) add(d.publishDate.take(12))
                                }.joinToString(" • ")
                                if (quick.isNotBlank()) Text(quick, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            if (d.keywords.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(d.keywords.take(8).joinToString("  ") { "#$it" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    // Channel row
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = d.channelId.isNotBlank()) { onChannelClick(d.channelId) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = bestThumbUrl(d.channelAvatarsJson, d.channelAvatarUrl, avatarQ),
                                contentDescription = d.channelTitle,
                                modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF222222)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.channelTitle.ifBlank { d.author }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val subMeta = buildList {
                                    if (d.subscriberCountText.isNotBlank()) add(d.subscriberCountText)
                                    if (d.channelHandle.isNotBlank()) add(d.channelHandle)
                                }.joinToString(" • ")
                                if (subMeta.isNotBlank()) {
                                    Text(subMeta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                            }
                            FilledTonalButton(onClick = { if (d.channelId.isNotBlank()) onChannelClick(d.channelId) }, shape = RoundedCornerShape(20.dp)) {
                                Text("Visit")
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    // Action chips: like, share, etc
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {},
                                label = { Text(if (d.likeCountText.isNotBlank()) d.likeCountText else if (d.likeCount > 0) "${d.likeCount}" else "Like") },
                                leadingIcon = { Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            AssistChip(
                                onClick = {},
                                label = { Text("Share") },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                            AssistChip(
                                onClick = { vm.setDetailsSheet(true) },
                                label = { Text(d.playabilityStatus.ifBlank { "Details" }) },
                                leadingIcon = { Icon(Icons.Filled.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }

                    // Description expandable (stays in main list)
                    item {
                        val desc = d.description.ifBlank { d.shortDescription }
                        if (desc.isNotBlank()) {
                            Card(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clickable { vm.toggleDesc() },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Description", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                                        Text(if (state.expandedDesc) "Show less" else "Show more", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (state.expandedDesc) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    // Up next — paginated, all quality thumbs, channel + views
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Up next", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                            if (state.related.isNotEmpty()) Text("${state.related.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (state.related.isNotEmpty()) {
                        items(state.related.size) { idx ->
                            val v = state.related[idx]
                            // Use ChannelVideoListCard which shows thumbnail (all qualities), duration, title, views·time — no broken avatar, views fixed for 269K case
                            ChannelVideoListCard(video = v, thumbQuality = thumbQ, onClick = { onVideoClick(v.id) })
                        }
                        if (state.relatedLoading) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        } else if (state.relatedContinuation != null) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = { vm.loadMoreRelated() }) { Text("Load more") }
                                }
                            }
                        }
                    } else {
                        item {
                            Text("No related videos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                    }

                    // Comments — Mediyo flow port
                    item {
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.commentsCount ?: d.commentsCountText.takeIf { it.isNotBlank() }?.let { it } ?: "Comments",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.weight(1f)
                            )
                            if (state.commentsSortFilters.isNotEmpty()) {
                                // show selected sort
                                val selected = state.commentsSortFilters.find { it.selected }?.title ?: state.commentsSortFilters.firstOrNull()?.title ?: ""
                                if (selected.isNotBlank()) AssistChip(onClick = {}, label = { Text(selected) })
                            }
                        }
                        // sort chips (Top/Newest) — Mediyo style
                        if (state.commentsSortFilters.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.commentsSortFilters.take(4).forEach { sf ->
                                    FilterChip(
                                        selected = sf.selected,
                                        onClick = { vm.switchCommentsSort(sf.continuationToken) },
                                        label = { Text(sf.title) }
                                    )
                                }
                            }
                        }
                    }

                    if (state.comments.isEmpty() && state.commentsLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp)) }
                        }
                    } else if (state.comments.isEmpty()) {
                        item {
                            Text(
                                if (state.commentsContinuation == null && state.detail?.commentsContinuation.isNullOrBlank()) "Comments unavailable" else "No comments yet — be first",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    } else {
                        items(state.comments.size) { idx ->
                            val c = state.comments[idx]
                            CommentCard(comment = c, avatarQ = avatarQ, onReplyClick = { cont ->
                                // simple inline replies expansion via ViewModel
                                // for brevity, just no-op or could fetch
                            })
                        }
                        if (state.commentsLoading) {
                            item { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } }
                        } else if (state.commentsContinuation != null) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    TextButton(onClick = { vm.loadMoreComments() }) { Text("Load more comments") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CommentCard(comment: com.teamshryne.wediyo.data.model.UiComment, avatarQ: String, onReplyClick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        AsyncImage(
            model = com.teamshryne.wediyo.util.bestThumbUrl("", comment.author.avatar, avatarQ),
            contentDescription = comment.author.name,
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF222222)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.author.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                if (comment.author.isVerified) Text("  ✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                if (comment.author.isCreator) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.background(Color(0xFF3EA6FF), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp)) {
                        Text("Creator", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                Spacer(Modifier.width(6.dp))
                Text(comment.publishedTime, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Text(comment.content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (comment.likeCount.isNotBlank()) Text("♥ ${comment.likeCount}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (comment.replyCount.isNotBlank()) Text(comment.replyCount, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(enabled = comment.repliesContinuation.isNotBlank()) { onReplyClick(comment.repliesContinuation) })
            }
        }
    }
}

private fun formatDuration(secs: Long): String {
    if (secs <= 0) return "—"
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
