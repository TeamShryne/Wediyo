package com.teamshryne.wediyo.data.model

data class UiCaptionTrack(
    val baseUrl: String,
    val name: String,
    val languageCode: String,
    val kind: String,
    val isTranslatable: Boolean,
    val vssId: String,
    val trackName: String
)

data class UiTranslationLanguage(
    val languageCode: String,
    val languageName: String
)

data class UiStreamingFormat(
    val itag: Int,
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val width: Int,
    val height: Int,
    val quality: String,
    val fps: Int,
    val qualityLabel: String,
    val approxDurationMs: String,
    val audioQuality: String,
    val audioSampleRate: Int,
    val audioChannels: Int,
    val contentLength: String,
    val averageBitrate: Int,
    val lastModified: String,
    val isAudio: Boolean,
    val isDrc: Boolean,
    val xtags: String
)

data class UiVideoDetail(
    val videoId: String,
    val title: String,
    val author: String,
    val channelId: String,
    val channelHandle: String,
    val channelTitle: String,
    val channelAvatarUrl: String,
    val channelAvatarsJson: String,
    val subscriberCountText: String,
    val description: String,
    val shortDescription: String,
    val viewCount: Long,
    val viewCountText: String,
    val likeCount: Long,
    val likeCountText: String,
    val publishDate: String,
    val uploadDate: String,
    val category: String,
    val keywords: List<String>,
    val lengthSeconds: Long,
    val durationText: String,
    val isLiveContent: Boolean,
    val isLive: Boolean,
    val isPrivate: Boolean,
    val isUnlisted: Boolean,
    val isFamilySafe: Boolean,
    val allowRatings: Boolean,
    val playabilityStatus: String,
    val playableInEmbed: Boolean,
    val paidPromotionText: String,
    val thumbnailUrl: String,
    val thumbnailsJson: String,
    val storyboardSpec: String,
    val availableCountries: List<String>,
    val captionTracks: List<UiCaptionTrack>,
    val translationLanguages: List<UiTranslationLanguage>,
    val formats: List<UiStreamingFormat>,
    val adaptiveFormats: List<UiStreamingFormat>,
    val expiresInSeconds: Int,
    val serverAbrStreamingUrl: String,
    val hlsManifestUrl: String,
    val dashManifestUrl: String,
    val embedUrl: String,
    val canonicalUrl: String,
    val relatedVideos: List<UiVideo>,
    val relatedContinuation: String,
    val commentsContinuation: String,
    val commentsCountText: String
)

data class UiCommentAuthor(
    val channelId: String,
    val name: String,
    val avatar: String,
    val isVerified: Boolean,
    val isCreator: Boolean,
    val isArtist: Boolean
)

data class UiComment(
    val commentId: String,
    val content: String,
    val publishedTime: String,
    val author: UiCommentAuthor,
    val likeCount: String,
    val replyCount: String,
    val replyLevel: Int,
    val repliesContinuation: String
)

data class UiCommentSortFilter(
    val title: String,
    val selected: Boolean,
    val continuationToken: String,
    val subtitle: String?
)

data class UiCommentsPage(
    val count: String?,
    val comments: List<UiComment>,
    val continuation: String?,
    val sortFilters: List<UiCommentSortFilter>
)
