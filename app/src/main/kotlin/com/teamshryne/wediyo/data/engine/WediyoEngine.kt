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
        obj.optJSONObject("topic_card")?.let { t ->
            topicTitle = t.optString("title", null)
            topicBrowse = t.optString("browse_id", null)
            topicAvatar = t.optString("avatar_url", null)
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
        return UiSearchResult(query, videos, channels, shorts, playlists, topicTitle, topicBrowse, topicAvatar, chips, filterGroups, cont, est)
    }
}
