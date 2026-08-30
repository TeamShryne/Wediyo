package com.teamshryne.wediyo.ui.screens.video

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.teamshryne.wediyo.data.model.UiComment
import com.teamshryne.wediyo.data.model.UiVideoDetail
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.ui.components.ChannelVideoListCard
import com.teamshryne.wediyo.ui.components.WediyoPlayer
import com.teamshryne.wediyo.util.bestThumbUrl
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
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
    var isFullscreen by remember { mutableStateOf(false) }
    val config = LocalConfiguration.current

    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(Unit) { settings.avatarQuality.collectLatest { avatarQ = it } }
    LaunchedEffect(videoId) { vm.load(videoId) }

    // Apply fullscreen orientation / insets
    LaunchedEffect(isFullscreen) {
        val activity = ctx as? Activity ?: return@LaunchedEffect
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (isFullscreen) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState.firstVisibleItemIndex, state.relatedContinuation) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 6) {
            if (state.relatedContinuation != null && !state.relatedLoading) vm.loadMoreRelated()
        }
    }

    // Fullscreen: only player
    if (isFullscreen && state.detail != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            WediyoPlayer(
                detail = state.detail!!,
                isShorts = false,
                modifier = Modifier.fillMaxSize(),
                isFullscreen = true,
                onBack = { isFullscreen = false },
                onFullscreenToggle = { isFullscreen = false }
            )
        }
        return
    }

    Scaffold(
        topBar = {
            // Flow hides topBar when fullscreen; here subtle CenterAlignedTopAppBar like before but more minimal
            CenterAlignedTopAppBar(
                title = { Text("Now watching", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        state.detail?.let { det ->
                            val url = det.canonicalUrl.ifBlank { "https://www.youtube.com/watch?v=${det.videoId}" }
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "${det.title}\n$url")
                                putExtra(Intent.EXTRA_SUBJECT, det.title)
                            }
                            try { ctx.startActivity(Intent.createChooser(send, "Share")) } catch (_: Exception) {}
                        }
                    }) { Icon(Icons.Outlined.Share, contentDescription = "Share") }
                    IconButton(onClick = { state.detail?.let { vm.setDetailsSheet(true) } }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Text("Loading…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f))) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                            Text("Couldn't load video", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.retry() }, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Retry") }
                                OutlinedButton(onClick = {
                                    val url = "https://www.youtube.com/watch?v=$videoId"
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                }, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Rounded.OpenInBrowser, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Open in YouTube") }
                            }
                        }
                    }
                }
            }
            state.detail != null -> {
                val d = state.detail!!

                // Sheets
                if (state.showDetailsSheet) {
                    FlowDescriptionSheet(d = d, expandedDesc = state.expandedDesc, onToggleDesc = { vm.toggleDesc() }, onDismiss = { vm.setDetailsSheet(false) })
                }
                if (state.showCommentsSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { vm.setCommentsSheet(false) },
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        dragHandle = { BottomSheetDefaults.DragHandle() }
                    ) {
                        FlowCommentsSheet(
                            countText = state.commentsCount ?: d.commentsCountText,
                            sortFilters = state.commentsSortFilters,
                            comments = state.comments,
                            loading = state.commentsLoading,
                            continuation = state.commentsContinuation,
                            avatarQ = avatarQ,
                            onSort = { vm.switchCommentsSort(it) },
                            onLoadMore = { vm.loadMoreComments() },
                            onReply = { cont, cb -> vm.loadReplies(cont) { cb(it) } }
                        )
                    }
                }

                val isWide = config.screenWidthDp >= 840
                if (isWide) {
                    // Tablet / foldable two-pane like Flow's WIDE mode
                    Row(Modifier.fillMaxSize().padding(pad)) {
                        LazyColumn(state = listState, modifier = Modifier.weight(0.60f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            item { FlowPlayerHero(d = d, isFullscreen = isFullscreen, onFullscreen = { isFullscreen = true }) }
                            item { FlowVideoInfoSection(videoDetail = d, ctx = ctx, avatarQ = avatarQ, likeCount = d.likeCount, viewCount = d.viewCount, onChannelClick = { onChannelClick(d.channelId) }, onShare = { shareVideo(ctx, d) }, onDownload = { vm.setDetailsSheet(true) }, onSave = { vm.setDetailsSheet(true) }, onDescriptionClick = { vm.setDetailsSheet(true) }) }
                            item { FlowVideoActionRowFlow(d = d, ctx = ctx, onLike = {}, onDislike = {}, onShare = { shareVideo(ctx, d) }, onSave = {}, onDownload = { vm.setDetailsSheet(true) }, onCopyLink = { copyLink(ctx, d, withTimestamp = false) }, onCopyLinkAtTime = { copyLink(ctx, d, withTimestamp = true) }) }
                            item { FlowCommentsPreview(comments = state.comments, countText = state.commentsCount ?: d.commentsCountText, avatarQ = avatarQ, onClick = { vm.setCommentsSheet(true) }) }
                        }
                        LazyColumn(modifier = Modifier.weight(0.40f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("Up next", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)); if (state.related.isNotEmpty()) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text("${state.related.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) } } }
                            items(state.related.size) { idx -> val v = state.related[idx]; Box(Modifier.padding(horizontal = 8.dp)) { ChannelVideoListCard(video = v, thumbQuality = thumbQ, onClick = { onVideoClick(v.id) }) } }
                            if (state.relatedLoading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(22.dp)) } }
                        }
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 28.dp)) {
                        item { FlowPlayerHero(d = d, isFullscreen = isFullscreen, onFullscreen = { isFullscreen = true }) }
                        item { FlowVideoInfoSection(videoDetail = d, ctx = ctx, avatarQ = avatarQ, likeCount = d.likeCount, viewCount = d.viewCount, onChannelClick = { onChannelClick(d.channelId) }, onShare = { shareVideo(ctx, d) }, onDownload = { vm.setDetailsSheet(true) }, onSave = { vm.setDetailsSheet(true) }, onDescriptionClick = { vm.setDetailsSheet(true) }) }
                        item { FlowVideoActionRowFlow(d = d, ctx = ctx, onLike = {}, onDislike = {}, onShare = { shareVideo(ctx, d) }, onSave = {}, onDownload = { vm.setDetailsSheet(true) }, onCopyLink = { copyLink(ctx, d, withTimestamp = false) }, onCopyLinkAtTime = { copyLink(ctx, d, withTimestamp = true) }) }
                        item { FlowCommentsPreview(comments = state.comments, countText = state.commentsCount ?: d.commentsCountText, avatarQ = avatarQ, onClick = { vm.setCommentsSheet(true) }) }
                        // Up next header
                        item {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Up next", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                                if (state.related.isNotEmpty()) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text("${state.related.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) }
                                Spacer(Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) { Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Text("For you", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)) } }
                            }
                        }
                        if (state.related.isNotEmpty()) {
                            items(state.related.size) { idx -> val v = state.related[idx]; Box(Modifier.padding(horizontal = 0.dp)) { ChannelVideoListCard(video = v, thumbQuality = thumbQ, onClick = { onVideoClick(v.id) }) } }
                            if (state.relatedLoading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(22.dp)) } }
                            else if (state.relatedContinuation != null) item { Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) { OutlinedButton(onClick = { vm.loadMoreRelated() }, shape = RoundedCornerShape(24.dp)) { Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Load more") } } }
                        } else {
                            item {
                                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                                    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Rounded.PlayCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp)); Text("No related videos yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
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
private fun FlowPlayerHero(d: UiVideoDetail, isFullscreen: Boolean, onFullscreen: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (isFullscreen) 0.dp else 0.dp)
            .background(Color.Black)
            .aspectRatio(16f / 9f)
    ) {
        WediyoPlayer(detail = d, isShorts = false, modifier = Modifier.fillMaxSize(), isFullscreen = isFullscreen, onFullscreenToggle = onFullscreen)
        if (d.isLive) {
            Surface(modifier = Modifier.align(Alignment.TopStart).padding(10.dp).statusBarsPadding(), shape = RoundedCornerShape(4.dp), color = Color(0xFFFF0000)) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                    Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp))
                }
            }
        }
    }
}

