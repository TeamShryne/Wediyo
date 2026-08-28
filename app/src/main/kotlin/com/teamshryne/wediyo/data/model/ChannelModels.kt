package com.teamshryne.wediyo.data.model

data class UiChannelHeader(
    val channelId: String,
    val title: String,
    val handle: String,
    val avatarUrl: String,
    val avatarsJson: String,
    val bannerUrl: String,
    val bannersJson: String,
    val subs: String,
    val videoCount: String,
    val description: String,
    val verified: Boolean,
    val channelUrl: String,
    val rssUrl: String,
    val keywords: String
)

data class UiChannelTab(
    val title: String,
    val selected: Boolean,
    val params: String,
    val browseId: String,
    val canonicalBase: String
)

data class UiChannelShelf(
    val title: String,
    val browseId: String,
    val videos: List<UiVideo>
)

data class UiChannelHome(
    val header: UiChannelHeader?,
    val tabs: List<UiChannelTab>,
    val shelves: List<UiChannelShelf>
)

data class UiChannelVideoChip(
    val title: String,
    val selected: Boolean,
    val token: String
)

data class UiChannelVideos(
    val header: UiChannelHeader?,
    val tabs: List<UiChannelTab>,
    val chips: List<UiChannelVideoChip>,
    val videos: List<UiVideo>,
    val continuation: String
)

data class UiChannelShorts(
    val header: UiChannelHeader?,
    val tabs: List<UiChannelTab>,
    val chips: List<UiChannelVideoChip>,
    val shorts: List<UiShort>,
    val continuation: String
)

data class UiChannelLive(
    val header: UiChannelHeader?,
    val tabs: List<UiChannelTab>,
    val chips: List<UiChannelVideoChip>,
    val lives: List<UiVideo>,
    val continuation: String
)

data class UiChannelPodcast(
    val podcastId: String,
    val browseId: String,
    val title: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val episodeCountText: String,
    val episodeCount: Int,
    val updatedText: String
)

data class UiChannelPodcasts(
    val header: UiChannelHeader?,
    val tabs: List<UiChannelTab>,
    val podcasts: List<UiChannelPodcast>,
    val continuation: String
)

data class UiChannelPlaylist(
    val playlistId: String,
    val browseId: String,
    val title: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val videoCountText: String,
    val videoCount: Int
)

data class UiChannelPlaylists(
    val header: UiChannelHeader?,
    val tabs: List<UiChannelTab>,
    val playlists: List<UiChannelPlaylist>,
    val continuation: String
)

data class UiChannelPostPollChoice(val text: String)

data class UiChannelPostPoll(
    val choices: List<UiChannelPostPollChoice>,
    val totalVotesText: String,
    val type: String
)

data class UiChannelPostImage(
    val url: String,
    val thumbsJson: String
)

data class UiChannelPost(
    val postId: String,
    val authorText: String,
    val authorThumbUrl: String,
    val authorThumbsJson: String,
    val contentText: String,
    val publishedTimeText: String,
    val voteCountText: String,
    val voteCountLabel: String,
    val attachmentType: String,
    val poll: UiChannelPostPoll?,
    val images: List<UiChannelPostImage>,
    val video: UiVideo? = null
)

data class UiChannelPosts(
    val header: UiChannelHeader?,
    val tabs: List<UiChannelTab>,
    val posts: List<UiChannelPost>,
    val continuation: String
)

data class UiChannelStoreProduct(
    val title: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val priceText: String,
    val merchantName: String,
    val fromText: String,
    val productUrl: String
)

data class UiChannelStore(
    val header: UiChannelHeader?,
    val tabs: List<UiChannelTab>,
    val products: List<UiChannelStoreProduct>,
    val continuation: String
)

data class UiChannelCourse(
    val playlistId: String,
    val browseId: String,
    val title: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val videoCountText: String,
    val videoCount: Int
)

data class UiChannelCourses(
    val header: UiChannelHeader?,
    val tabs: List<UiChannelTab>,
    val courses: List<UiChannelCourse>,
    val continuation: String
)

