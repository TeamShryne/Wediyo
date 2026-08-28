package com.teamshryne.wediyo.data.model

data class UiVideo(
    val id: String,
    val title: String,
    val author: String,
    val channelId: String,
    val thumbnailUrl: String,
    val thumbnailsJson: String,
    val avatarUrl: String,
    val avatarsJson: String,
    val viewCountText: String,
    val publishedText: String,
    val durationText: String,
    val isLive: Boolean,
    val badges: List<String>,
    val description: String
)

data class UiChannel(
    val channelId: String,
    val title: String,
    val handle: String,
    val subs: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val desc: String,
    val verified: Boolean
)

data class UiShort(
    val videoId: String,
    val title: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val views: String
)

data class UiPlaylist(
    val playlistId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val countText: String,
    val count: Int,
    val isCourse: Boolean,
    val badges: List<String>
)

data class UiChip(val title: String, val selected: Boolean, val token: String)
data class UiFilter(val label: String, val params: String)
data class UiFilterGroup(val title: String, val filters: List<UiFilter>)

data class UiSearchResult(
    val query: String,
    val videos: List<UiVideo>,
    val channels: List<UiChannel>,
    val shorts: List<UiShort>,
    val playlists: List<UiPlaylist>,
    val topicTitle: String?,
    val topicBrowseId: String?,
    val topicAvatar: String?,
    val chips: List<UiChip>,
    val filterGroups: List<UiFilterGroup>,
    val continuation: String,
    val estimated: String
)