// ── Flow VideoInfoSection clone ────────────────────────────────────────────
@Composable
private fun FlowVideoInfoSection(
    videoDetail: UiVideoDetail,
    ctx: Context,
    avatarQ: String,
    viewCount: Long,
    likeCount: Long,
    onChannelClick: () -> Unit,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onSave: () -> Unit,
    onDescriptionClick: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        // Title – Flow uses titleLarge 20sp bold, maxLines from prefs, long-press copy
        Text(
            text = videoDetail.title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("title", videoDetail.title))
                    Toast.makeText(ctx, "Title copied", Toast.LENGTH_SHORT).show()
                }
            )
        )
        // views • date + ...more
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val views = videoDetail.viewCountText.ifBlank { if (viewCount > 0) formatCompact(viewCount) + " views" else "" }
            val date = videoDetail.publishDate.ifBlank { videoDetail.uploadDate }
            val text = buildString {
                if (views.isNotBlank()) append(views)
                if (date.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(date.take(20))
                }
            }
            if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("  …more", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.clickable { onDescriptionClick() })
        }
        Spacer(Modifier.height(10.dp))
        // Channel row – Flow's ChannelAvatarStack + SubscribeButton
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.weight(1f).clickable { onChannelClick() }, verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = bestThumbUrl(videoDetail.channelAvatarsJson, videoDetail.channelAvatarUrl, avatarQ),
                    contentDescription = videoDetail.channelTitle,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF222222)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(videoDetail.channelTitle.ifBlank { videoDetail.author }, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (videoDetail.channelHandle.isNotBlank()) Icon(Icons.Rounded.Verified, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    val subText = videoDetail.subscriberCountText
                    if (subText.isNotBlank()) Text(subText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            FlowSubscribeButton(isSubscribed = false, onSubscribe = onChannelClick)
        }
    }
}