data class UiChannelShow(
    val showId: String,
    val browseId: String,
    val title: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val subtitle: String,
    val episodeCountText: String,
    val episodeCount: Int
)

data class UiChannelShows(
    val header: UiChannelHeader?,
    val tabs: List<UiChannelTab>,
    val shows: List<UiChannelShow>,
    val continuation: String
)

data class UiChannelAboutLink(
    val title: String,
    val url: String,
    val linkText: String,
    val faviconUrl: String,
    val faviconsJson: String
)

data class UiChannelAbout(
    val description: String,
    val country: String,
    val subscriberCountText: String,
    val viewCountText: String,
    val joinedDateText: String,
    val canonicalUrl: String,
    val displayUrl: String,
    val channelId: String,
    val videoCountText: String,
    val videoCount: Int,
    val links: List<UiChannelAboutLink>
)

data class UiChannelAboutResult(
    val header: UiChannelHeader?,
    val tabs: List<UiChannelTab>,
    val about: UiChannelAbout?
)

data class UiPlaylistVideo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val durationText: String,
    val indexText: String,
    val viewCountText: String,
    val publishedText: String,
    val isUnavailable: Boolean,
    val unavailableReason: String
)

data class UiPlaylistHeader(
    val title: String,
    val description: String,
    val channelName: String,
    val channelId: String,
    val channelHandle: String,
    val channelAvatarUrl: String,
    val channelAvatarsJson: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val videoCountText: String,
    val videoCount: Int,
    val viewCountText: String,
    val lastUpdatedText: String,
    val privacy: String,
    val hasUnavailable: Boolean
)

data class UiPlaylistDetail(
    val header: UiPlaylistHeader?,
    val videos: List<UiPlaylistVideo>,
    val continuation: String,
    val playlistId: String
)

data class UiCourseVideo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val durationText: String,
    val indexText: String,
    val viewCountText: String,
    val publishedText: String,
    val isUnavailable: Boolean,
    val unavailableReason: String
)

data class UiCourseHeader(
    val title: String,
    val description: String,
    val channelName: String,
    val channelId: String,
    val channelHandle: String,
    val channelAvatarUrl: String,
    val channelAvatarsJson: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val videoCountText: String,
    val videoCount: Int,
    val viewCountText: String,
    val lastUpdatedText: String,
    val hasUnavailable: Boolean
)

data class UiCourseDetail(
    val header: UiCourseHeader?,
    val videos: List<UiCourseVideo>,
    val continuation: String,
    val playlistId: String
)

data class UiShowEpisode(
    val videoId: String,
    val title: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val durationText: String,
    val durationSecs: Int,
    val indexText: String,
    val isUnplayable: Boolean
)

data class UiShowSeason(
    val title: String,
    val selected: Boolean,
    val params: String,
    val browseId: String
)

data class UiShowHeader(
    val title: String,
    val description: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val seasonText: String,
    val episodeCountText: String,
    val episodeCount: Int,
    val seasons: List<UiShowSeason>,
    val currentSeason: String,
    val subtitle: String,
    val overlayTitle: String,
    val overlaySubtitle: String
)

data class UiShowDetail(
    val header: UiShowHeader?,
    val episodes: List<UiShowEpisode>,
    val continuation: String,
    val playlistId: String
)

data class UiPodcastEpisode(
    val videoId: String,
    val title: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val durationText: String,
    val durationSecs: Int,
    val channelName: String,
    val channelId: String,
    val publishedText: String,
    val viewCountText: String
)

data class UiPodcastHeader(
    val title: String,
    val description: String,
    val thumbUrl: String,
    val thumbsJson: String,
    val channelName: String,
    val channelId: String,
    val channelHandle: String,
    val channelAvatarUrl: String,
    val channelAvatarsJson: String,
    val episodeCountText: String,
    val episodeCount: Int,
    val updatedText: String
)

data class UiPodcastDetail(
    val header: UiPodcastHeader?,
    val episodes: List<UiPodcastEpisode>,
    val continuation: String,
    val playlistId: String
)
