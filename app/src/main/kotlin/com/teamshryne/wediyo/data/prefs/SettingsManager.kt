package com.teamshryne.wediyo.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
    val shortsQuality = stringPreferencesKey("shorts_quality") // auto, 1080, 720, 480, ...
    val videoQuality = stringPreferencesKey("video_quality") // auto, 1080, 720, 480, ...
    val captionLanguage = stringPreferencesKey("caption_language") // off, en, hi, etc
    val audioLanguage = stringPreferencesKey("audio_language") // original, en, hi, etc (id or display)
    val audioTrackId = stringPreferencesKey("audio_track_id") // last selected track id
    val historyPaused = booleanPreferencesKey("history_paused")
}

class SettingsManager(private val context: Context) {
    val thumbQuality: Flow<String> = context.dataStore.data.map { it[SettingsKeys.thumbQuality] ?: "high" }
    val avatarQuality: Flow<String> = context.dataStore.data.map { it[SettingsKeys.avatarQuality] ?: "high" }
    val theme: Flow<String> = context.dataStore.data.map { it[SettingsKeys.theme] ?: "system" }
    val shortsQuality: Flow<String> = context.dataStore.data.map { it[SettingsKeys.shortsQuality] ?: "auto" }
    val videoQuality: Flow<String> = context.dataStore.data.map { it[SettingsKeys.videoQuality] ?: "auto" }
    val captionLanguage: Flow<String> = context.dataStore.data.map { it[SettingsKeys.captionLanguage] ?: "off" }
    val audioLanguage: Flow<String> = context.dataStore.data.map { it[SettingsKeys.audioLanguage] ?: "original" }
    val audioTrackId: Flow<String> = context.dataStore.data.map { it[SettingsKeys.audioTrackId] ?: "" }
    val historyPaused: Flow<Boolean> = context.dataStore.data.map { it[SettingsKeys.historyPaused] ?: false }

    suspend fun setThumbQuality(v: String) { context.dataStore.edit { it[SettingsKeys.thumbQuality] = v } }
    suspend fun setAvatarQuality(v: String) { context.dataStore.edit { it[SettingsKeys.avatarQuality] = v } }
    suspend fun setTheme(v: String) { context.dataStore.edit { it[SettingsKeys.theme] = v } }
    suspend fun setShortsQuality(v: String) { context.dataStore.edit { it[SettingsKeys.shortsQuality] = v } }
    suspend fun setVideoQuality(v: String) { context.dataStore.edit { it[SettingsKeys.videoQuality] = v } }
    suspend fun setCaptionLanguage(v: String) { context.dataStore.edit { it[SettingsKeys.captionLanguage] = v } }
    suspend fun setAudioLanguage(v: String) { context.dataStore.edit { it[SettingsKeys.audioLanguage] = v } }
    suspend fun setAudioTrackId(v: String) { context.dataStore.edit { it[SettingsKeys.audioTrackId] = v } }
    suspend fun setHistoryPaused(v: Boolean) { context.dataStore.edit { it[SettingsKeys.historyPaused] = v } }
}
