package com.teamshryne.wediyo.ui.screens.shorts

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.ui.components.WediyoPlayer
import com.teamshryne.wediyo.util.bestThumbUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private const val SHORTS_SHEET_HEIGHT_FRACTION = 0.72f

@OptIn(ExperimentalMaterial3Api::class, androidx.media3.common.util.UnstableApi::class)
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
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { settings.thumbQuality.collectLatest { thumbQ = it } }
    LaunchedEffect(Unit) { settings.avatarQuality.collectLatest { avatarQ = it } }
    LaunchedEffect(initialVideoId) { vm.loadInitial(initialVideoId) }

    val pagerState = rememberPagerState(initialPage = 0) { state.shorts.size }

    // Prefetch & pagination – Flow-style: prefetch next 2, paginate near end
    LaunchedEffect(pagerState.currentPage, state.shorts.size, state.continuation) {
        val idx = pagerState.currentPage
        if (state.shorts.isNotEmpty() && idx in state.shorts.indices) {
            vm.fetchDetail(state.shorts[idx].videoId)
            if (idx + 1 < state.shorts.size) vm.fetchDetail(state.shorts[idx + 1].videoId)
            if (idx + 2 < state.shorts.size) vm.fetchDetail(state.shorts[idx + 2].videoId)
        }
        if (state.shorts.isNotEmpty() && idx >= state.shorts.size - 4) {
            state.continuation?.let { vm.loadMoreContinuation(it) }
        }
    }

    // Sheet state – Flow uses Follow-inset shrinking
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showDescriptionSheet by remember { mutableStateOf(false) }
    var sheetProgress by remember { mutableStateOf(0f) } // 0 closed, 1 open
    val expandedFor = state.expandedCommentsFor
    // derived sheet open for per-page shrink
    val anySheetOpen = showCommentsSheet || showDescriptionSheet || expandedFor != null

    // Track settled page for auto-advance defer while sheet open
    var deferredAdvance by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val canShrinkReel = maxHeight > maxWidth
        val sheetExpandedHeight = (maxHeight * SHORTS_SHEET_HEIGHT_FRACTION).takeIf { canShrinkReel }
        val density = LocalDensity.current
        val sheetExpandedPx = with(density) { (sheetExpandedHeight ?: 0.dp).toPx() }

        // ── Main pager ───────────────────────────────────────────────────
        when {
            state.isLoading && state.shorts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
                        Text("Loading Shorts…", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.error != null && state.shorts.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = Color.White, modifier = Modifier.size(40.dp))
                        Text(state.error ?: "Failed to load", color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        FilledTonalButton(onClick = { vm.retry() }) { Text("Retry") }
                    }
                }
            }
            state.shorts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No Shorts found", color = Color.White.copy(alpha = 0.7f)) }
            }
            else -> {
                // page index tracking
                LaunchedEffect(pagerState.currentPage) {
                    // Flow's updateCurrentIndex
                    // also handle deferred auto-advance
                    if (anySheetOpen) deferredAdvance = true
                }
                LaunchedEffect(anySheetOpen) {
                    if (!anySheetOpen && deferredAdvance) {
                        deferredAdvance = false
                        // optional auto-next could be triggered here
                    }
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount = 1,
                    key = { state.shorts[it].videoId }
                ) { page ->
                    val short = state.shorts[page]
                    val detail = state.details[short.videoId]
                    val isCurrent = page == pagerState.currentPage
                    // shrink when sheet open and this is current page (Flow's shortsSheetInset)
                    val shrinkFraction = if (canShrinkReel && isCurrent && anySheetOpen) sheetProgress else 0f
                    val scale = 1f - shrinkFraction * 0.08f
                    val translateY = -sheetExpandedPx * shrinkFraction * 0.45f

                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = scale; scaleY = scale; translationY = translateY }
                            .clip(if (shrinkFraction > 0) RoundedCornerShape(16.dp * shrinkFraction) else RoundedCornerShape(0.dp))
                    ) {
                        ShortPageFlow(
                            short = short,
                            detail = detail,
                            thumbQ = thumbQ,
                            avatarQ = avatarQ,
                            isCurrent = isCurrent,
                            pageIndex = page,
                            sheetOpen = anySheetOpen,
                            onChannelClick = onChannelClick,
                            onCommentClick = {
                                vm.setCommentsSheet(short.videoId)
                                showCommentsSheet = false
                                // also trigger legacy expandedFor
                            },
                            onDescriptionClick = {
                                scope.launch { vm.fetchDetail(short.videoId) }
                                showDescriptionSheet = true
                            },
                            onCommentsClickFlow = {
                                vm.setCommentsSheet(short.videoId)
                            },
                            onShareClick = {
                                val url = detail?.canonicalUrl?.ifBlank { "https://www.youtube.com/watch?v=${short.videoId}" } ?: "https://www.youtube.com/watch?v=${short.videoId}"
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${detail?.title ?: short.title}\n$url")
                                }
                                try { ctx.startActivity(Intent.createChooser(send, "Share Short")) } catch (_: Exception) {}
                            },
                            onLikeClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                scope.launch { snackbarHostState.showSnackbar("Added to liked shorts") }
                            },
                            onVideoEnded = {
                                scope.launch {
                                    if (page < pagerState.pageCount - 1) pagerState.animateScrollToPage(page + 1)
                                }
                            }
                        )
                    }
                }

                if (state.isPaginating) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // ── Top bar – Flow's ShortsTopBar (overlay) ─────────────────────
        if (state.shorts.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Flow shows "Shorts" title unless source != Feed then back button
                if (initialVideoId != null) {
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.14f), modifier = Modifier.clickable { /* back via nav */ }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(36.dp).padding(8.dp))
                    }
                } else {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.14f)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("Shorts", color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 15.sp))
                            Text("• ${pagerState.currentPage + 1} / ${state.shorts.size}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                            if (state.isPaginating) {
                                Spacer(Modifier.width(6.dp))
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = Color.White)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.14f)) {
                        Icon(Icons.Filled.Search, contentDescription = "Search", tint = Color.White, modifier = Modifier.size(36.dp).padding(8.dp))
                    }
                }
            }
        }

        // ── Side dots indicator (like TikTok) – subtle ───────────────────
        if (state.shorts.size > 8) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).padding(bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(minOf(5, state.shorts.size)) { idx ->
                    val active = idx == (pagerState.currentPage % 5)
                    Box(
                        Modifier.width(if (active) 3.dp else 2.dp).height(if (active) 14.dp else 6.dp).clip(RoundedCornerShape(20.dp)).background(if (active) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }

        // ── Comments sheet – FlowCommentsBottomSheet clone ───────────────
        val expandedDetail = expandedFor?.let { state.details[it] }
        val expandedPage = expandedFor?.let { state.comments[it] }
        if (expandedFor != null) {
            ModalBottomSheet(
                onDismissRequest = { vm.setCommentsSheet(null) },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                FlowShortCommentsSheet(
                    videoId = expandedFor,
                    detail = expandedDetail,
                    page = expandedPage,
                    avatarQ = avatarQ,
                    expandedHeight = sheetExpandedHeight,
                    onProgress = { sheetProgress = it },
                    onLoadMore = { vm.loadMoreComments(expandedFor) },
                    onDismiss = { vm.setCommentsSheet(null) }
                )
            }
            LaunchedEffect(Unit) { sheetProgress = 0f }
            DisposableEffect(Unit) { onDispose { sheetProgress = 0f } }
        }

        if (showCommentsSheet && state.shorts.isNotEmpty()) {
            val idx = pagerState.currentPage.coerceIn(0, state.shorts.size - 1)
            val sid = state.shorts[idx].videoId
            val det = state.details[sid]
            val page = state.comments[sid]
            ModalBottomSheet(
                onDismissRequest = { showCommentsSheet = false; sheetProgress = 0f },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                FlowShortCommentsSheet(
                    videoId = sid,
                    detail = det,
                    page = page,
                    avatarQ = avatarQ,
                    expandedHeight = sheetExpandedHeight,
                    onProgress = { sheetProgress = it },
                    onLoadMore = { vm.loadMoreComments(sid) },
                    onDismiss = { showCommentsSheet = false; sheetProgress = 0f }
                )
            }
        }

        if (showDescriptionSheet && state.shorts.isNotEmpty()) {
            val idx = pagerState.currentPage.coerceIn(0, state.shorts.size - 1)
            val short = state.shorts[idx]
            val detail = state.details[short.videoId]
            ModalBottomSheet(
                onDismissRequest = { showDescriptionSheet = false; sheetProgress = 0f },
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                FlowShortDescriptionSheet(
                    short = short,
                    detail = detail,
                    avatarQ = avatarQ,
                    onProgress = { sheetProgress = it },
                    onDismiss = { showDescriptionSheet = false; sheetProgress = 0f }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp)
        ) { data ->
            Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface, shape = MaterialTheme.shapes.medium)
        }
    }
}

@Composable
private fun ShortPageFlow(
    short: com.teamshryne.wediyo.data.model.UiShort,
    detail: com.teamshryne.wediyo.data.model.UiVideoDetail?,
    thumbQ: String,
    avatarQ: String,
    isCurrent: Boolean,
    pageIndex: Int,
    sheetOpen: Boolean,
    onChannelClick: (String) -> Unit,
    onCommentClick: () -> Unit,
    onDescriptionClick: () -> Unit,
    onCommentsClickFlow: () -> Unit,
    onShareClick: () -> Unit,
    onLikeClick: () -> Unit,
    onVideoEnded: () -> Unit
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

    var showPauseIndicator by remember { mutableStateOf(false) }
    var isPlayingOverlay by remember { mutableStateOf(true) }
    var showLikeAnim by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }
    var hasStarted by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isCurrent) { if (!isCurrent) { showPauseIndicator = false; isFastForwarding = false } }
    LaunchedEffect(showPauseIndicator) { if (showPauseIndicator) { delay(600); showPauseIndicator = false } }
    LaunchedEffect(showLikeAnim) { if (showLikeAnim) { delay(800); showLikeAnim = false } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Player or thumbnail
        if (isCurrent && detail != null && (detail.formats.isNotEmpty() || detail.adaptiveFormats.isNotEmpty())) {
            key(detail.videoId) {
                WediyoPlayer(detail = detail, isShorts = true, modifier = Modifier.fillMaxSize())
            }
            LaunchedEffect(detail.videoId) { hasStarted = true }
        } else {
            AsyncImage(
                model = bestThumbUrl(short.thumbsJson, short.thumbUrl, thumbQ),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.96f
            )
            if (!hasStarted) {
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.12f), Color.Transparent))))
            }
        }

        // Ambient dim gradients (Flow's VideoAmbientBackground simplified)
        Box(Modifier.fillMaxWidth().align(Alignment.TopCenter).height(180.dp).background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent))))
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(380.dp).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f)))))

        // Fullscreen tap / gestures layer – Flow handles center tap, double-tap like, long-press 2x
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(isCurrent, sheetOpen) {
                    detectTapGestures(
                        onTap = { offset ->
                            val isCenter = offset.x in (size.width * 0.25f)..(size.width * 0.75f) && offset.y in (size.height * 0.30f)..(size.height * 0.70f)
                            if (isCenter) {
                                showPauseIndicator = true
                                isPlayingOverlay = !isPlayingOverlay
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        onDoubleTap = {
                            showLikeAnim = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLikeClick()
                        },
                        onLongPress = {
                            isFastForwarding = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onPress = {
                            try { awaitRelease() } finally { if (isFastForwarding) isFastForwarding = false }
                        }
                    )
                }
        )

        // 2x indicator
        AnimatedVisibility(visible = isFastForwarding, enter = slideInVertically { -it / 2 } + fadeIn(), exit = slideOutVertically { -it / 2 } + fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 88.dp)) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color.Black.copy(alpha = 0.72f)) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.FastForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("2× speed", color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        // Pause overlay (center)
        AnimatedVisibility(
            visible = showPauseIndicator && isCurrent,
            enter = scaleIn(initialScale = 0.6f) + fadeIn(),
            exit = scaleOut(targetScale = 1.2f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(Modifier.size(72.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(if (isPlayingOverlay) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
            }
        }

        // Like heart animation (double-tap)
        AnimatedVisibility(
            visible = showLikeAnim,
            enter = scaleIn(initialScale = 0.3f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut(targetScale = 1.4f, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.Red, modifier = Modifier.size(110.dp))
        }

        // ── Right action column – Flow's ShortsOverlay (avatar + like + comment + share + more) ──
        Column(
            Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, bottom = 96.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar with follow
            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(bottom = 18.dp)) {
                AsyncImage(
                    model = bestThumbUrl(avatarsJson, authorAvatar, avatarQ),
                    contentDescription = authorName,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF222222)).clickable(enabled = detail?.channelId?.isNotBlank() == true) { detail?.channelId?.let { onChannelClick(it) } },
                    contentScale = ContentScale.Crop
                )
                Surface(shape = CircleShape, color = Color(0xFFFF2D55), modifier = Modifier.align(Alignment.BottomCenter).offset(y = 8.dp).size(18.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp)) }
                }
            }
            ShortActionButtonFlow(icon = Icons.Filled.Favorite, label = likeText.ifBlank { "Like" }, onClick = onLikeClick, isLiked = false)
            Spacer(Modifier.height(14.dp))
            ShortActionButtonFlow(icon = Icons.Filled.ChatBubble, label = commentCount.ifBlank { "Comment" }, onClick = onCommentClick)
            Spacer(Modifier.height(14.dp))
            ShortActionButtonFlow(icon = Icons.Filled.Share, label = "Share", onClick = onShareClick)
            Spacer(Modifier.height(14.dp))
            ShortActionButtonFlow(icon = Icons.Filled.MoreVert, label = "")
            Spacer(Modifier.height(10.dp))
            if (duration.isNotBlank()) {
                Surface(shape = RoundedCornerShape(6.dp), color = Color.Black.copy(alpha = 0.56f)) { Text(duration, color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) }
            }
        }

        // ── Bottom info – Flow's Shorts overlay text with shadow ──
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 12.dp, end = 78.dp, bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            if (authorName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clickable(enabled = detail?.channelId?.isNotBlank() == true) { detail?.channelId?.let { onChannelClick(it) } }) {
                    Text("@${authorName}", color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold, shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 8f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Icon(Icons.Filled.Verified, null, tint = Color(0xFF3EA6FF), modifier = Modifier.size(14.dp))
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, modifier = Modifier.clickable { detail?.channelId?.let { onChannelClick(it) } }) {
                        Text("Subscribe", color = Color.Black, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
            Text(
                title,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onDescriptionClick() }
            )
            if (desc.isNotBlank() && desc != title) {
                Text(
                    (desc.take(140) + if (desc.length > 140) "…" else ""),
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.bodySmall.copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f)),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { onDescriptionClick() }
                )
            }
            val meta = buildList {
                if (views.isNotBlank()) add(views) else if (detail != null && detail.viewCount > 0) add("${detail.viewCount} views")
                detail?.publishDate?.takeIf { it.isNotBlank() }?.let { add(it.take(12)) }
            }.joinToString(" • ")
            if (meta.isNotBlank()) {
                Surface(shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.14f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Visibility, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Text(meta, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.MusicNote, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text("Original sound • ${authorName.ifBlank { "Wediyo" }}", color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.labelSmall.copy(shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 4f)), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            // progress handle when not current? show thin bar
            if (!isCurrent) {
                Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = 0.25f)))
            }
        }

        if (!isCurrent || detail == null) {
            Surface(modifier = Modifier.align(Alignment.Center).size(64.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.10f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
            }
        }
    }
}

