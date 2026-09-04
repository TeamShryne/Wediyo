package com.teamshryne.wediyo.ui.screens.video

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
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
import com.teamshryne.wediyo.data.local.LibraryRepository
import com.teamshryne.wediyo.data.model.UiComment
import com.teamshryne.wediyo.data.model.UiVideo
import com.teamshryne.wediyo.data.model.UiVideoDetail
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.player.SleepTimerManager
import com.teamshryne.wediyo.ui.components.ChannelVideoListCard
import com.teamshryne.wediyo.ui.components.SleepTimerSheet
import com.teamshryne.wediyo.ui.components.SubscribeButton
import com.teamshryne.wediyo.ui.components.VideoActionsSheet
import com.teamshryne.wediyo.ui.components.WediyoPlayer
import com.teamshryne.wediyo.util.bestThumbUrl
import com.teamshryne.wediyo.util.rememberHaptics
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    var showSleepSheet by remember { mutableStateOf(false) }
    var showSaveSheet by remember { mutableStateOf(false) }
    val libScope = rememberCoroutineScope()
    val libHaptics = rememberHaptics()
    val config = LocalConfiguration.current
    LaunchedEffect(Unit) { try { SleepTimerManager.init(ctx) } catch (_: Exception) {} }
    LaunchedEffect(Unit) { try { LibraryRepository.init(ctx) } catch (_: Exception) {} }

    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(Unit) { settings.avatarQuality.collectLatest { avatarQ = it } }
    LaunchedEffect(videoId) { vm.load(videoId) }

    // Library: cache + history (local-only, respects pause toggle)
    val openedVideoId = state.detail?.videoId
    LaunchedEffect(openedVideoId) {
        val d = state.detail ?: return@LaunchedEffect
        try {
            LibraryRepository.cacheVideoDetail(d)
            if (!settings.historyPaused.first()) {
                LibraryRepository.logHistory(videoId = d.videoId, channelId = d.channelId, source = "video")
            }
        } catch (_: Exception) {}
    }
    val isLikedLocal by remember(openedVideoId) {
        if (openedVideoId.isNullOrBlank()) kotlinx.coroutines.flow.flowOf(false)
        else try { LibraryRepository.isLiked(openedVideoId) } catch (_: Exception) { kotlinx.coroutines.flow.flowOf(false) }
    }.collectAsState(initial = false)

    // Apply fullscreen orientation / insets - no recreate due to configChanges
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

    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    val listState = rememberLazyListState()
    LaunchedEffect(listState.firstVisibleItemIndex, state.relatedContinuation) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 6) {
            if (state.relatedContinuation != null && !state.relatedLoading) vm.loadMoreRelated()
        }
    }

    // Fullscreen overlay - reuses same PlayerManager instance, no restart
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

    // Normal - sticky player pinned at top, scroll content below
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Text("Loading…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f))) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                            Text("Couldn't load video", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { vm.retry() }, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Retry") }
                                OutlinedButton(onClick = {
                                    val url = "https://www.youtube.com/watch?v=$videoId"
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                                }, shape = RoundedCornerShape(12.dp)) { Icon(Icons.Filled.OpenInBrowser, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Open in YouTube") }
                            }
                        }
                    }
                }
            }
            state.detail != null -> {
                val d = state.detail!!

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
                    Row(Modifier.fillMaxSize()) {
                        Column(Modifier.weight(0.60f).fillMaxHeight()) {
                            // Sticky player - pinned
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .background(Color.Black)
                                    .aspectRatio(16f / 9f)
                            ) {
                                WediyoPlayer(detail = d, isShorts = false, modifier = Modifier.fillMaxSize(), isFullscreen = false, onFullscreenToggle = { isFullscreen = true })
                            }
                            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                                item { FlowVideoInfoSection(videoDetail = d, ctx = ctx, avatarQ = avatarQ, likeCount = d.likeCount, viewCount = d.viewCount, onChannelClick = { onChannelClick(d.channelId) }, onShare = { shareVideo(ctx, d) }, onDownload = { vm.setDetailsSheet(true) }, onSave = { vm.setDetailsSheet(true) }, onDescriptionClick = { vm.setDetailsSheet(true) }) }
                                item { FlowVideoActionRowFlow(d = d, ctx = ctx, isLiked = isLikedLocal, onLike = { libHaptics.toggle(!isLikedLocal); libScope.launch { try { LibraryRepository.toggleLike(d) } catch (_: Exception) {} } }, onDislike = {}, onShare = { shareVideo(ctx, d) }, onSave = { libHaptics.longPress(); showSaveSheet = true }, onDownload = { vm.setDetailsSheet(true) }, onCopyLink = { copyLink(ctx, d, withTimestamp = false) }, onCopyLinkAtTime = { copyLink(ctx, d, withTimestamp = true) }, onSleepTimer = { showSleepSheet = true }) }
                                item { FlowCommentsPreview(comments = state.comments, countText = state.commentsCount ?: d.commentsCountText, avatarQ = avatarQ, onClick = { vm.setCommentsSheet(true) }) }
                            }
                        }
                        LazyColumn(modifier = Modifier.weight(0.40f).fillMaxHeight(), contentPadding = PaddingValues(bottom = 24.dp)) {
                            item { Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text("Up next", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)); if (state.related.isNotEmpty()) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text("${state.related.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) } } }
                            items(state.related.size) { idx -> val v = state.related[idx]; Box(Modifier.padding(horizontal = 8.dp)) { ChannelVideoListCard(video = v, thumbQuality = thumbQ, onClick = { onVideoClick(v.id) }) } }
                            if (state.relatedLoading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(22.dp)) } }
                        }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        // Sticky player - pinned, starts right after status bar
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .background(Color.Black)
                                .aspectRatio(16f / 9f)
                        ) {
                            WediyoPlayer(detail = d, isShorts = false, modifier = Modifier.fillMaxSize(), isFullscreen = false, onFullscreenToggle = { isFullscreen = true })
                            if (d.isLive) {
                                androidx.compose.foundation.layout.Box(modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFFF0000)) {
                                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                                            Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 11.sp))
                                        }
                                    }
                                }
                            }
                        }
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                            item { FlowVideoInfoSection(videoDetail = d, ctx = ctx, avatarQ = avatarQ, likeCount = d.likeCount, viewCount = d.viewCount, onChannelClick = { onChannelClick(d.channelId) }, onShare = { shareVideo(ctx, d) }, onDownload = { vm.setDetailsSheet(true) }, onSave = { vm.setDetailsSheet(true) }, onDescriptionClick = { vm.setDetailsSheet(true) }) }
                            item { FlowVideoActionRowFlow(d = d, ctx = ctx, isLiked = isLikedLocal, onLike = { libHaptics.toggle(!isLikedLocal); libScope.launch { try { LibraryRepository.toggleLike(d) } catch (_: Exception) {} } }, onDislike = {}, onShare = { shareVideo(ctx, d) }, onSave = { libHaptics.longPress(); showSaveSheet = true }, onDownload = { vm.setDetailsSheet(true) }, onCopyLink = { copyLink(ctx, d, withTimestamp = false) }, onCopyLinkAtTime = { copyLink(ctx, d, withTimestamp = true) }, onSleepTimer = { showSleepSheet = true }) }
                            item { FlowCommentsPreview(comments = state.comments, countText = state.commentsCount ?: d.commentsCountText, avatarQ = avatarQ, onClick = { vm.setCommentsSheet(true) }) }
                            item {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Up next", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                                    if (state.related.isNotEmpty()) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) { Text("${state.related.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) }
                                    Spacer(Modifier.weight(1f))
                                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) { Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Text("For you", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)) } }
                                }
                            }
                            if (state.related.isNotEmpty()) {
                                items(state.related.size) { idx -> val v = state.related[idx]; Box(Modifier.padding(horizontal = 0.dp)) { ChannelVideoListCard(video = v, thumbQuality = thumbQ, onClick = { onVideoClick(v.id) }) } }
                                if (state.relatedLoading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(22.dp)) } }
                                else if (state.relatedContinuation != null) item { Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) { OutlinedButton(onClick = { vm.loadMoreRelated() }, shape = RoundedCornerShape(24.dp)) { Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Load more") } } }
                            } else {
                                item {
                                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                                        Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Filled.PlayCircle, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp)); Text("No related videos yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                                    }
                                }
                            }
                        }
                    }
                }
                if (showSleepSheet) {
                    SleepTimerSheet(onDismiss = { showSleepSheet = false })
                }
                if (showSaveSheet) {
                    VideoActionsSheet(
                        video = UiVideo(
                            id = d.videoId,
                            title = d.title,
                            author = d.author.ifBlank { d.channelTitle },
                            channelId = d.channelId,
                            thumbnailUrl = d.thumbnailUrl,
                            thumbnailsJson = d.thumbnailsJson,
                            avatarUrl = d.channelAvatarUrl,
                            avatarsJson = d.channelAvatarsJson,
                            viewCountText = d.viewCountText,
                            publishedText = d.uploadDate.ifBlank { d.publishDate },
                            durationText = d.durationText,
                            isLive = d.isLive,
                            badges = emptyList(),
                            description = d.shortDescription
                        ),
                        onDismiss = { showSaveSheet = false }
                    )
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
@OptIn(ExperimentalFoundationApi::class)
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
                        if (videoDetail.channelHandle.isNotBlank()) Icon(Icons.Filled.Verified, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    val subText = videoDetail.subscriberCountText
                    if (subText.isNotBlank()) Text(subText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            Spacer(Modifier.width(8.dp))
            SubscribeButton(
                channelId = videoDetail.channelId,
                title = videoDetail.channelTitle.ifBlank { videoDetail.author },
                handle = videoDetail.channelHandle,
                avatarUrl = videoDetail.channelAvatarUrl,
                avatarsJson = videoDetail.channelAvatarsJson,
                subsText = videoDetail.subscriberCountText
            )
        }
    }
}

