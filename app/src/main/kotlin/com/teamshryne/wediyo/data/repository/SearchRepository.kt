package com.teamshryne.wediyo.data.repository

import com.teamshryne.wediyo.data.engine.WediyoEngine
import com.teamshryne.wediyo.data.model.UiSearchResult

class SearchRepository {
    suspend fun search(query: String, params: String = "", continuation: String = ""): UiSearchResult {
        return WediyoEngine.search(query, continuation, params)
    }
    suspend fun searchChip(token: String): UiSearchResult = WediyoEngine.searchWithChip(token)
}
