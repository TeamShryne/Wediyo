package com.teamshryne.wediyo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.teamshryne.wediyo.ui.screens.channel.ChannelScreen
import com.teamshryne.wediyo.ui.screens.course.CourseScreen
import com.teamshryne.wediyo.ui.screens.home.HomeScreen
import com.teamshryne.wediyo.ui.screens.library.LibraryScreen
import com.teamshryne.wediyo.ui.screens.playlist.PlaylistScreen
import com.teamshryne.wediyo.ui.screens.search.SearchScreen
import com.teamshryne.wediyo.ui.screens.settings.SettingsScreen
import com.teamshryne.wediyo.ui.screens.shorts.ShortsScreen
import com.teamshryne.wediyo.ui.screens.show.ShowScreen
import com.teamshryne.wediyo.ui.screens.subscriptions.SubscriptionsScreen

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
}

@Composable
fun AppNavHost(nav: NavHostController, start: String = Screen.Home.route) {
    NavHost(navController = nav, startDestination = start) {
        composable(Screen.Home.route) { HomeScreen(onSearch = { nav.navigate(Screen.Search.route) }, onSettings = { nav.navigate(Screen.Settings.route) }) }
        composable(Screen.Search.route) {
            SearchScreen(
                onBack = { nav.popBackStack() },
                onChannelClick = { browseId -> nav.navigate(Screen.Channel.route(browseId)) }
            )
        }
        composable(Screen.Channel.route) { backStackEntry ->
            val browseId = backStackEntry.arguments?.getString("browseId") ?: ""
            ChannelScreen(
                browseId = browseId,
                onBack = { nav.popBackStack() },
                onPlaylistClick = { pid -> nav.navigate(Screen.Playlist.route(pid)) },
                onCourseClick = { pid -> nav.navigate(Screen.Course.route(pid)) },
                onShowClick = { pid -> nav.navigate(Screen.Show.route(pid)) }
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
        composable(Screen.Settings.route) { SettingsScreen(onBack = { nav.popBackStack() }) }
        composable(Screen.Shorts.route) { ShortsScreen() }
        composable(Screen.Subscriptions.route) { SubscriptionsScreen() }
        composable(Screen.Library.route) { LibraryScreen() }
    }
}
