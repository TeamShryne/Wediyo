package com.teamshryne.wediyo.data.repository

import com.teamshryne.wediyo.data.engine.WediyoEngine
import com.teamshryne.wediyo.data.model.UiChannelHome

class ChannelRepository {
    suspend fun fetchHome(browseId: String): UiChannelHome = WediyoEngine.fetchChannelHome(browseId)
    suspend fun fetchVideos(browseId: String, continuation: String = ""): com.teamshryne.wediyo.data.model.UiChannelVideos =
        WediyoEngine.fetchChannelVideos(browseId, continuation)
}
