package com.teamshryne.wediyo.ui.screens.subscriptions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.teamshryne.wediyo.data.local.LibraryRepository
import com.teamshryne.wediyo.data.local.SubscriptionRow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SubscriptionsViewModel(app: Application) : AndroidViewModel(app) {
    init { LibraryRepository.init(app) }

    val subscriptions: StateFlow<List<SubscriptionRow>> =
        LibraryRepository.subscriptions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun unsubscribe(channelId: String) = viewModelScope.launch { LibraryRepository.unsubscribe(channelId) }
}