@Composable
private fun FlowVideoActionRowFlow(
    d: UiVideoDetail,
    ctx: Context,
    isLiked: Boolean = false,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDownload: () -> Unit,
    onCopyLink: () -> Unit,
    onCopyLinkAtTime: () -> Unit,
    onSleepTimer: () -> Unit = {}
) {
    val sleep by SleepTimerManager.state.collectAsState()
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        // Sleep timer FIRST — most discoverable, shows remaining when active
        item {
            val isActive = sleep.isActive
            val label = when {
                !isActive -> "Sleep timer"
                sleep.mode == SleepTimerManager.Mode.END_OF_VIDEO -> "Sleep • end"
                else -> "Sleep ${formatDurationMs(sleep.remainingMs)}"
            }
            Surface(
                onClick = onSleepTimer,
                shape = RoundedCornerShape(18.dp),
                color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                modifier = Modifier.height(36.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isActive) Icons.Filled.Bedtime else Icons.Outlined.Timer,
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium), color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f), modifier = Modifier.height(36.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.clickable { onLike() }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isLiked) Icons.Filled.ThumbUp else if (d.likeCountText.isNotBlank() || d.likeCount > 0) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp, null, modifier = Modifier.size(18.dp), tint = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(6.dp))
                        Text(if (isLiked) "Liked" else if (d.likeCountText.isNotBlank()) d.likeCountText else if (d.likeCount > 0) formatCompact(d.likeCount) else "Like", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium), color = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
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
                            Icon(Icons.Filled.Description, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
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
                FlowStatChip("Views", d.viewCountText.ifBlank { if (d.viewCount > 0) formatCompact(d.viewCount) else "—" }, Icons.Filled.Visibility, Modifier.weight(1f))
                FlowStatChip("Likes", d.likeCountText.ifBlank { d.likeCount.takeIf { it > 0 }?.let { formatCompact(it) } ?: "—" }, Icons.Filled.ThumbUp, Modifier.weight(1f))
                FlowStatChip("Duration", d.durationText.ifBlank { formatDuration(d.lengthSeconds) }, Icons.Filled.Schedule, Modifier.weight(1f))
            }
            if (d.captionTracks.isNotEmpty()) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.Subtitles, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Captions • ${d.captionTracks.size}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
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
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)) { Icon(Icons.Filled.Close, null, modifier = Modifier.size(24.dp).padding(4.dp)) }
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
                            Icon(Icons.Filled.ChatBubbleOutline, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                else if (continuation != null) item { Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { Button(onClick = onLoadMore, shape = RoundedCornerShape(24.dp)) { Icon(Icons.Filled.ExpandMore, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Load more") } } }
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
                if (comment.author.isVerified) Icon(Icons.Filled.Verified, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                if (comment.author.isCreator) Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF3EA6FF)) { Text("Creator", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) }
                Text("• ${comment.publishedTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(comment.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)).padding(horizontal = 9.dp, vertical = 5.dp)) {
                    Icon(Icons.Filled.ThumbUp, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun formatDurationMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return if (m >= 60) String.format("%d:%02d:%02d", m / 60, m % 60, sec) else String.format("%d:%02d", m, sec)
}

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
