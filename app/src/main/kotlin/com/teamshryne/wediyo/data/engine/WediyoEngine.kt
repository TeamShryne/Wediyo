package com.teamshryne.wediyo.data.engine

import com.teamshryne.wediyo.wediyo.InnertubeSession
import com.teamshryne.wediyo.wediyo.Wediyo
import com.teamshryne.wediyo.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object WediyoEngine {
    @Volatile private var session: InnertubeSession? = null

    suspend fun getSession(): InnertubeSession = withContext(Dispatchers.IO) {
        session ?: run {
            Wediyo.touch()
            val s = Wediyo.fetchInnertubeSession()
            session = s
            s
        }
    }

    suspend fun search(query: String, continuation: String = "", params: String = ""): UiSearchResult = withContext(Dispatchers.IO) {
        val s = getSession()
        val res = if (params.isNotBlank() && continuation.isBlank()) {
            Wediyo.searchWithParams(s, query, params, "")
        } else if (continuation.isNotBlank()) {
            // params ignored when continuation present (token already encodes filter)
            Wediyo.search(s, query, continuation)
        } else {
            Wediyo.search(s, query, "")
        }
        parse(res)
    }

    suspend fun searchWithChip(token: String): UiSearchResult = withContext(Dispatchers.IO) {
        val s = getSession()
        val res = Wediyo.search(s, "", token)
        parse(res)
    }

    suspend fun fetchChannelHome(browseId: String): UiChannelHome = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchChannelHome(s, browseId)
        parseChannelHome(r)
    }

    suspend fun fetchChannelVideos(browseId: String, continuation: String = ""): UiChannelVideos = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchChannelVideos(s, browseId, continuation)
        parseChannelVideos(r)
    }

    suspend fun fetchChannelShorts(browseId: String, continuation: String = ""): UiChannelShorts = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchChannelShorts(s, browseId, continuation)
        parseChannelShorts(r)
    }

    suspend fun fetchChannelLive(browseId: String, continuation: String = ""): UiChannelLive = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchChannelLive(s, browseId, continuation)
        parseChannelLive(r)
    }

    suspend fun fetchChannelPodcasts(browseId: String, continuation: String = ""): UiChannelPodcasts = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchChannelPodcasts(s, browseId, continuation)
        parseChannelPodcasts(r)
    }

    suspend fun fetchChannelPlaylists(browseId: String, continuation: String = ""): UiChannelPlaylists = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchChannelPlaylists(s, browseId, continuation)
        parseChannelPlaylists(r)
    }

    suspend fun fetchChannelPosts(browseId: String, continuation: String = ""): UiChannelPosts = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchChannelPosts(s, browseId, continuation)
        parseChannelPosts(r)
    }

    suspend fun fetchChannelStore(browseId: String, continuation: String = ""): UiChannelStore = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchChannelStore(s, browseId, continuation)
        parseChannelStore(r)
    }

    suspend fun fetchChannelCourses(browseId: String, continuation: String = ""): UiChannelCourses = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchChannelCourses(s, browseId, continuation)
        parseChannelCourses(r)
    }

    suspend fun fetchChannelShows(browseId: String, continuation: String = ""): UiChannelShows = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchChannelShows(s, browseId, continuation)
        parseChannelShows(r)
    }

    suspend fun fetchChannelAbout(browseId: String): UiChannelAboutResult = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchChannelAbout(s, browseId)
        parseChannelAbout(r)
    }

    suspend fun fetchPlaylist(playlistId: String, continuation: String = ""): UiPlaylistDetail = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchPlaylist(s, playlistId, continuation)
        parsePlaylistDetail(r)
    }

    suspend fun fetchCourse(playlistId: String, continuation: String = ""): UiCourseDetail = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchCourse(s, playlistId, continuation)
        parseCourseDetail(r)
    }

    suspend fun fetchShow(playlistId: String, continuation: String = ""): UiShowDetail = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchShow(s, playlistId, continuation)
        parseShowDetail(r)
    }

    suspend fun fetchShowWithSeason(playlistId: String, params: String): UiShowDetail = withContext(Dispatchers.IO) {
        val s = getSession()
        val r = Wediyo.fetchShowWithOption(s, playlistId, "", params)
        parseShowDetail(r)
    }

    suspend fun fetchVideoDetail(videoId: String): UiVideoDetail = withContext(Dispatchers.IO) {
        val s = getSession()
        // Try new gomobile method, fallback to reflection if AAR stale
        val r: Any = try {
            Wediyo.fetchVideoDetail(s, videoId)
        } catch (_: Throwable) {
            val cls = Wediyo::class.java
            val method = cls.methods.find { it.name == "fetchVideoDetail" }
                ?: throw IllegalStateException("fetchVideoDetail not found in AAR")
            method.invoke(null, s, videoId)!!
        }
        parseVideoDetail(r)
    }

    private fun parseVideoDetail(r: Any): UiVideoDetail {
        val jsonStr = try { r::class.java.getMethod("toJSON").invoke(r) as String } catch (_: Exception) { "{}" }
        val obj = JSONObject(jsonStr)
        fun optThumbs(key: String): String = obj.optJSONArray(key)?.toString() ?: "[]"
        val channelAvatarsJson = obj.optJSONArray("channel_avatars")?.toString() ?: "[]"
        val thumbnailsJson = obj.optJSONArray("thumbnails")?.toString() ?: "[]"
        val keywords = mutableListOf<String>()
        obj.optJSONArray("keywords")?.let { arr ->
            for (i in 0 until arr.length()) keywords.add(arr.optString(i))
        }
        val availableCountries = mutableListOf<String>()
        obj.optJSONArray("available_countries")?.let { arr ->
            for (i in 0 until arr.length()) availableCountries.add(arr.optString(i))
        }
        val captionTracks = mutableListOf<UiCaptionTrack>()
        obj.optJSONArray("caption_tracks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                captionTracks.add(
                    UiCaptionTrack(
                        baseUrl = o.optString("base_url", ""),
                        name = o.optString("name", ""),
                        languageCode = o.optString("language_code", ""),
                        kind = o.optString("kind", ""),
                        isTranslatable = o.optBoolean("is_translatable", false),
                        vssId = o.optString("vss_id", ""),
                        trackName = o.optString("track_name", "")
                    )
                )
            }
        }
        val translationLanguages = mutableListOf<UiTranslationLanguage>()
        obj.optJSONArray("translation_languages")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                translationLanguages.add(UiTranslationLanguage(o.optString("language_code", ""), o.optString("language_name", "")))
            }
        }
        val formats = mutableListOf<UiStreamingFormat>()
        parseFormats(obj.optJSONArray("formats"), formats)
        val adaptiveFormats = mutableListOf<UiStreamingFormat>()
        parseFormats(obj.optJSONArray("adaptive_formats"), adaptiveFormats)
        val related = mutableListOf<UiVideo>()
        // related_videos is []VideoMetadata reuse
        try {
            val method = r::class.java.getMethod("relatedVideosJSON")
            val js = method.invoke(r) as String
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    related.add(
                        UiVideo(
                            id = o.optString("id", ""),
                            title = o.optString("title", ""),
                            author = o.optString("author", ""),
                            channelId = o.optString("channel_id", ""),
                            thumbnailUrl = o.optString("thumbnail_url", ""),
                            thumbnailsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            avatarUrl = o.optString("channel_avatar_url", ""),
                            avatarsJson = o.optJSONArray("channel_avatars")?.toString() ?: "[]",
                            viewCountText = o.optString("view_count_text", ""),
                            publishedText = o.optString("published_time_text", ""),
                            durationText = o.optString("duration_text", ""),
                            isLive = o.optBoolean("is_live", false),
                            badges = o.optJSONArray("badges")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                            description = o.optString("description_snippet", "")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // fallback via JSON
            obj.optJSONArray("related_videos")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    related.add(
                        UiVideo(
                            id = o.optString("id", ""),
                            title = o.optString("title", ""),
                            author = o.optString("author", ""),
                            channelId = o.optString("channel_id", ""),
                            thumbnailUrl = o.optString("thumbnail_url", ""),
                            thumbnailsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            avatarUrl = o.optString("channel_avatar_url", ""),
                            avatarsJson = o.optJSONArray("channel_avatars")?.toString() ?: "[]",
                            viewCountText = o.optString("view_count_text", ""),
                            publishedText = o.optString("published_time_text", ""),
                            durationText = o.optString("duration_text", ""),
                            isLive = o.optBoolean("is_live", false),
                            badges = emptyList(),
                            description = o.optString("description_snippet", "")
                        )
                    )
                }
            }
        }
        return UiVideoDetail(
            videoId = obj.optString("video_id", ""),
            title = obj.optString("title", ""),
            author = obj.optString("author", ""),
            channelId = obj.optString("channel_id", ""),
            channelHandle = obj.optString("channel_handle", ""),
            channelTitle = obj.optString("channel_title", ""),
            channelAvatarUrl = obj.optString("channel_avatar_url", ""),
            channelAvatarsJson = channelAvatarsJson,
            subscriberCountText = obj.optString("subscriber_count_text", ""),
            description = obj.optString("description", ""),
            shortDescription = obj.optString("short_description", ""),
            viewCount = obj.optLong("view_count", 0),
            viewCountText = obj.optString("view_count_text", ""),
            likeCount = obj.optLong("like_count", 0),
            likeCountText = obj.optString("like_count_text", ""),
            publishDate = obj.optString("publish_date", ""),
            uploadDate = obj.optString("upload_date", ""),
            category = obj.optString("category", ""),
            keywords = keywords,
            lengthSeconds = obj.optLong("length_seconds", 0),
            durationText = obj.optString("duration_text", ""),
            isLiveContent = obj.optBoolean("is_live_content", false),
            isLive = obj.optBoolean("is_live", false),
            isPrivate = obj.optBoolean("is_private", false),
            isUnlisted = obj.optBoolean("is_unlisted", false),
            isFamilySafe = obj.optBoolean("is_family_safe", true),
            allowRatings = obj.optBoolean("allow_ratings", true),
            playabilityStatus = obj.optString("playability_status", ""),
            playableInEmbed = obj.optBoolean("playable_in_embed", true),
            paidPromotionText = obj.optString("paid_promotion_text", ""),
            thumbnailUrl = obj.optString("thumbnail_url", ""),
            thumbnailsJson = thumbnailsJson,
            storyboardSpec = obj.optString("storyboard_spec", ""),
            availableCountries = availableCountries,
            captionTracks = captionTracks,
            translationLanguages = translationLanguages,
            formats = formats,
            adaptiveFormats = adaptiveFormats,
            expiresInSeconds = obj.optInt("expires_in_seconds", 0),
            serverAbrStreamingUrl = obj.optString("server_abr_streaming_url", ""),
            hlsManifestUrl = obj.optString("hls_manifest_url", ""),
            dashManifestUrl = obj.optString("dash_manifest_url", ""),
            embedUrl = obj.optString("embed_url", ""),
            canonicalUrl = obj.optString("canonical_url", ""),
            relatedVideos = related,
            relatedContinuation = obj.optString("related_continuation", ""),
            commentsContinuation = obj.optString("comments_continuation", ""),
            commentsCountText = obj.optString("comments_count_text", "")
        )
    }

    private fun parseFormats(arr: JSONArray?, out: MutableList<UiStreamingFormat>) {
        if (arr == null) return
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val trackObj = o.optJSONObject("audio_track")
            out.add(
                UiStreamingFormat(
                    itag = o.optInt("itag", 0),
                    url = o.optString("url", ""),
                    mimeType = o.optString("mime_type", ""),
                    bitrate = o.optInt("bitrate", 0),
                    width = o.optInt("width", 0),
                    height = o.optInt("height", 0),
                    quality = o.optString("quality", ""),
                    fps = o.optInt("fps", 0),
                    qualityLabel = o.optString("quality_label", ""),
                    approxDurationMs = o.optString("approx_duration_ms", ""),
                    audioQuality = o.optString("audio_quality", ""),
                    audioSampleRate = o.optInt("audio_sample_rate", 0),
                    audioChannels = o.optInt("audio_channels", 0),
                    contentLength = o.optString("content_length", ""),
                    averageBitrate = o.optInt("average_bitrate", 0),
                    lastModified = o.optString("last_modified", ""),
                    isAudio = o.optBoolean("is_audio", false),
                    isDrc = o.optBoolean("is_drc", false),
                    xtags = o.optString("xtags", "")
                )
            )
        }
    }

    // Comments — Mediyo flow port
    suspend fun fetchComments(continuation: String): UiCommentsPage = withContext(Dispatchers.IO) {
        val s = getSession()
        val r: Any = try {
            Wediyo.fetchCommentsPage(s, continuation)
        } catch (_: Throwable) {
            val cls = Wediyo::class.java
            val method = cls.methods.find { it.name == "fetchCommentsPage" }
                ?: throw IllegalStateException("fetchCommentsPage not found in AAR")
            method.invoke(null, s, continuation)!!
        }
        parseCommentsPage(r)
    }

    suspend fun fetchReplies(continuation: String): UiCommentsPage = withContext(Dispatchers.IO) {
        val s = getSession()
        val r: Any = try {
            Wediyo.fetchRepliesPage(s, continuation)
        } catch (_: Throwable) {
            val cls = Wediyo::class.java
            val method = cls.methods.find { it.name == "fetchRepliesPage" }
                ?: throw IllegalStateException("fetchRepliesPage not found in AAR")
            method.invoke(null, s, continuation)!!
        }
        parseCommentsPage(r)
    }

    suspend fun fetchRelated(continuation: String): UiVideoDetail = withContext(Dispatchers.IO) {
        val s = getSession()
        val r: Any = try {
            Wediyo.fetchRelatedContinuation(s, continuation)
        } catch (_: Throwable) {
            val cls = Wediyo::class.java
            val method = cls.methods.find { it.name == "fetchRelatedContinuation" }
                ?: throw IllegalStateException("fetchRelatedContinuation not found in AAR")
            method.invoke(null, s, continuation)!!
        }
        parseVideoDetail(r)
    }

    private fun parseCommentsPage(r: Any): UiCommentsPage {
        val jsonStr = try { r::class.java.getMethod("toJSON").invoke(r) as String } catch (_: Exception) { "{}" }
        val obj = JSONObject(jsonStr)
        val comments = mutableListOf<UiComment>()
        val arr = try {
            val m = r::class.java.getMethod("commentsJSON").invoke(r) as String
            JSONArray(m)
        } catch (_: Exception) {
            obj.optJSONArray("comments") ?: JSONArray()
        }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val authorObj = o.optJSONObject("author") ?: JSONObject()
            comments.add(
                UiComment(
                    commentId = o.optString("comment_id", ""),
                    content = o.optString("content", ""),
                    publishedTime = o.optString("published_time", ""),
                    author = UiCommentAuthor(
                        channelId = authorObj.optString("channel_id", ""),
                        name = authorObj.optString("name", ""),
                        avatar = authorObj.optString("avatar", ""),
                        isVerified = authorObj.optBoolean("is_verified", false),
                        isCreator = authorObj.optBoolean("is_creator", false),
                        isArtist = authorObj.optBoolean("is_artist", false)
                    ),
                    likeCount = o.optString("like_count", ""),
                    replyCount = o.optString("reply_count", ""),
                    replyLevel = o.optInt("reply_level", 0),
                    repliesContinuation = o.optString("replies_continuation", "")
                )
            )
        }
        val sortFilters = mutableListOf<UiCommentSortFilter>()
        val sfArr = try {
            val m = r::class.java.getMethod("sortFiltersJSON").invoke(r) as String
            JSONArray(m)
        } catch (_: Exception) {
            obj.optJSONArray("sort_filters") ?: JSONArray()
        }
        for (i in 0 until sfArr.length()) {
            val o = sfArr.getJSONObject(i)
            sortFilters.add(
                UiCommentSortFilter(
                    title = o.optString("title", ""),
                    selected = o.optBoolean("selected", false),
                    continuationToken = o.optString("continuation_token", ""),
                    subtitle = o.optString("subtitle", "").takeIf { it.isNotBlank() }
                )
            )
        }
        return UiCommentsPage(
            count = obj.optString("count", "").takeIf { it.isNotBlank() },
            comments = comments,
            continuation = obj.optString("continuation", "").takeIf { it.isNotBlank() },
            sortFilters = sortFilters
        )
    }

    suspend fun fetchPodcast(playlistId: String, continuation: String = ""): UiPodcastDetail = withContext(Dispatchers.IO) {
        val s = getSession()
        // Use playlist fetch as base; podcast is playlist with square thumb. Until AAR rebuilt with FetchPodcast, fallback to FetchPlaylist.
        // After CI builds new AAR, this can be switched to Wediyo.fetchPodcast
        try {
            val cls = Wediyo::class.java
            val method = cls.methods.find { it.name == "fetchPodcast" }
            if (method != null) {
                val r = method.invoke(null, s, playlistId, continuation)
                if (r != null) return@withContext parsePodcastDetailGeneric(r)
            }
        } catch (_: Exception) {}
        // Fallback: use playlist API and map to podcast (header will be populated via playlist but podcast episodes are same)
        val r2 = Wediyo.fetchPlaylist(s, playlistId, continuation)
        parsePodcastViaPlaylist(r2, playlistId, continuation)
    }

    private fun parsePodcastViaPlaylist(r: com.teamshryne.wediyo.wediyo.PlaylistDetailResult, pid: String, cont: String): UiPodcastDetail {
        // Map playlist detail to podcast detail (common structure: lockupViewModel episodes)
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiPodcastHeader(
                title = h.optString("title", ""),
                description = h.optString("description", ""),
                thumbUrl = h.optString("thumbnail_url", ""),
                thumbsJson = h.optJSONArray("thumbnails")?.toString() ?: "[]",
                channelName = h.optString("channel_name", ""),
                channelId = h.optString("channel_id", ""),
                channelHandle = h.optString("channel_handle", ""),
                channelAvatarUrl = h.optString("channel_avatar_url", ""),
                channelAvatarsJson = h.optJSONArray("channel_avatars")?.toString() ?: "[]",
                episodeCountText = h.optString("video_count_text", ""),
                episodeCount = h.optInt("video_count", 0),
                updatedText = h.optString("last_updated_text", "")
            )
        }
        val episodes = mutableListOf<UiPodcastEpisode>()
        r.videosJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    episodes.add(
                        UiPodcastEpisode(
                            videoId = o.optString("video_id", ""),
                            title = o.optString("title", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            durationText = o.optString("duration_text", ""),
                            durationSecs = 0,
                            channelName = o.optString("channel_name", ""),
                            channelId = o.optString("channel_id", ""),
                            publishedText = o.optString("published_time_text", ""),
                            viewCountText = o.optString("view_count_text", "")
                        )
                    )
                }
            }
        }
        return UiPodcastDetail(header, episodes, obj.optString("continuation", ""), obj.optString("playlist_id", pid))
    }

    private fun parsePodcastDetailGeneric(r: Any): UiPodcastDetail {
        val jsonStr = try { r::class.java.getMethod("toJSON").invoke(r) as String } catch (_: Exception) { "{}" }
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiPodcastHeader(
                title = h.optString("title", ""),
                description = h.optString("description", ""),
                thumbUrl = h.optString("thumbnail_url", ""),
                thumbsJson = h.optJSONArray("thumbnails")?.toString() ?: "[]",
                channelName = h.optString("channel_name", ""),
                channelId = h.optString("channel_id", ""),
                channelHandle = h.optString("channel_handle", ""),
                channelAvatarUrl = h.optString("channel_avatar_url", ""),
                channelAvatarsJson = h.optJSONArray("channel_avatars")?.toString() ?: "[]",
                episodeCountText = h.optString("episode_count_text", ""),
                episodeCount = h.optInt("episode_count", 0),
                updatedText = h.optString("updated_text", "")
            )
        }
        val episodes = mutableListOf<UiPodcastEpisode>()
        val episodesJson = try { r::class.java.getMethod("episodesJSON").invoke(r) as String } catch (_: Exception) { "[]" }
        episodesJson.let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    episodes.add(
                        UiPodcastEpisode(
                            videoId = o.optString("video_id", ""),
                            title = o.optString("title", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            durationText = o.optString("duration_text", ""),
                            durationSecs = o.optInt("duration_secs", 0),
                            channelName = o.optString("channel_name", ""),
                            channelId = o.optString("channel_id", ""),
                            publishedText = o.optString("published_text", ""),
                            viewCountText = o.optString("view_count_text", "")
                        )
                    )
                }
            }
        }
        return UiPodcastDetail(header, episodes, obj.optString("continuation", ""), obj.optString("playlist_id", ""))
    }

    suspend fun parseCourseDetail(r: com.teamshryne.wediyo.wediyo.CourseDetailResult): UiCourseDetail {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiCourseHeader(
                title = h.optString("title", ""),
                description = h.optString("description", ""),
                channelName = h.optString("channel_name", ""),
                channelId = h.optString("channel_id", ""),
                channelHandle = h.optString("channel_handle", ""),
                channelAvatarUrl = h.optString("channel_avatar_url", ""),
                channelAvatarsJson = h.optJSONArray("channel_avatars")?.toString() ?: "[]",
                thumbUrl = h.optString("thumbnail_url", ""),
                thumbsJson = h.optJSONArray("thumbnails")?.toString() ?: "[]",
                videoCountText = h.optString("video_count_text", ""),
                videoCount = h.optInt("video_count", 0),
                viewCountText = h.optString("view_count_text", ""),
                lastUpdatedText = h.optString("last_updated_text", ""),
                hasUnavailable = h.optBoolean("has_unavailable", false)
            )
        }
        val videos = mutableListOf<UiCourseVideo>()
        r.videosJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    videos.add(
                        UiCourseVideo(
                            videoId = o.optString("video_id", ""),
                            title = o.optString("title", ""),
                            channelName = o.optString("channel_name", ""),
                            channelId = o.optString("channel_id", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            durationText = o.optString("duration_text", ""),
                            indexText = o.optString("index_text", ""),
                            viewCountText = o.optString("view_count_text", ""),
                            publishedText = o.optString("published_time_text", ""),
                            isUnavailable = o.optBoolean("is_unavailable", false),
                            unavailableReason = o.optString("unavailable_reason", "")
                        )
                    )
                }
            }
        }
        return UiCourseDetail(header, videos, obj.optString("continuation", ""), obj.optString("playlist_id", ""))
    }

    suspend fun parseShowDetail(r: com.teamshryne.wediyo.wediyo.ShowDetailResult): UiShowDetail {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            val seasons = mutableListOf<UiShowSeason>()
            h.optJSONArray("seasons")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    seasons.add(UiShowSeason(o.optString("title"), o.optBoolean("selected"), o.optString("params"), o.optString("browse_id")))
                }
            }
            UiShowHeader(
                title = h.optString("title", ""),
                description = h.optString("description", ""),
                thumbUrl = h.optString("thumbnail_url", ""),
                thumbsJson = h.optJSONArray("thumbnails")?.toString() ?: "[]",
                seasonText = h.optString("season_text", ""),
                episodeCountText = h.optString("episode_count_text", ""),
                episodeCount = h.optInt("episode_count", 0),
                seasons = seasons,
                currentSeason = h.optString("current_season", ""),
                subtitle = h.optString("subtitle", ""),
                overlayTitle = h.optString("overlay_title", ""),
                overlaySubtitle = h.optString("overlay_subtitle", "")
            )
        }
        val episodes = mutableListOf<UiShowEpisode>()
        r.episodesJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    episodes.add(
                        UiShowEpisode(
                            videoId = o.optString("video_id", ""),
                            title = o.optString("title", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            durationText = o.optString("duration_text", ""),
                            durationSecs = o.optInt("duration_secs", 0),
                            indexText = o.optString("index_text", ""),
                            isUnplayable = o.optBoolean("is_unplayable", false)
                        )
                    )
                }
            }
        }
        return UiShowDetail(header, episodes, obj.optString("continuation", ""), obj.optString("playlist_id", ""))
    }

    private fun parsePlaylistDetail(r: com.teamshryne.wediyo.wediyo.PlaylistDetailResult): UiPlaylistDetail {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiPlaylistHeader(
                title = h.optString("title", ""),
                description = h.optString("description", ""),
                channelName = h.optString("channel_name", ""),
                channelId = h.optString("channel_id", ""),
                channelHandle = h.optString("channel_handle", ""),
                channelAvatarUrl = h.optString("channel_avatar_url", ""),
                channelAvatarsJson = h.optJSONArray("channel_avatars")?.toString() ?: "[]",
                thumbUrl = h.optString("thumbnail_url", ""),
                thumbsJson = h.optJSONArray("thumbnails")?.toString() ?: "[]",
                videoCountText = h.optString("video_count_text", ""),
                videoCount = h.optInt("video_count", 0),
                viewCountText = h.optString("view_count_text", ""),
                lastUpdatedText = h.optString("last_updated_text", ""),
                privacy = h.optString("privacy", ""),
                hasUnavailable = h.optBoolean("has_unavailable", false)
            )
        }
        val videos = mutableListOf<UiPlaylistVideo>()
        r.videosJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    videos.add(
                        UiPlaylistVideo(
                            videoId = o.optString("video_id", ""),
                            title = o.optString("title", ""),
                            channelName = o.optString("channel_name", ""),
                            channelId = o.optString("channel_id", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            durationText = o.optString("duration_text", ""),
                            indexText = o.optString("index_text", ""),
                            viewCountText = o.optString("view_count_text", ""),
                            publishedText = o.optString("published_time_text", ""),
                            isUnavailable = o.optBoolean("is_unavailable", false),
                            unavailableReason = o.optString("unavailable_reason", "")
                        )
                    )
                }
            }
        }
        val cont = obj.optString("continuation", "")
        val pid = obj.optString("playlist_id", "")
        return UiPlaylistDetail(header, videos, cont, pid)
    }

    private fun parseChannelAbout(r: com.teamshryne.wediyo.wediyo.ChannelAboutResult): UiChannelAboutResult {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiChannelHeader(
                channelId = h.optString("channel_id", ""),
                title = h.optString("title", ""),
                handle = h.optString("handle", ""),
                avatarUrl = h.optString("avatar_url", ""),
                avatarsJson = h.optJSONArray("avatars")?.toString() ?: "[]",
                bannerUrl = h.optString("banner_url", ""),
                bannersJson = h.optJSONArray("banners")?.toString() ?: "[]",
                subs = h.optString("subscriber_count_text", ""),
                videoCount = h.optString("video_count_text", ""),
                description = h.optString("description", ""),
                verified = h.optBoolean("verified", false),
                channelUrl = h.optString("channel_url", ""),
                rssUrl = h.optString("rss_url", ""),
                keywords = h.optString("keywords", "")
            )
        }
        val tabs = mutableListOf<UiChannelTab>()
        r.tabsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tabs.add(UiChannelTab(o.optString("title"), o.optBoolean("selected"), o.optString("params"), o.optString("browse_id"), o.optString("canonical_base_url")))
                }
            }
        }
        val aboutObj = obj.optJSONObject("about")
        val about = aboutObj?.let { a ->
            val links = mutableListOf<UiChannelAboutLink>()
            a.optJSONArray("links")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    links.add(
                        UiChannelAboutLink(
                            title = o.optString("title", ""),
                            url = o.optString("url", ""),
                            linkText = o.optString("link_text", ""),
                            faviconUrl = o.optString("favicon_url", ""),
                            faviconsJson = o.optJSONArray("favicons")?.toString() ?: "[]"
                        )
                    )
                }
            }
            UiChannelAbout(
                description = a.optString("description", ""),
                country = a.optString("country", ""),
                subscriberCountText = a.optString("subscriber_count_text", ""),
                viewCountText = a.optString("view_count_text", ""),
                joinedDateText = a.optString("joined_date_text", ""),
                canonicalUrl = a.optString("canonical_channel_url", ""),
                displayUrl = a.optString("display_canonical_channel_url", ""),
                channelId = a.optString("channel_id", ""),
                videoCountText = a.optString("video_count_text", ""),
                videoCount = a.optInt("video_count", 0),
                links = links
            )
        }
        return UiChannelAboutResult(header, tabs, about)
    }

    private fun parseChannelShows(r: com.teamshryne.wediyo.wediyo.ChannelShowsResult): UiChannelShows {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiChannelHeader(
                channelId = h.optString("channel_id", ""),
                title = h.optString("title", ""),
                handle = h.optString("handle", ""),
                avatarUrl = h.optString("avatar_url", ""),
                avatarsJson = h.optJSONArray("avatars")?.toString() ?: "[]",
                bannerUrl = h.optString("banner_url", ""),
                bannersJson = h.optJSONArray("banners")?.toString() ?: "[]",
                subs = h.optString("subscriber_count_text", ""),
                videoCount = h.optString("video_count_text", ""),
                description = h.optString("description", ""),
                verified = h.optBoolean("verified", false),
                channelUrl = h.optString("channel_url", ""),
                rssUrl = h.optString("rss_url", ""),
                keywords = h.optString("keywords", "")
            )
        }
        val tabs = mutableListOf<UiChannelTab>()
        r.tabsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tabs.add(UiChannelTab(o.optString("title"), o.optBoolean("selected"), o.optString("params"), o.optString("browse_id"), o.optString("canonical_base_url")))
                }
            }
        }
        val shows = mutableListOf<UiChannelShow>()
        r.showsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    shows.add(
                        UiChannelShow(
                            showId = o.optString("show_id", ""),
                            browseId = o.optString("browse_id", ""),
                            title = o.optString("title", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            subtitle = o.optString("subtitle", ""),
                            episodeCountText = o.optString("episode_count_text", ""),
                            episodeCount = o.optInt("episode_count", 0)
                        )
                    )
                }
            }
        }
        val cont = obj.optString("continuation", "")
        return UiChannelShows(header, tabs, shows, cont)
    }

    private fun parseChannelCourses(r: com.teamshryne.wediyo.wediyo.ChannelCoursesResult): UiChannelCourses {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiChannelHeader(
                channelId = h.optString("channel_id", ""),
                title = h.optString("title", ""),
                handle = h.optString("handle", ""),
                avatarUrl = h.optString("avatar_url", ""),
                avatarsJson = h.optJSONArray("avatars")?.toString() ?: "[]",
                bannerUrl = h.optString("banner_url", ""),
                bannersJson = h.optJSONArray("banners")?.toString() ?: "[]",
                subs = h.optString("subscriber_count_text", ""),
                videoCount = h.optString("video_count_text", ""),
                description = h.optString("description", ""),
                verified = h.optBoolean("verified", false),
                channelUrl = h.optString("channel_url", ""),
                rssUrl = h.optString("rss_url", ""),
                keywords = h.optString("keywords", "")
            )
        }
        val tabs = mutableListOf<UiChannelTab>()
        r.tabsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tabs.add(UiChannelTab(o.optString("title"), o.optBoolean("selected"), o.optString("params"), o.optString("browse_id"), o.optString("canonical_base_url")))
                }
            }
        }
        val courses = mutableListOf<UiChannelCourse>()
        r.coursesJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    courses.add(
                        UiChannelCourse(
                            playlistId = o.optString("playlist_id", ""),
                            browseId = o.optString("browse_id", ""),
                            title = o.optString("title", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            videoCountText = o.optString("video_count_text", ""),
                            videoCount = o.optInt("video_count", 0)
                        )
                    )
                }
            }
        }
        val cont = obj.optString("continuation", "")
        return UiChannelCourses(header, tabs, courses, cont)
    }

    private fun parseChannelStore(r: com.teamshryne.wediyo.wediyo.ChannelStoreResult): UiChannelStore {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiChannelHeader(
                channelId = h.optString("channel_id", ""),
                title = h.optString("title", ""),
                handle = h.optString("handle", ""),
                avatarUrl = h.optString("avatar_url", ""),
                avatarsJson = h.optJSONArray("avatars")?.toString() ?: "[]",
                bannerUrl = h.optString("banner_url", ""),
                bannersJson = h.optJSONArray("banners")?.toString() ?: "[]",
                subs = h.optString("subscriber_count_text", ""),
                videoCount = h.optString("video_count_text", ""),
                description = h.optString("description", ""),
                verified = h.optBoolean("verified", false),
                channelUrl = h.optString("channel_url", ""),
                rssUrl = h.optString("rss_url", ""),
                keywords = h.optString("keywords", "")
            )
        }
        val tabs = mutableListOf<UiChannelTab>()
        r.tabsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tabs.add(UiChannelTab(o.optString("title"), o.optBoolean("selected"), o.optString("params"), o.optString("browse_id"), o.optString("canonical_base_url")))
                }
            }
        }
        val products = mutableListOf<UiChannelStoreProduct>()
        r.productsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    products.add(
                        UiChannelStoreProduct(
                            title = o.optString("title", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            priceText = o.optString("price_text", ""),
                            merchantName = o.optString("merchant_name", ""),
                            fromText = o.optString("from_text", ""),
                            productUrl = o.optString("product_url", "")
                        )
                    )
                }
            }
        }
        val cont = obj.optString("continuation", "")
        return UiChannelStore(header, tabs, products, cont)
    }

    private fun parseChannelPosts(r: com.teamshryne.wediyo.wediyo.ChannelPostsResult): UiChannelPosts {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiChannelHeader(
                channelId = h.optString("channel_id", ""),
                title = h.optString("title", ""),
                handle = h.optString("handle", ""),
                avatarUrl = h.optString("avatar_url", ""),
                avatarsJson = h.optJSONArray("avatars")?.toString() ?: "[]",
                bannerUrl = h.optString("banner_url", ""),
                bannersJson = h.optJSONArray("banners")?.toString() ?: "[]",
                subs = h.optString("subscriber_count_text", ""),
                videoCount = h.optString("video_count_text", ""),
                description = h.optString("description", ""),
                verified = h.optBoolean("verified", false),
                channelUrl = h.optString("channel_url", ""),
                rssUrl = h.optString("rss_url", ""),
                keywords = h.optString("keywords", "")
            )
        }
        val tabs = mutableListOf<UiChannelTab>()
        r.tabsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tabs.add(UiChannelTab(o.optString("title"), o.optBoolean("selected"), o.optString("params"), o.optString("browse_id"), o.optString("canonical_base_url")))
                }
            }
        }
        val posts = mutableListOf<UiChannelPost>()
        r.postsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val pollObj = o.optJSONObject("poll")
                    val poll = pollObj?.let { p ->
                        val choices = mutableListOf<UiChannelPostPollChoice>()
                        val carr = p.optJSONArray("choices")
                        if (carr != null) {
                            for (j in 0 until carr.length()) {
                                val cj = carr.getJSONObject(j)
                                choices.add(UiChannelPostPollChoice(cj.optString("text", "")))
                            }
                        }
                        UiChannelPostPoll(choices, p.optString("total_votes_text", ""), p.optString("type", ""))
                    }
                    val images = mutableListOf<UiChannelPostImage>()
                    val iarr = o.optJSONArray("images")
                    if (iarr != null) {
                        for (j in 0 until iarr.length()) {
                            val ij = iarr.getJSONObject(j)
                            images.add(UiChannelPostImage(ij.optString("url", ""), ij.optJSONArray("thumbnails")?.toString() ?: "[]"))
                        }
                    }
                    var video: UiVideo? = null
                    o.optJSONObject("video")?.let { v ->
                        if (v.optString("id", "").isNotEmpty()) {
                            video = UiVideo(
                                id = v.optString("id", ""),
                                title = v.optString("title", ""),
                                author = v.optString("author", ""),
                                channelId = v.optString("channel_id", ""),
                                thumbnailUrl = v.optString("thumbnail_url", ""),
                                thumbnailsJson = v.optJSONArray("thumbnails")?.toString() ?: "[]",
                                avatarUrl = v.optString("channel_avatar_url", ""),
                                avatarsJson = v.optJSONArray("channel_avatars")?.toString() ?: "[]",
                                viewCountText = v.optString("view_count_text", ""),
                                publishedText = v.optString("published_time_text", ""),
                                durationText = v.optString("duration_text", ""),
                                isLive = v.optBoolean("is_live", false),
                                badges = emptyList(),
                                description = v.optString("description_snippet", "")
                            )
                        }
                    }
                    posts.add(
                        UiChannelPost(
                            postId = o.optString("post_id", ""),
                            authorText = o.optString("author_text", ""),
                            authorThumbUrl = o.optString("author_thumbnail_url", ""),
                            authorThumbsJson = o.optJSONArray("author_thumbnails")?.toString() ?: "[]",
                            contentText = o.optString("content_text", ""),
                            publishedTimeText = o.optString("published_time_text", ""),
                            voteCountText = o.optString("vote_count_text", ""),
                            voteCountLabel = o.optString("vote_count_label", ""),
                            attachmentType = o.optString("attachment_type", "none"),
                            poll = poll,
                            images = images,
                            video = video
                        )
                    )
                }
            }
        }
        val cont = obj.optString("continuation", "")
        return UiChannelPosts(header, tabs, posts, cont)
    }

    private fun parseChannelPlaylists(r: com.teamshryne.wediyo.wediyo.ChannelPlaylistsResult): UiChannelPlaylists {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiChannelHeader(
                channelId = h.optString("channel_id", ""),
                title = h.optString("title", ""),
                handle = h.optString("handle", ""),
                avatarUrl = h.optString("avatar_url", ""),
                avatarsJson = h.optJSONArray("avatars")?.toString() ?: "[]",
                bannerUrl = h.optString("banner_url", ""),
                bannersJson = h.optJSONArray("banners")?.toString() ?: "[]",
                subs = h.optString("subscriber_count_text", ""),
                videoCount = h.optString("video_count_text", ""),
                description = h.optString("description", ""),
                verified = h.optBoolean("verified", false),
                channelUrl = h.optString("channel_url", ""),
                rssUrl = h.optString("rss_url", ""),
                keywords = h.optString("keywords", "")
            )
        }
        val tabs = mutableListOf<UiChannelTab>()
        r.tabsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tabs.add(UiChannelTab(o.optString("title"), o.optBoolean("selected"), o.optString("params"), o.optString("browse_id"), o.optString("canonical_base_url")))
                }
            }
        }
        val playlists = mutableListOf<UiChannelPlaylist>()
        r.playlistsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    playlists.add(
                        UiChannelPlaylist(
                            playlistId = o.optString("playlist_id", ""),
                            browseId = o.optString("browse_id", ""),
                            title = o.optString("title", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            videoCountText = o.optString("video_count_text", ""),
                            videoCount = o.optInt("video_count", 0)
                        )
                    )
                }
            }
        }
        val cont = obj.optString("continuation", "")
        return UiChannelPlaylists(header, tabs, playlists, cont)
    }

    private fun parseChannelPodcasts(r: com.teamshryne.wediyo.wediyo.ChannelPodcastsResult): UiChannelPodcasts {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiChannelHeader(
                channelId = h.optString("channel_id", ""),
                title = h.optString("title", ""),
                handle = h.optString("handle", ""),
                avatarUrl = h.optString("avatar_url", ""),
                avatarsJson = h.optJSONArray("avatars")?.toString() ?: "[]",
                bannerUrl = h.optString("banner_url", ""),
                bannersJson = h.optJSONArray("banners")?.toString() ?: "[]",
                subs = h.optString("subscriber_count_text", ""),
                videoCount = h.optString("video_count_text", ""),
                description = h.optString("description", ""),
                verified = h.optBoolean("verified", false),
                channelUrl = h.optString("channel_url", ""),
                rssUrl = h.optString("rss_url", ""),
                keywords = h.optString("keywords", "")
            )
        }
        val tabs = mutableListOf<UiChannelTab>()
        r.tabsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tabs.add(UiChannelTab(o.optString("title"), o.optBoolean("selected"), o.optString("params"), o.optString("browse_id"), o.optString("canonical_base_url")))
                }
            }
        }
        val podcasts = mutableListOf<UiChannelPodcast>()
        r.podcastsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    podcasts.add(
                        UiChannelPodcast(
                            podcastId = o.optString("podcast_id", ""),
                            browseId = o.optString("browse_id", ""),
                            title = o.optString("title", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            episodeCountText = o.optString("episode_count_text", ""),
                            episodeCount = o.optInt("episode_count", 0),
                            updatedText = o.optString("updated_text", "")
                        )
                    )
                }
            }
        }
        val cont = obj.optString("continuation", "")
        return UiChannelPodcasts(header, tabs, podcasts, cont)
    }

    private fun parseChannelLive(r: com.teamshryne.wediyo.wediyo.ChannelLiveResult): UiChannelLive {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiChannelHeader(
                channelId = h.optString("channel_id", ""),
                title = h.optString("title", ""),
                handle = h.optString("handle", ""),
                avatarUrl = h.optString("avatar_url", ""),
                avatarsJson = h.optJSONArray("avatars")?.toString() ?: "[]",
                bannerUrl = h.optString("banner_url", ""),
                bannersJson = h.optJSONArray("banners")?.toString() ?: "[]",
                subs = h.optString("subscriber_count_text", ""),
                videoCount = h.optString("video_count_text", ""),
                description = h.optString("description", ""),
                verified = h.optBoolean("verified", false),
                channelUrl = h.optString("channel_url", ""),
                rssUrl = h.optString("rss_url", ""),
                keywords = h.optString("keywords", "")
            )
        }
        val tabs = mutableListOf<UiChannelTab>()
        r.tabsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tabs.add(UiChannelTab(o.optString("title"), o.optBoolean("selected"), o.optString("params"), o.optString("browse_id"), o.optString("canonical_base_url")))
                }
            }
        }
        val chips = mutableListOf<UiChannelVideoChip>()
        r.chipsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    chips.add(UiChannelVideoChip(o.optString("title"), o.optBoolean("selected"), o.optString("token")))
                }
            }
        }
        val lives = mutableListOf<UiVideo>()
        r.livesJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    lives.add(
                        UiVideo(
                            id = o.optString("id", ""),
                            title = o.optString("title", ""),
                            author = o.optString("author", ""),
                            channelId = o.optString("channel_id", ""),
                            thumbnailUrl = o.optString("thumbnail_url", ""),
                            thumbnailsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            avatarUrl = o.optString("channel_avatar_url", ""),
                            avatarsJson = o.optJSONArray("channel_avatars")?.toString() ?: "[]",
                            viewCountText = o.optString("view_count_text", ""),
                            publishedText = o.optString("published_time_text", ""),
                            durationText = o.optString("duration_text", ""),
                            isLive = o.optBoolean("is_live", false),
                            badges = o.optJSONArray("badges")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                            description = o.optString("description_snippet", "")
                        )
                    )
                }
            }
        }
        val cont = obj.optString("continuation", "")
        return UiChannelLive(header, tabs, chips, lives, cont)
    }

    private fun parseChannelShorts(r: com.teamshryne.wediyo.wediyo.ChannelShortsResult): UiChannelShorts {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiChannelHeader(
                channelId = h.optString("channel_id", ""),
                title = h.optString("title", ""),
                handle = h.optString("handle", ""),
                avatarUrl = h.optString("avatar_url", ""),
                avatarsJson = h.optJSONArray("avatars")?.toString() ?: "[]",
                bannerUrl = h.optString("banner_url", ""),
                bannersJson = h.optJSONArray("banners")?.toString() ?: "[]",
                subs = h.optString("subscriber_count_text", ""),
                videoCount = h.optString("video_count_text", ""),
                description = h.optString("description", ""),
                verified = h.optBoolean("verified", false),
                channelUrl = h.optString("channel_url", ""),
                rssUrl = h.optString("rss_url", ""),
                keywords = h.optString("keywords", "")
            )
        }
        val tabs = mutableListOf<UiChannelTab>()
        r.tabsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tabs.add(UiChannelTab(o.optString("title"), o.optBoolean("selected"), o.optString("params"), o.optString("browse_id"), o.optString("canonical_base_url")))
                }
            }
        }
        val chips = mutableListOf<UiChannelVideoChip>()
        r.chipsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    chips.add(UiChannelVideoChip(o.optString("title"), o.optBoolean("selected"), o.optString("token")))
                }
            }
        }
        val shorts = mutableListOf<UiShort>()
        r.shortsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    shorts.add(
                        UiShort(
                            videoId = o.optString("video_id", ""),
                            title = o.optString("title", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            views = o.optString("view_count_text", "")
                        )
                    )
                }
            }
        }
        val cont = obj.optString("continuation", "")
        return UiChannelShorts(header, tabs, chips, shorts, cont)
    }

    private fun parseChannelVideos(r: com.teamshryne.wediyo.wediyo.ChannelVideosResult): UiChannelVideos {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiChannelHeader(
                channelId = h.optString("channel_id", ""),
                title = h.optString("title", ""),
                handle = h.optString("handle", ""),
                avatarUrl = h.optString("avatar_url", ""),
                avatarsJson = h.optJSONArray("avatars")?.toString() ?: "[]",
                bannerUrl = h.optString("banner_url", ""),
                bannersJson = h.optJSONArray("banners")?.toString() ?: "[]",
                subs = h.optString("subscriber_count_text", ""),
                videoCount = h.optString("video_count_text", ""),
                description = h.optString("description", ""),
                verified = h.optBoolean("verified", false),
                channelUrl = h.optString("channel_url", ""),
                rssUrl = h.optString("rss_url", ""),
                keywords = h.optString("keywords", "")
            )
        }
        val tabs = mutableListOf<UiChannelTab>()
        r.tabsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tabs.add(UiChannelTab(o.optString("title"), o.optBoolean("selected"), o.optString("params"), o.optString("browse_id"), o.optString("canonical_base_url")))
                }
            }
        }
        val chips = mutableListOf<UiChannelVideoChip>()
        r.chipsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    chips.add(UiChannelVideoChip(o.optString("title"), o.optBoolean("selected"), o.optString("token")))
                }
            }
        }
        val videos = mutableListOf<UiVideo>()
        r.videosJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    videos.add(
                        UiVideo(
                            id = o.optString("id", ""),
                            title = o.optString("title", ""),
                            author = o.optString("author", ""),
                            channelId = o.optString("channel_id", ""),
                            thumbnailUrl = o.optString("thumbnail_url", ""),
                            thumbnailsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            avatarUrl = o.optString("channel_avatar_url", ""),
                            avatarsJson = o.optJSONArray("channel_avatars")?.toString() ?: "[]",
                            viewCountText = o.optString("view_count_text", ""),
                            publishedText = o.optString("published_time_text", ""),
                            durationText = o.optString("duration_text", ""),
                            isLive = o.optBoolean("is_live", false),
                            badges = o.optJSONArray("badges")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                            description = o.optString("description_snippet", "")
                        )
                    )
                }
            }
        }
        val cont = obj.optString("continuation", "")
        return UiChannelVideos(header, tabs, chips, videos, cont)
    }

    private fun parseChannelHome(r: com.teamshryne.wediyo.wediyo.ChannelHomeResult): UiChannelHome {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val headerObj = obj.optJSONObject("header")
        val header = headerObj?.let { h ->
            UiChannelHeader(
                channelId = h.optString("channel_id", ""),
                title = h.optString("title", ""),
                handle = h.optString("handle", ""),
                avatarUrl = h.optString("avatar_url", ""),
                avatarsJson = h.optJSONArray("avatars")?.toString() ?: "[]",
                bannerUrl = h.optString("banner_url", ""),
                bannersJson = h.optJSONArray("banners")?.toString() ?: "[]",
                subs = h.optString("subscriber_count_text", ""),
                videoCount = h.optString("video_count_text", ""),
                description = h.optString("description", ""),
                verified = h.optBoolean("verified", false),
                channelUrl = h.optString("channel_url", ""),
                rssUrl = h.optString("rss_url", ""),
                keywords = h.optString("keywords", "")
            )
        }
        val tabs = mutableListOf<UiChannelTab>()
        r.tabsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    tabs.add(
                        UiChannelTab(
                            title = o.optString("title", ""),
                            selected = o.optBoolean("selected", false),
                            params = o.optString("params", ""),
                            browseId = o.optString("browse_id", ""),
                            canonicalBase = o.optString("canonical_base_url", "")
                        )
                    )
                }
            }
        }
        val shelves = mutableListOf<UiChannelShelf>()
        r.shelvesJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    val title = s.optString("title", "")
                    val bid = s.optString("browse_id", "")
                    val vids = mutableListOf<UiVideo>()
                    val varr = s.optJSONArray("videos") ?: JSONArray()
                    for (j in 0 until varr.length()) {
                        val o = varr.getJSONObject(j)
                        vids.add(
                            UiVideo(
                                id = o.optString("id", ""),
                                title = o.optString("title", ""),
                                author = o.optString("author", ""),
                                channelId = o.optString("channel_id", ""),
                                thumbnailUrl = o.optString("thumbnail_url", ""),
                                thumbnailsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                                avatarUrl = o.optString("channel_avatar_url", ""),
                                avatarsJson = o.optJSONArray("channel_avatars")?.toString() ?: "[]",
                                viewCountText = o.optString("view_count_text", ""),
                                publishedText = o.optString("published_time_text", ""),
                                durationText = o.optString("duration_text", ""),
                                isLive = o.optBoolean("is_live", false),
                                badges = o.optJSONArray("badges")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                                description = o.optString("description_snippet", "")
                            )
                        )
                    }
                    shelves.add(UiChannelShelf(title, bid, vids))
                }
            }
        }
        return UiChannelHome(header, tabs, shelves)
    }

    private fun parse(r: com.teamshryne.wediyo.wediyo.SearchResult): UiSearchResult {
        val jsonStr = r.toJSON()
        val obj = JSONObject(jsonStr)
        val query = obj.optString("query", "")
        val cont = obj.optString("continuation", "")
        val est = obj.optString("estimated_results", "")
        // videos
        val videos = mutableListOf<UiVideo>()
        r.videosJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    videos.add(
                        UiVideo(
                            id = o.optString("id", ""),
                            title = o.optString("title", ""),
                            author = o.optString("author", ""),
                            channelId = o.optString("channel_id", ""),
                            thumbnailUrl = o.optString("thumbnail_url", ""),
                            thumbnailsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            avatarUrl = o.optString("channel_avatar_url", ""),
                            avatarsJson = o.optJSONArray("channel_avatars")?.toString() ?: "[]",
                            viewCountText = o.optString("view_count_text", ""),
                            publishedText = o.optString("published_time_text", ""),
                            durationText = o.optString("duration_text", ""),
                            isLive = o.optBoolean("is_live", false),
                            badges = o.optJSONArray("badges")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                            description = o.optString("description_snippet", "")
                        )
                    )
                }
            }
        }
        val channels = mutableListOf<UiChannel>()
        r.channelsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    channels.add(
                        UiChannel(
                            channelId = o.optString("channel_id", ""),
                            title = o.optString("title", ""),
                            handle = o.optString("handle", ""),
                            subs = o.optString("subscriber_count_text", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            desc = o.optString("description_snippet", ""),
                            verified = o.optBoolean("verified", false)
                        )
                    )
                }
            }
        }
        val shorts = mutableListOf<UiShort>()
        r.shortsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    shorts.add(
                        UiShort(
                            videoId = o.optString("video_id", ""),
                            title = o.optString("title", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            views = o.optString("view_count_text", "")
                        )
                    )
                }
            }
        }
        val playlists = mutableListOf<UiPlaylist>()
        r.playlistsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    playlists.add(
                        UiPlaylist(
                            playlistId = o.optString("playlist_id", ""),
                            title = o.optString("title", ""),
                            channelName = o.optString("channel_name", ""),
                            channelId = o.optString("channel_id", ""),
                            thumbUrl = o.optString("thumbnail_url", ""),
                            thumbsJson = o.optJSONArray("thumbnails")?.toString() ?: "[]",
                            countText = o.optString("video_count_text", ""),
                            count = o.optInt("video_count", 0),
                            isCourse = o.optBoolean("is_course", false),
                            badges = o.optJSONArray("badges")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
                        )
                    )
                }
            }
        }
        var topicTitle: String? = null; var topicBrowse: String? = null; var topicAvatar: String? = null
        var topicAvatarsJson: String? = null; var topicHandle: String? = null; var topicSubs: String? = null; var topicVideoCount: String? = null; var topicVerified = false
        obj.optJSONObject("topic_card")?.let { t ->
            topicTitle = t.optString("title", null)
            topicBrowse = t.optString("browse_id", null)
            topicAvatar = t.optString("avatar_url", null)
            topicAvatarsJson = t.optJSONArray("avatars")?.toString()
            topicHandle = t.optString("handle", null).takeIf { it.isNotEmpty() }
            topicSubs = t.optString("subscriber_count_text", null).takeIf { it.isNotEmpty() }
            topicVideoCount = t.optString("video_count_text", null).takeIf { it.isNotEmpty() }
            topicVerified = t.optBoolean("verified", false)
        }
        val chips = mutableListOf<UiChip>()
        r.chipsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    chips.add(UiChip(o.optString("title"), o.optBoolean("selected"), o.optString("token")))
                }
            }
        }
        val filterGroups = mutableListOf<UiFilterGroup>()
        r.filterGroupsJSON().let { js ->
            if (js != "[]") {
                val arr = JSONArray(js)
                for (i in 0 until arr.length()) {
                    val g = arr.getJSONObject(i)
                    val title = g.optString("title", "")
                    val flts = mutableListOf<UiFilter>()
                    val farr = g.optJSONArray("filters") ?: JSONArray()
                    for (j in 0 until farr.length()) {
                        val fo = farr.getJSONObject(j)
                        flts.add(UiFilter(fo.optString("label"), fo.optString("params")))
                    }
                    filterGroups.add(UiFilterGroup(title, flts))
                }
            }
        }
        return UiSearchResult(query, videos, channels, shorts, playlists, topicTitle, topicBrowse, topicAvatar, topicAvatarsJson, topicHandle, topicSubs, topicVideoCount, topicVerified, chips, filterGroups, cont, est)
    }
}