@Composable
private fun ShortActionButtonFlow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color = Color.White, bg: Color = Color.White.copy(alpha = 0.14f), isLiked: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.clickable(enabled = onClick != null) { onClick?.invoke() }) {
        Surface(shape = CircleShape, color = if (isLiked) Color(0xFFFF2D55) else bg, modifier = Modifier.size(44.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label, tint = if (isLiked) Color.White else tint, modifier = Modifier.size(22.dp)) }
        }
        if (label.isNotBlank()) Text(label.take(10), color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.8f), blurRadius = 6f)), maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowShortCommentsSheet(
    videoId: String,
    detail: com.teamshryne.wediyo.data.model.UiVideoDetail?,
    page: com.teamshryne.wediyo.data.model.UiCommentsPage?,
    avatarQ: String,
    expandedHeight: androidx.compose.ui.unit.Dp?,
    onProgress: (Float) -> Unit,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit
) {
    // Cheap progress tracking: when sheet height animates, approximate
    LaunchedEffect(expandedHeight) { onProgress(0f) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Comments", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                Text(page?.count ?: detail?.commentsCountText ?: "${page?.comments?.size ?: 0} comments", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = Modifier.clickable { onDismiss() }) {
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
                        if (detail?.commentsContinuation.isNullOrBlank() && detail != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Filled.ChatBubbleOutline, null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Comments are off", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else if (page.comments.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.ChatBubbleOutline, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                if (c.author.isVerified) Icon(Icons.Filled.Verified, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("• ${c.publishedTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(c.content, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.FavoriteBorder, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Button(onClick = onLoadMore, shape = RoundedCornerShape(24.dp)) { Text("Load more") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowShortDescriptionSheet(
    short: com.teamshryne.wediyo.data.model.UiShort,
    detail: com.teamshryne.wediyo.data.model.UiVideoDetail?,
    avatarQ: String,
    onProgress: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.72f).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Description", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = Modifier.clickable { onDismiss() }) {
                Icon(Icons.Filled.Close, null, modifier = Modifier.size(28.dp).padding(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(detail?.title ?: short.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                val meta = buildList {
                    detail?.viewCountText?.takeIf { it.isNotBlank() }?.let { add(it) }
                    detail?.publishDate?.takeIf { it.isNotBlank() }?.let { add(it) }
                }.joinToString(" • ")
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!detail?.description.isNullOrBlank() || !detail?.shortDescription.isNullOrBlank()) {
                item {
                    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                        Text(detail?.description?.ifBlank { detail.shortDescription } ?: "", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.clickable { detail?.channelId?.let { } }) {
                    AsyncImage(model = bestThumbUrl(detail?.channelAvatarsJson ?: "[]", detail?.channelAvatarUrl ?: "", avatarQ), contentDescription = detail?.channelTitle, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF222222)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text(detail?.channelTitle ?: "Channel", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(detail?.subscriberCountText ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
