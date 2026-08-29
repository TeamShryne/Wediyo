package com.teamshryne.wediyo.ui.screens.video

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
    LaunchedEffect(listState.firstVisibleItemIndex, state.relatedContinuation) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0 && lastVisible >= total - 6) {
            if (state.relatedContinuation != null && !state.relatedLoading) vm.loadMoreRelated()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Now watching", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Filled.Share, contentDescription = "Share") }
                    IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                        Text("Loading masterpiece…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(pad).padding(24.dp), contentAlignment = Alignment.Center) {
                    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                            Text("Couldn't load video", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text(state.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick = { vm.retry() }, shape = RoundedCornerShape(24.dp)) { Text("Retry") }
                        }
                    }
                }
            }
            state.detail != null -> {
                val d = state.detail!!

                // --- Details sheet (title -> description + stats) ---
                if (state.showDetailsSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { vm.setDetailsSheet(false) },
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp) }
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // handle bar is automatic
                            Text(d.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, lineHeight = MaterialTheme.typography.titleMedium.lineHeight))
                            val meta = buildList {
                                if (d.viewCountText.isNotBlank()) add(d.viewCountText) else if (d.viewCount > 0) add("${d.viewCount} views")
                                if (d.publishDate.isNotBlank()) add(d.publishDate) else if (d.uploadDate.isNotBlank()) add(d.uploadDate)
                                if (d.category.isNotBlank()) add(d.category)
                                if (d.isLiveContent) add("Live")
                            }.joinToString(" • ")
                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                            // Description inside sheet — full, expandable, polished
                            val desc = d.description.ifBlank { d.shortDescription }
                            if (desc.isNotBlank()) {
                                ElevatedCard(
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(Modifier.fillMaxWidth().clickable { vm.toggleDesc() }.padding(16.dp).animateContentSize()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Description", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                                Text(if (state.expandedDesc) "Show less" else "Show more", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (state.expandedDesc) Int.MAX_VALUE else 6, overflow = TextOverflow.Ellipsis)
                                        if (!state.expandedDesc && desc.length > 220) {
                                            Spacer(Modifier.height(6.dp))
                                            Text("Tap to expand", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        if (state.expandedDesc && d.canonicalUrl.isNotBlank()) {
                                            Spacer(Modifier.height(12.dp))
                                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                                Text(d.canonicalUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PolishedStatChip("Views", d.viewCountText.ifBlank { if (d.viewCount > 0) formatCompact(d.viewCount) else "—" }, Icons.Filled.Star, Modifier.weight(1f))
                                PolishedStatChip("Likes", d.likeCountText.ifBlank { d.likeCount.takeIf { it > 0 }?.let { formatCompact(it) } ?: "—" }, Icons.Filled.Favorite, Modifier.weight(1f))
                                PolishedStatChip("Duration", d.durationText.ifBlank { formatDuration(d.lengthSeconds) }, Icons.Filled.DateRange, Modifier.weight(1f))
                            }

                            if (d.captionTracks.isNotEmpty()) {
                                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Captions • ${d.captionTracks.size}", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        d.captionTracks.take(5).forEach { ct ->
                                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(28.dp)) {
                                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(ct.languageCode.take(2).uppercase(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) }
                                                }
                                                Spacer(Modifier.width(10.dp))
                                                Text(ct.name.ifBlank { ct.languageCode }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                Text(ct.kind.ifBlank { "standard" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        if (d.translationLanguages.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text("${d.translationLanguages.size} translation languages", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            if (d.formats.isNotEmpty() || d.adaptiveFormats.isNotEmpty()) {
                                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    Column(Modifier.padding(16.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Streams", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text("${d.formats.size} progressive • ${d.adaptiveFormats.size} adaptive • expires in ${d.expiresInSeconds}s", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        val top = d.adaptiveFormats.filter { !it.isAudio }.sortedByDescending { it.bitrate }.take(3)
                                        if (top.isNotEmpty()) {
                                            Spacer(Modifier.height(10.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                top.forEach { f ->
                                                    AssistChip(onClick = {}, label = { Text(f.qualityLabel.ifBlank { "${f.height}p" }, style = MaterialTheme.typography.labelSmall) }, shape = RoundedCornerShape(20.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Details", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    DetailRow("Video", d.videoId)
                                    DetailRow("Channel", d.channelTitle.ifBlank { d.author })
                                    DetailRow("Category", d.category)
                                    DetailRow("Published", d.publishDate.ifBlank { d.uploadDate })
                                    DetailRow("Safety", if (d.isFamilySafe) "Family safe" else "—")
                                    DetailRow("Embed", if (d.playableInEmbed) "Allowed" else "Restricted")
                                    if (d.keywords.isNotEmpty()) DetailRow("Tags", d.keywords.take(8).joinToString(", "))
                                    if (d.paidPromotionText.isNotBlank()) DetailRow("Promotion", d.paidPromotionText)
                                }
                            }
                        }
                    }
                }

                // --- Comments sheet (new) ---
                if (state.showCommentsSheet) {
                    ModalBottomSheet(
                        onDismissRequest = { vm.setCommentsSheet(false) },
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp) }
                    ) {
                        CommentsSheet(
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

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(pad),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    // Hero — polished, rounded, glass play, gradient scrim
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 10.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black).aspectRatio(16f / 9f)
                        ) {
                            AsyncImage(
                                model = bestThumbUrl(d.thumbnailsJson, d.thumbnailUrl, thumbQ),
                                contentDescription = d.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // bottom gradient for legibility
                            Box(
                                Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(72.dp)
                                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))))
                            )
                            // glass play button
                            Surface(
                                modifier = Modifier.align(Alignment.Center).size(64.dp),
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.16f),
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.Black, modifier = Modifier.size(28.dp).offset(x = 1.dp))
                                    }
                                }
                            }
                            // duration pill
                            if (d.durationText.isNotBlank()) {
                                Surface(
                                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp),
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.Black.copy(alpha = 0.78f)
                                ) {
                                    Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Filled.DateRange, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Text(d.durationText, color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                                    }
                                }
                            }
                            if (d.isLive) {
                                Surface(modifier = Modifier.align(Alignment.TopStart).padding(10.dp), shape = RoundedCornerShape(20.dp), color = Color(0xFFFF1A1A)) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                                        Text("LIVE", color = Color.White, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold))
                                    }
                                }
                            }
                        }
                    }

                    // Title block — tappable opens polished sheet
                    item {
                        Column(
                            Modifier.fillMaxWidth().clickable { vm.setDetailsSheet(true) }.padding(horizontal = 16.dp).padding(top = 16.dp)
                        ) {
                            Text(d.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, lineHeight = MaterialTheme.typography.titleMedium.lineHeight * 1.1), maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                                    Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            (if (d.viewCountText.isNotBlank()) d.viewCountText else if (d.viewCount > 0) formatCompact(d.viewCount) else "—") + " • " + (d.publishDate.take(12).ifBlank { d.uploadDate.take(12) }.ifBlank { "recent" }),
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                                        )
                                    }
                                }
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp).padding(2.dp))
                                }
                            }
                            if (d.keywords.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    d.keywords.take(6).forEach { kw ->
                                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                            Text("#$kw", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Channel row — polished, elevated
                    item {
                        ElevatedCard(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth().clickable(enabled = d.channelId.isNotBlank()) { onChannelClick(d.channelId) }.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    AsyncImage(
                                        model = bestThumbUrl(d.channelAvatarsJson, d.channelAvatarUrl, avatarQ),
                                        contentDescription = d.channelTitle,
                                        modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF222222)),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (d.subscriberCountText.isNotBlank()) {
                                        Box(Modifier.align(Alignment.BottomEnd).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(1.dp)) {
                                            Box(Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surface))
                                        }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(d.channelTitle.ifBlank { d.author }, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (d.channelHandle.isNotBlank()) Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    val handle = displayHandle(d.channelHandle)
                                    val subMeta = listOfNotNull(d.subscriberCountText.takeIf { it.isNotBlank() }, handle.takeIf { it.isNotBlank() }).joinToString(" • ")
                                    if (subMeta.isNotBlank()) Text(subMeta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                FilledTonalButton(onClick = { if (d.channelId.isNotBlank()) onChannelClick(d.channelId) }, shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) {
                                    Text("Visit", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }

                    // Actions — pill bar, horizontal scroll, polished
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Like pill
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.clickable {}) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text(if (d.likeCountText.isNotBlank()) d.likeCountText else if (d.likeCount > 0) formatCompact(d.likeCount) else "Like", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                                    if (d.likeCountText.isNotBlank() || d.likeCount > 0) {
                                        Box(Modifier.width(1.dp).height(14.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                        Icon(Icons.Filled.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            ActionPill(icon = Icons.Filled.Share, label = "Share")
                            ActionPill(icon = Icons.Filled.Bookmark, label = "Save")
                            ActionPill(icon = Icons.Filled.GetApp, label = "Download")
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.clickable { vm.setDetailsSheet(true) }) {
                                Box(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Details", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    // Comments preview card — replaces description card, opens sheet
                    item {
                        val countLabel = state.commentsCount ?: d.commentsCountText.ifBlank { if (state.comments.isNotEmpty()) "${state.comments.size} comments" else "Comments" }
                        ElevatedCard(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp).clickable { vm.setCommentsSheet(true) },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {
                                        Icon(Icons.Filled.Email, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp).padding(4.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text("Comments", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                                        Text(countLabel + (state.commentsSortFilters.find { it.selected }?.let { " • ${it.title}" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(20.dp).padding(3.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                Spacer(Modifier.height(12.dp))
                                if (state.comments.isNotEmpty()) {
                                    // show up to 2 preview comments inside card
                                    state.comments.take(2).forEach { c ->
                                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 10.dp)) {
                                            AsyncImage(
                                                model = bestThumbUrl("", c.author.avatar, avatarQ),
                                                contentDescription = c.author.name,
                                                modifier = Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF222222)),
                                                contentScale = ContentScale.Crop
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(c.author.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                                    if (c.author.isCreator) Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF3EA6FF)) { Text("Creator", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) }
                                                    Text("• ${c.publishedTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                                }
                                                Spacer(Modifier.height(2.dp))
                                                Text(c.content, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                                Spacer(Modifier.height(4.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant); if (c.likeCount.isNotBlank()) Text(c.likeCount, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                                    if (c.replyCount.isNotBlank()) Text(c.replyCount, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                        Text("View all comments", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                                    }
                                } else if (state.commentsLoading) {
                                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Text("Loading comments…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.Email, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                                        Text(if (d.commentsContinuation.isBlank()) "Comments are off" else "Be the first to comment", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (d.commentsContinuation.isNotBlank()) Text("Tap to open", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    // Up next — header polished
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Up next", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                            if (state.related.isNotEmpty()) Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text("${state.related.size}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.clickable {}) {
                                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("For you", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                                }
                            }
                        }
                    }
                    if (state.related.isNotEmpty()) {
                        items(state.related.size) { idx ->
                            val v = state.related[idx]
                            Box(Modifier.padding(horizontal = 8.dp)) { ChannelVideoListCard(video = v, thumbQuality = thumbQ, onClick = { onVideoClick(v.id) }) }
                        }
                        if (state.relatedLoading) {
                            item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp) } }
                        } else if (state.relatedContinuation != null) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                                    OutlinedButton(onClick = { vm.loadMoreRelated() }, shape = RoundedCornerShape(24.dp)) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Load more")
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                        Text("No related videos yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
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
private fun CommentsSheet(
    countText: String,
    sortFilters: List<com.teamshryne.wediyo.data.model.UiCommentSortFilter>,
    comments: List<com.teamshryne.wediyo.data.model.UiComment>,
    loading: Boolean,
    continuation: String?,
    avatarQ: String,
    onSort: (String) -> Unit,
    onLoadMore: () -> Unit,
    onReply: (String, (List<com.teamshryne.wediyo.data.model.UiComment>) -> Unit) -> Unit
) {
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Comments", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
                if (countText.isNotBlank()) Text(countText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(24.dp).padding(4.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        if (sortFilters.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sortFilters.take(4).forEach { sf ->
                    FilterChip(
                        selected = sf.selected,
                        onClick = { if (!sf.selected) onSort(sf.continuationToken) },
                        label = { Text(sf.title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (sf.selected) FontWeight.Bold else FontWeight.Medium)) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (comments.isEmpty() && loading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            } else if (comments.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Email, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No comments yet", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                            Text("Be the first to share what you think", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(comments.size) { idx ->
                    val c = comments[idx]
                    PolishedCommentCard(comment = c, avatarQ = avatarQ, onReply = onReply)
                    if (idx < comments.lastIndex) HorizontalDivider(Modifier.padding(start = 54.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
                if (loading) {
                    item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp) } }
                } else if (continuation != null) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            Button(onClick = onLoadMore, shape = RoundedCornerShape(24.dp)) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Load more")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolishedCommentCard(comment: com.teamshryne.wediyo.data.model.UiComment, avatarQ: String, onReply: (String, (List<com.teamshryne.wediyo.data.model.UiComment>) -> Unit) -> Unit) {
    var expandedReplies by remember { mutableStateOf<List<com.teamshryne.wediyo.data.model.UiComment>?>(null) }
    var repliesLoading by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
        AsyncImage(
            model = bestThumbUrl("", comment.author.avatar, avatarQ),
            contentDescription = comment.author.name,
            modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF222222)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(comment.author.name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (comment.author.isVerified) Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                if (comment.author.isCreator) Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF3EA6FF)) { Text("Creator", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) }
                Text("• ${comment.publishedTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(4.dp))
            Text(comment.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 9.dp, vertical = 5.dp)) {
                    Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (comment.likeCount.isNotBlank()) Text(comment.likeCount, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (comment.replyCount.isNotBlank() && comment.repliesContinuation.isNotBlank()) {
                    Text(
                        comment.replyCount,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable {
                            if (expandedReplies == null && !repliesLoading) {
                                repliesLoading = true
                                onReply(comment.repliesContinuation) { replies ->
                                    expandedReplies = replies
                                    repliesLoading = false
                                }
                            } else if (expandedReplies != null) expandedReplies = null
                        }.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)).padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                if (repliesLoading) { CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp) }
            }
            if (expandedReplies != null) {
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
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

@Composable
private fun PolishedStatChip(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ActionPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.clickable {}) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
    }
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

private fun displayHandle(raw: String): String {
    if (raw.isBlank()) return ""
    val trimmed = raw.trim()
    // Already handle like "@Foo"
    if (trimmed.startsWith("@")) return trimmed
    // URL like "http://www.youtube.com/@ThePrimeTimeagen" -> extract after "/@"
    val atIdx = trimmed.lastIndexOf("/@")
    if (atIdx != -1) return "@" + trimmed.substring(atIdx + 2).substringBefore("?").substringBefore("/")
    // fallback: handle without @ may be in path segment containing dot -> avoid showing full URL
    if (trimmed.contains("youtube.com") || trimmed.contains("http")) {
        val last = trimmed.substringAfterLast("/").substringBefore("?").substringBefore("#")
        if (last.isNotBlank() && !last.contains(".")) return if (last.startsWith("@")) last else "@$last"
        return ""
    }
    return trimmed
}
