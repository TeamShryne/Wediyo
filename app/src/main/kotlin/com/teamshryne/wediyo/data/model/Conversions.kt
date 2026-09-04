package com.teamshryne.wediyo.data.model

// Local-library conversions: every playable row type becomes a UiVideo
// so the global overflow sheet (save/like/add-to-playlist) works everywhere.

fun UiPlaylistVideo.toUiVideo(): UiVideo = UiVideo(
    id = videoId,
    title = if (isUnavailable) unavailableReason.ifBlank { "Unavailable video" } else title,
    author = channelName,
    channelId = channelId,
    thumbnailUrl = thumbUrl,
    thumbnailsJson = thumbsJson,
    avatarUrl = "",
    avatarsJson = "[]",
    viewCountText = viewCountText,
    publishedText = publishedText,
    durationText = durationText,
    isLive = false,
    badges = emptyList(),
    description = ""
)

fun UiCourseVideo.toUiVideo(): UiVideo = UiVideo(
    id = videoId,
    title = if (isUnavailable) unavailableReason.ifBlank { "Unavailable video" } else title,
    author = channelName,
    channelId = channelId,
    thumbnailUrl = thumbUrl,
    thumbnailsJson = thumbsJson,
    avatarUrl = "",
    avatarsJson = "[]",
    viewCountText = viewCountText,
    publishedText = publishedText,
    durationText = durationText,
    isLive = false,
    badges = emptyList(),
    description = ""
)

fun UiShowEpisode.toUiVideo(): UiVideo = UiVideo(
    id = videoId,
    title = title,
    author = "",
    channelId = "",
    thumbnailUrl = thumbUrl,
    thumbnailsJson = thumbsJson,
    avatarUrl = "",
    avatarsJson = "[]",
    viewCountText = "",
    publishedText = "",
    durationText = durationText,
    isLive = false,
    badges = emptyList(),
    description = ""
)

fun UiPodcastEpisode.toUiVideo(): UiVideo = UiVideo(
    id = videoId,
    title = title,
    author = channelName,
    channelId = channelId,
    thumbnailUrl = thumbUrl,
    thumbnailsJson = thumbsJson,
    avatarUrl = "",
    avatarsJson = "[]",
    viewCountText = viewCountText,
    publishedText = publishedText,
    durationText = durationText,
    isLive = false,
    badges = emptyList(),
    description = ""
)

fun UiChannelPodcast.toUiVideo(): UiVideo = UiVideo(
    id = podcastId,
    title = title,
    author = "",
    channelId = "",
    thumbnailUrl = thumbUrl,
    thumbnailsJson = thumbsJson,
    avatarUrl = "",
    avatarsJson = "[]",
    viewCountText = "",
    publishedText = updatedText,
    durationText = "",
    isLive = false,
    badges = emptyList(),
    description = episodeCountText
)