@Composable
private fun FlowSubscribeButton(isSubscribed: Boolean, onSubscribe: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val bg = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f) else MaterialTheme.colorScheme.onBackground
    val fg = if (isSubscribed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface
    Box {
        Surface(onClick = { if (isSubscribed) expanded = true else onSubscribe() }, shape = RoundedCornerShape(18.dp), color = bg, modifier = Modifier.height(36.dp)) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (isSubscribed) {
                    Icon(Icons.Rounded.Notifications, null, modifier = Modifier.size(18.dp), tint = fg)
                    Spacer(Modifier.width(6.dp))
                    Text("Subscribed", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = fg)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(16.dp), tint = fg)
                } else Text("Subscribe", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = fg)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Unsubscribe") }, leadingIcon = { Icon(Icons.Rounded.PersonRemove, null) }, onClick = { expanded = false; onSubscribe() })
        }
    }
}

@Composable
private fun FlowVideoActionRowFlow(
    d: UiVideoDetail,
    ctx: Context,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyLinkAtTime: () -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        item {
            // Segmented like/dislike – Flow's pill with divider
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f), modifier = Modifier.height(36.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clickable { onLike() }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (d.likeCountText.isNotBlank() || d.likeCount > 0) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(6.dp))
                        Text(if (d.likeCountText.isNotBlank()) d.likeCountText else if (d.likeCount > 0) formatCompact(d.likeCount) else "Like", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                    }
                    Box(Modifier.width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)))
                    Row(Modifier.clickable { onDislike() }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ThumbDown, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        item { FlowActionChip(icon = Icons.Outlined.BookmarkBorder, label = "Save", onClick = onSave) }
        item { FlowActionChip(icon = Icons.Outlined.Download, label = "Download", onClick = onDownload) }
        item { FlowActionChip(icon = Icons.Outlined.Headphones, label = "Background", onClick = {}) }
        item { FlowActionChip(icon = Icons.Outlined.Share, label = "Share", onClick = onShare) }
        item { FlowActionChip(icon = Icons.Outlined.Link, label = "Copy link", onClick = onCopyLink) }
        item { FlowActionChip(icon = Icons.Outlined.Timer, label = "Copy at time", onClick = onCopyLinkAtTime) }
    }
}

