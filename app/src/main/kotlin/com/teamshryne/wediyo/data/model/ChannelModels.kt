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
