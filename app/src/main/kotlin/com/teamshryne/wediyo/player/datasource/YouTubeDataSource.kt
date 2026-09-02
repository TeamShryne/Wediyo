package com.teamshryne.wediyo.player.datasource

import android.content.Context
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.teamshryne.wediyo.player.cache.PlayerCacheManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object YouTubeDataSource {
    @Volatile private var okClient: OkHttpClient? = null
    private fun client(): OkHttpClient = okClient ?: synchronized(this) {
        okClient ?: OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build().also { okClient = it }
    }

    private fun baseHeaders(): Map<String, String> = mapOf(
        "Accept" to "*/*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Origin" to "https://www.youtube.com",
        "Referer" to "https://www.youtube.com/",
        "Sec-Fetch-Site" to "cross-site",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Dest" to "empty"
    )

    private fun uaFor(uri: Uri): String {
        val c = uri.getQueryParameter("c")?.uppercase() ?: return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        return when (c) {
            "ANDROID", "ANDROID_CREATOR" -> "com.google.android.youtube/19.29.37 (Linux; U; Android 14; en_US; Pixel 8 Build/AP1A.240505.004) gzip"
            "VISIONOS" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15"
            "MWEB" -> "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            else -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        }
    }

    fun factory(context: Context): DataSource.Factory {
        val okFactory = OkHttpDataSource.Factory(client())
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(baseHeaders())
        // Wrap with DefaultDataSource for content:// + file support, then cache
        val defaultDs = DefaultDataSource.Factory(context, okFactory)
        val cache = PlayerCacheManager.getCache(context)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(defaultDs)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun progressiveFactory(context: Context): DataSource.Factory = factory(context)

    fun upstreamFactory(context: Context): DataSource.Factory {
        val okFactory = OkHttpDataSource.Factory(client())
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
        return DefaultDataSource.Factory(context, okFactory)
    }

    fun subtitleFactory(context: Context): DataSource.Factory {
        // Subtitles are tiny; no cache needed but use same OkHttp for consistency
        return OkHttpDataSource.Factory(client())
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .setDefaultRequestProperties(baseHeaders())
    }
}