@Composable
private fun FlowActionChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f), modifier = Modifier.height(36.dp)) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun FlowCommentsPreview(comments: List<UiComment>, countText: String, avatarQ: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Comments", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                if (countText.isNotBlank()) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text(countText, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) }
            }
            if (comments.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = bestThumbUrl("", comments.first().author.avatar, avatarQ), contentDescription = null, modifier = Modifier.size(24.dp).clip(CircleShape).background(Color.Gray), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(10.dp))
                    Text(comments.first().content, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
            } else {
                Spacer(Modifier.height(6.dp))
                Text("Add a comment…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowDescriptionSheet(d: UiVideoDetail, expandedDesc: Boolean, onToggleDesc: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerLow, dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(d.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            val meta = buildList {
                if (d.viewCountText.isNotBlank()) add(d.viewCountText) else if (d.viewCount > 0) add("${formatCompact(d.viewCount)} views")
                if (d.publishDate.isNotBlank()) add(d.publishDate) else if (d.uploadDate.isNotBlank()) add(d.uploadDate)
                if (d.category.isNotBlank()) add(d.category)
                if (d.isLiveContent) add("Live")
            }.joinToString(" • ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val desc = d.description.ifBlank { d.shortDescription }
            if (desc.isNotBlank()) {
                ElevatedCard(shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)) {
                    Column(Modifier.fillMaxWidth().clickable { onToggleDesc() }.padding(16.dp).animateContentSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Description, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Description", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) { Text(if (expandedDesc) "Show less" else "Show more", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expandedDesc) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowStatChip("Views", d.viewCountText.ifBlank { if (d.viewCount > 0) formatCompact(d.viewCount) else "—" }, Icons.Rounded.Visibility, Modifier.weight(1f))
                FlowStatChip("Likes", d.likeCountText.ifBlank { d.likeCount.takeIf { it > 0 }?.let { formatCompact(it) } ?: "—" }, Icons.Rounded.ThumbUp, Modifier.weight(1f))
                FlowStatChip("Duration", d.durationText.ifBlank { formatDuration(d.lengthSeconds) }, Icons.Rounded.Schedule, Modifier.weight(1f))
            }
            if (d.captionTracks.isNotEmpty()) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.ClosedCaption, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Captions • ${d.captionTracks.size}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
                        Spacer(Modifier.height(10.dp))
                        d.captionTracks.take(5).forEach { ct ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(28.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(ct.languageCode.take(2).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) } }
                                Spacer(Modifier.width(10.dp))
                                Text(ct.name.ifBlank { ct.languageCode }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(ct.kind.ifBlank { "standard" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowStatChip(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FlowCommentsSheet(
    countText: String,
    sortFilters: List<com.teamshryne.wediyo.data.model.UiCommentSortFilter>,
    comments: List<UiComment>,
    loading: Boolean,
    continuation: String?,
    avatarQ: String,
    onSort: (String) -> Unit,
    onLoadMore: () -> Unit,
    onReply: (String, (List<UiComment>) -> Unit) -> Unit
) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Comments", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                if (countText.isNotBlank()) Text(countText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) { Icon(Icons.Rounded.Close, null, modifier = Modifier.size(24.dp).padding(4.dp)) }
        }
        Spacer(Modifier.height(12.dp))
        if (sortFilters.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sortFilters.take(4).size) { idx ->
                    val sf = sortFilters[idx]
                    FilterChip(selected = sf.selected, onClick = { if (!sf.selected) onSort(sf.continuationToken) }, label = { Text(sf.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (sf.selected) FontWeight.Bold else FontWeight.Medium)) }, shape = RoundedCornerShape(20.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (comments.isEmpty() && loading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (comments.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.ChatBubbleOutline, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No comments yet", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Be the first to share what you think", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(comments.size) { idx ->
                    val c = comments[idx]
                    FlowCommentCard(comment = c, avatarQ = avatarQ, onReply = onReply)
                    if (idx < comments.lastIndex) HorizontalDivider(Modifier.padding(start = 48.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
                if (loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(22.dp)) } }
                else if (continuation != null) item { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { Button(onClick = onLoadMore, shape = RoundedCornerShape(24.dp)) { Icon(Icons.Rounded.ExpandMore, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Load more") } } }
            }
        }
    }
}

@Composable
private fun FlowCommentCard(comment: UiComment, avatarQ: String, onReply: (String, (List<UiComment>) -> Unit) -> Unit) {
    var expandedReplies by remember { mutableStateOf<List<UiComment>?>(null) }
    var repliesLoading by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        AsyncImage(model = bestThumbUrl("", comment.author.avatar, avatarQ), contentDescription = comment.author.name, modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF222222)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(comment.author.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (comment.author.isVerified) Icon(Icons.Rounded.Verified, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                if (comment.author.isCreator) Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF3EA6FF)) { Text("Creator", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) }
                Text("• ${comment.publishedTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(comment.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                    Icon(Icons.Rounded.ThumbUp, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (comment.likeCount.isNotBlank()) Text(comment.likeCount, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (comment.replyCount.isNotBlank() && comment.repliesContinuation.isNotBlank()) {
                    Text(
                        comment.replyCount, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                            if (expandedReplies == null && !repliesLoading) {
                                repliesLoading = true
                                onReply(comment.repliesContinuation) { replies -> expandedReplies = replies; repliesLoading = false }
                            } else if (expandedReplies != null) expandedReplies = null
                        }.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)).padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                if (repliesLoading) CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
            if (expandedReplies != null) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        expandedReplies!!.forEach { r ->
                            Row(verticalAlignment = Alignment.Top) {
                                AsyncImage(model = bestThumbUrl("", r.author.avatar, avatarQ), contentDescription = r.author.name, modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF222222)), contentScale = ContentScale.Crop)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(r.author.name, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1)
                                        Text("• ${r.publishedTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(r.content, style = MaterialTheme.typography.bodySmall, maxLines = 6, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                        if (expandedReplies!!.isEmpty()) Text("No replies", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ── helpers ─────────────────────────────────────────────────────────────────
private fun shareVideo(ctx: Context, d: UiVideoDetail) {
    val url = d.canonicalUrl.ifBlank { "https://www.youtube.com/watch?v=${d.videoId}" }
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${d.title}\n$url"); putExtra(Intent.EXTRA_SUBJECT, d.title) }
    try { ctx.startActivity(Intent.createChooser(send, "Share video")) } catch (_: Exception) {}
}

private fun copyLink(ctx: Context, d: UiVideoDetail, withTimestamp: Boolean) {
    val base = d.canonicalUrl.ifBlank { "https://www.youtube.com/watch?v=${d.videoId}" }
    val url = if (withTimestamp) "$base&t=0s" else base
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("link", url))
    Toast.makeText(ctx, if (withTimestamp) "Link with timestamp copied" else "Link copied", Toast.LENGTH_SHORT).show()
}

private fun formatDuration(secs: Long): String {
    if (secs <= 0) return "—"
    val h = secs / 3600
    val m = (secs % 3600) / 60
    val s = secs % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}

private fun formatCompact(n: Long): String {
    return when {
        n >= 1_000_000_000 -> String.format("%.1fB", n / 1_000_000_000.0).replace(".0B", "B")
        n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0).replace(".0M", "M")
        n >= 1_000 -> String.format("%.1fK", n / 1_000.0).replace(".0K", "K")
        else -> n.toString()
    }
}
