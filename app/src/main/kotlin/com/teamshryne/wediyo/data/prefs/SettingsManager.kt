package com.teamshryne.wediyo.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "wediyo_settings")

object SettingsKeys {
    val thumbQuality = stringPreferencesKey("thumb_quality") // high, 720p, 360p, low
    val avatarQuality = stringPreferencesKey("avatar_quality")
    val theme = stringPreferencesKey("theme") // system, light, dark
}

class SettingsManager(private val context: Context) {
    val thumbQuality: Flow<String> = context.dataStore.data.map { it[SettingsKeys.thumbQuality] ?: "high" }
    val avatarQuality: Flow<String> = context.dataStore.data.map { it[SettingsKeys.avatarQuality] ?: "high" }
    val theme: Flow<String> = context.dataStore.data.map { it[SettingsKeys.theme] ?: "system" }

    suspend fun setThumbQuality(v: String) { context.dataStore.edit { it[SettingsKeys.thumbQuality] = v } }
    suspend fun setAvatarQuality(v: String) { context.dataStore.edit { it[SettingsKeys.avatarQuality] = v } }
    suspend fun setTheme(v: String) { context.dataStore.edit { it[SettingsKeys.theme] = v } }
}
