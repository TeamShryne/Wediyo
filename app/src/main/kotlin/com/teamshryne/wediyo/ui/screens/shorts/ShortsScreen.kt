package com.teamshryne.wediyo.ui.screens.shorts

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.teamshryne.wediyo.data.prefs.SettingsManager
import androidx.media3.common.util.UnstableApi
import com.teamshryne.wediyo.ui.components.WediyoPlayer
import com.teamshryne.wediyo.util.bestThumbUrl
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun ShortsScreen(
    initialVideoId: String? = null,
    onChannelClick: (String) -> Unit = {},
    vm: ShortsViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val ctx = LocalContext.current
    val settings = remember { SettingsManager(ctx) }
    var thumbQ by remember { mutableStateOf("high") }
    var avatarQ by remember { mutableStateOf("high") }
    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(Unit) { settings.avatarQuality.collectLatest { avatarQ = it } }
    LaunchedEffect(initialVideoId) { vm.loadInitial(initialVideoId) }

    val pagerState = rememberPagerState(initialPage = 0) { state.shorts.size }

    // Prefetch & pagination
    LaunchedEffect(pagerState.currentPage, state.shorts.size, state.continuation) {
        val idx = pagerState.currentPage
        if (state.shorts.isNotEmpty() && idx in state.shorts.indices) {
            vm.fetchDetail(state.shorts[idx].videoId)
            // prefetch next 2
            if (idx + 1 < state.shorts.size) vm.fetchDetail(state.shorts[idx + 1].videoId)
            if (idx + 2 < state.shorts.size) vm.fetchDetail(state.shorts[idx + 2].videoId)
        }
        if (state.shorts.isNotEmpty() && idx >= state.shorts.size - 4) {
            state.continuation?.let { vm.loadMoreContinuation(it) }
        }
    }

    // Comments sheet per short
    val expandedFor = state.expandedCommentsFor
    if (expandedFor != null) {
        val det = state.details[expandedFor]
        val page = state.comments[expandedFor]
        ModalBottomSheet(
            onDismissRequest = { vm.setCommentsSheet(null) },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f).padding(horizontal = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Comments", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                        Text(
                            page?.count ?: det?.commentsCountText ?: "${page?.comments?.size ?: 0} comments",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.clickable { vm.setCommentsSheet(null) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(28.dp).padding(6.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (page == null) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                if (det?.commentsContinuation.isNullOrBlank() && state.details[expandedFor] != null) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("Comments are off", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    } else if (page.comments.isEmpty()) {
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("No comments yet", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    } else {
                        items(page.comments.size) { idx ->
                            val c = page.comments[idx]
                            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                                AsyncImage(model = bestThumbUrl("", c.author.avatar, avatarQ), contentDescription = c.author.name, modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF222222)), contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(c.author.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (c.author.isVerified) Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                        Text("• ${c.publishedTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Text(c.content, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.height(6.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (c.likeCount.isNotBlank()) Text(c.likeCount, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (c.replyCount.isNotBlank()) Text(c.replyCount, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                            if (idx < page.comments.lastIndex) HorizontalDivider(Modifier.padding(start = 42.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                        if (page.continuation != null) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                    Button(onClick = { vm.loadMoreComments(expandedFor) }, shape = RoundedCornerShape(24.dp)) { Text("Load more") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            state.isLoading && state.shorts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
                        Text("Loading Shorts…", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            state.error != null && state.shorts.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = Color(0xFFFF3B30), modifier = Modifier.size(40.dp))
                            Text("Couldn't load Shorts", color = Color.White, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(state.error!!, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { vm.retry() }, shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) { Text("Retry") }
                        }
                    }
                }
            }
            state.shorts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No Shorts found — try search", color = Color.White.copy(alpha = 0.7f))
                }
            }
            else -> {
                VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val short = state.shorts[page]
                    val detail = state.details[short.videoId]
                    val isCurrent = page == pagerState.currentPage
                    ShortPage(
                        short = short,
                        detail = detail,
                        thumbQ = thumbQ,
                        avatarQ = avatarQ,
                        isCurrent = isCurrent,
                        onChannelClick = onChannelClick,
                        onCommentClick = { vm.setCommentsSheet(short.videoId) },
                        onShareClick = {
                            val url = detail?.canonicalUrl?.ifBlank { "https://www.youtube.com/watch?v=${short.videoId}" } ?: "https://www.youtube.com/watch?v=${short.videoId}"
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${detail?.title ?: short.title}\n$url")
                            }
                            try { ctx.startActivity(Intent.createChooser(send, "Share Short")) } catch (_: Exception) {}
                        }
                    )
                }
                // Top overlay — Wediyo Shorts + thin progress indicator for pagination status
                Box(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.14f)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("Shorts", color = Color.White, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold))
                                Text("• ${pagerState.currentPage + 1} / ${state.shorts.size}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                                if (state.isPaginating) {
                                    Spacer(Modifier.width(6.dp))
                                    CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color.White)
                                }
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.14f), modifier = Modifier.clickable { /* search */ }) {
                            Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(32.dp).padding(7.dp))
                        }
                    }
                }
                // Page indicator dots (right edge like TikTok)
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 4.dp)
                        .padding(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // minimal — only show when many shorts
                    if (state.shorts.size > 20) {
                        repeat(kotlin.math.min(5, state.shorts.size)) { idx ->
                            val active = idx == (pagerState.currentPage % 5)
                            Box(
                                Modifier
                                    .width(if (active) 4.dp else 3.dp)
                                    .height(if (active) 14.dp else 6.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(if (active) Color.White else Color.White.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortPage(
    short: com.teamshryne.wediyo.data.model.UiShort,
    detail: com.teamshryne.wediyo.data.model.UiVideoDetail?,
    thumbQ: String,
    avatarQ: String,
    isCurrent: Boolean = false,
    onChannelClick: (String) -> Unit,
    onCommentClick: () -> Unit,
    onShareClick: () -> Unit
) {
    val title = detail?.title?.ifBlank { short.title } ?: short.title
    val views = detail?.viewCountText?.ifBlank { short.views } ?: short.views
    val authorName = detail?.channelTitle?.ifBlank { detail?.author ?: "" } ?: ""
    val authorAvatar = detail?.channelAvatarUrl ?: ""
    val avatarsJson = detail?.channelAvatarsJson ?: "[]"
    val desc = detail?.shortDescription?.ifBlank { detail?.description ?: "" } ?: ""
    val likeText = detail?.likeCountText ?: ""
    val commentCount = detail?.commentsCountText ?: ""
    val duration = detail?.durationText ?: ""

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Fast playback for current page — Flow's gapless shorts buffer 1.5s/8s + VISIONOS direct URLs
        if (isCurrent && detail != null && (detail.formats.isNotEmpty() || detail.adaptiveFormats.isNotEmpty())) {
            androidx.compose.runtime.key(detail.videoId) {
                WediyoPlayer(detail = detail, isShorts = true, modifier = Modifier.fillMaxSize())
            }
        } else {
            // Thumbnail fallback while loading / for offscreen pages
            AsyncImage(
                model = bestThumbUrl(short.thumbsJson, short.thumbUrl, thumbQ),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.92f
            )
        }
        // Vignette gradients top & bottom for legibility
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(160.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.66f), Color.Transparent)))
        )
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(340.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f))))
        )

        // Right action column — TikTok style (avatar + like + comment + share)
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 10.dp, bottom = 96.dp)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Author avatar with follow ring
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 20.dp)) {
                AsyncImage(
                    model = bestThumbUrl(avatarsJson, authorAvatar, avatarQ),
                    contentDescription = authorName,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF222222))
                        .clickable(enabled = detail?.channelId?.isNotBlank() == true) { detail?.channelId?.let { onChannelClick(it) } },
                    contentScale = ContentScale.Crop
                )
                Surface(
                    shape = CircleShape, color = Color(0xFFFF2D55),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 8.dp)
                        .size(18.dp)
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = "Follow", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }

            ShortActionButton(icon = Icons.Filled.Favorite, label = likeText.ifBlank { "Like" }, tint = Color.White, bg = Color.White.copy(alpha = 0.14f))
            Spacer(Modifier.height(16.dp))
            ShortActionButton(icon = Icons.Filled.Email, label = commentCount.ifBlank { if (short.views.isNotBlank()) short.views else "Comment" }, onClick = onCommentClick)
            Spacer(Modifier.height(16.dp))
            ShortActionButton(icon = Icons.Filled.Share, label = "Share", onClick = onShareClick)
            Spacer(Modifier.height(16.dp))
            ShortActionButton(icon = Icons.Filled.MoreVert, label = "")
            if (duration.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.56f)) {
                    Text(duration, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        }

        // Bottom info — author + title/desc + views
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 76.dp, bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Author row
            if (authorName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clickable(enabled = detail?.channelId?.isNotBlank() == true) { detail?.channelId?.let { onChannelClick(it) } }) {
                    Text("@${authorName}", color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF3EA6FF), modifier = Modifier.size(14.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
                        Text("Subscribe", color = Color.Black, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            // Title
            Text(title, color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            // Description snippet (keep tags here if present — desc may contain #tags)
            if (desc.isNotBlank() && desc != title) {
                Text(
                    desc.take(160) + if (desc.length > 160) "…" else "",
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Views + published
            val meta = buildList {
                if (views.isNotBlank()) add(views)
                else if (detail != null && detail.viewCount > 0) add("${detail.viewCount} views")
                detail?.publishDate?.takeIf { it.isNotBlank() }?.let { add(it.take(12)) }
            }.joinToString(" • ")
            if (meta.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.14f)) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            Text(meta, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            // Sound / music bar like TikTok
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text("Original sound • ${authorName.ifBlank { "Wediyo" }}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }

        // Center play hint for non-current / thumbnail state only
        if (!isCurrent || detail == null) {
            Surface(
                modifier = Modifier.align(Alignment.Center).size(72.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.12f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        }
    }
}

@Composable
private fun ShortActionButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, bg: Color = Color.White.copy(alpha = 0.14f), onClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable(enabled = onClick != null) { onClick?.invoke() }) {
        Surface(shape = CircleShape, color = bg, modifier = Modifier.size(44.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
        if (label.isNotBlank()) Text(label.take(10), color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}
