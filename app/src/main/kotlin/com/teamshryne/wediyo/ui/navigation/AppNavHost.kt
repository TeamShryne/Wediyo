package com.teamshryne.wediyo.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.teamshryne.wediyo.ui.screens.channel.ChannelScreen
import com.teamshryne.wediyo.ui.screens.course.CourseScreen
import com.teamshryne.wediyo.ui.screens.home.HomeScreen
import com.teamshryne.wediyo.ui.screens.library.LibraryScreen
import com.teamshryne.wediyo.ui.screens.playlist.PlaylistScreen
import com.teamshryne.wediyo.ui.screens.podcast.PodcastScreen
import com.teamshryne.wediyo.ui.screens.search.SearchScreen
import com.teamshryne.wediyo.ui.screens.settings.SettingsScreen
import com.teamshryne.wediyo.ui.screens.shorts.ShortsScreen
import com.teamshryne.wediyo.ui.screens.show.ShowScreen
import com.teamshryne.wediyo.ui.screens.subscriptions.SubscriptionsScreen
import com.teamshryne.wediyo.ui.screens.video.VideoScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Shorts : Screen("shorts")
    object Subscriptions : Screen("subscriptions")
    object Library : Screen("library")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Channel : Screen("channel/{browseId}") {
        fun route(browseId: String) = "channel/$browseId"
    }
    object Playlist : Screen("playlist/{playlistId}") {
        fun route(playlistId: String) = "playlist/$playlistId"
    }
    object Course : Screen("course/{playlistId}") {
        fun route(playlistId: String) = "course/$playlistId"
    }
    object Show : Screen("show/{playlistId}") {
        fun route(playlistId: String) = "show/$playlistId"
    }
    object Podcast : Screen("podcast/{playlistId}") {
        fun route(playlistId: String) = "podcast/$playlistId"
    }
    object Video : Screen("video/{videoId}") {
        fun route(videoId: String) = "video/$videoId"
    }
    object ShortPlayer : Screen("short/{videoId}") {
        fun route(videoId: String) = "short/$videoId"
    }
}

@Composable
fun AppNavHost(nav: NavHostController, start: String = Screen.Home.route) {
    NavHost(navController = nav, startDestination = start) {
        composable(Screen.Home.route) {
            HomeScreen(
                onSearch = { nav.navigate(Screen.Search.route) },
                onSettings = { nav.navigate(Screen.Settings.route) },
                onShorts = { nav.navigate(Screen.Shorts.route) },
                onLibrary = { nav.navigate(Screen.Library.route) },
                onVideoClick = { vid -> nav.navigate(Screen.Video.route(vid)) },
                onShortClick = { vid -> nav.navigate(Screen.ShortPlayer.route(vid)) },
                onChannelClick = { bid -> nav.navigate(Screen.Channel.route(bid)) }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onBack = { nav.popBackStack() },
                onChannelClick = { browseId -> nav.navigate(Screen.Channel.route(browseId)) },
                onPlaylistClick = { pid -> nav.navigate(Screen.Playlist.route(pid)) },
                onCourseClick = { pid -> nav.navigate(Screen.Course.route(pid)) },
                onShowClick = { pid -> nav.navigate(Screen.Show.route(pid)) },
                onPodcastClick = { pid -> nav.navigate(Screen.Podcast.route(pid)) },
                onVideoClick = { vid -> nav.navigate(Screen.Video.route(vid)) },
                onShortClick = { vid -> nav.navigate(Screen.ShortPlayer.route(vid)) }
            )
        }
        composable(Screen.Channel.route) { backStackEntry ->
            val browseId = backStackEntry.arguments?.getString("browseId") ?: ""
            ChannelScreen(
                browseId = browseId,
                onBack = { nav.popBackStack() },
                onPlaylistClick = { pid -> nav.navigate(Screen.Playlist.route(pid)) },
                onCourseClick = { pid -> nav.navigate(Screen.Course.route(pid)) },
                onShowClick = { pid -> nav.navigate(Screen.Show.route(pid)) },
                onPodcastClick = { pid -> nav.navigate(Screen.Podcast.route(pid)) },
                onVideoClick = { vid -> nav.navigate(Screen.Video.route(vid)) },
                onShortClick = { vid -> nav.navigate(Screen.ShortPlayer.route(vid)) }
            )
        }
        composable(Screen.Playlist.route) { backStackEntry ->
            val pid = backStackEntry.arguments?.getString("playlistId") ?: ""
            PlaylistScreen(playlistId = pid, onBack = { nav.popBackStack() })
        }
        composable(Screen.Course.route) { backStackEntry ->
            val pid = backStackEntry.arguments?.getString("playlistId") ?: ""
            CourseScreen(playlistId = pid, onBack = { nav.popBackStack() })
        }
        composable(Screen.Show.route) { backStackEntry ->
            val pid = backStackEntry.arguments?.getString("playlistId") ?: ""
            ShowScreen(playlistId = pid, onBack = { nav.popBackStack() })
        }
        composable(Screen.Podcast.route) { backStackEntry ->
            val pid = backStackEntry.arguments?.getString("playlistId") ?: ""
            PodcastScreen(playlistId = pid, onBack = { nav.popBackStack() })
        }
        composable(
            Screen.Video.route,
            // Flow-like watch open/close: page rises from the miniplayer on open and
            // sinks back down on close (system back / swipe-down minimize).
            enterTransition = {
                slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = {
                slideOutOfContainer(
                    AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) { backStackEntry ->
            val vid = backStackEntry.arguments?.getString("videoId") ?: ""
            VideoScreen(
                videoId = vid,
                onBack = { nav.popBackStack() },
                onChannelClick = { bid -> nav.navigate(Screen.Channel.route(bid)) },
                // Up-next replaces the watch page (Flow/YouTube): back from the new video
                // minimizes into the miniplayer instead of reopening the previous video.
                onVideoClick = { nid ->
                    nav.navigate(Screen.Video.route(nid)) {
                        popUpTo(Screen.Video.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Screen.Settings.route) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.Shorts.route) {
            ShortsScreen(
                initialVideoId = null,
                onChannelClick = { bid -> nav.navigate(Screen.Channel.route(bid)) }
            )
        }
        composable(Screen.ShortPlayer.route) { backStackEntry ->
            val vid = backStackEntry.arguments?.getString("videoId") ?: ""
            ShortsScreen(
                initialVideoId = vid,
                onChannelClick = { bid -> nav.navigate(Screen.Channel.route(bid)) }
            )
        }
        composable(Screen.Subscriptions.route) {
            SubscriptionsScreen(
                onChannelClick = { bid -> nav.navigate(Screen.Channel.route(bid)) },
                onVideoClick = { vid -> nav.navigate(Screen.Video.route(vid)) },
                onShortClick = { vid -> nav.navigate(Screen.ShortPlayer.route(vid)) }
            )
        }
        composable(Screen.Library.route) {
            LibraryScreen(
                onVideoClick = { vid -> nav.navigate(Screen.Video.route(vid)) },
                onChannelClick = { bid -> nav.navigate(Screen.Channel.route(bid)) },
                onSubscriptionsClick = { nav.navigate(Screen.Subscriptions.route) },
                onSearchClick = { nav.navigate(Screen.Search.route) }
            )
        }
    }
}
