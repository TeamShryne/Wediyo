package com.teamshryne.wediyo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.common.util.UnstableApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.teamshryne.wediyo.data.prefs.SettingsManager
import com.teamshryne.wediyo.ui.navigation.AppNavHost
import com.teamshryne.wediyo.ui.navigation.Screen
import com.teamshryne.wediyo.ui.theme.WediyoTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restore sleep timer (alarm-backed, survives process death)
        try { com.teamshryne.wediyo.player.SleepTimerManager.init(this) } catch (_: Exception) {}
        enableEdgeToEdge()
        setContent {
            val ctx = LocalContext.current
            val settings = remember { SettingsManager(ctx) }
            var themePref by remember { mutableStateOf("system") }
            LaunchedEffect(Unit) { settings.theme.collectLatest { themePref = it } }
            val useDark = when (themePref) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            WediyoTheme(darkTheme = useDark) {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val showBottom = route in setOf(Screen.Home.route, Screen.Shorts.route, Screen.Subscriptions.route, Screen.Library.route)

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    // Remove default window insets — inner screens handle status bar padding themselves
                    // This fixes the "extra space above header" from nested scaffolds
                    contentWindowInsets = WindowInsets(0.dp),
                    bottomBar = {
                        if (showBottom) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 0.dp
                            ) {
                                NavigationBarItem(
                                    selected = route == Screen.Home.route,
                                    onClick = {
                                        nav.navigate(Screen.Home.route) {
                                            popUpTo(Screen.Home.route) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            if (route == Screen.Home.route) Icons.Filled.Home else Icons.Outlined.Home,
                                            contentDescription = "Home"
                                        )
                                    },
                                    label = { Text("Home", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                NavigationBarItem(
                                    selected = route == Screen.Shorts.route,
                                    onClick = { nav.navigate(Screen.Shorts.route) { launchSingleTop = true } },
                                    icon = {
                                        Icon(
                                            if (route == Screen.Shorts.route) Icons.Filled.PlayArrow else Icons.Outlined.PlayArrow,
                                            contentDescription = "Shorts"
                                        )
                                    },
                                    label = { Text("Shorts", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                NavigationBarItem(
                                    selected = route == Screen.Subscriptions.route,
                                    onClick = { nav.navigate(Screen.Subscriptions.route) { launchSingleTop = true } },
                                    icon = {
                                        Icon(
                                            if (route == Screen.Subscriptions.route) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                            contentDescription = "Subscriptions"
                                        )
                                    },
                                    label = { Text("Subscriptions", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                NavigationBarItem(
                                    selected = route == Screen.Library.route,
                                    onClick = { nav.navigate(Screen.Library.route) { launchSingleTop = true } },
                                    icon = {
                                        Icon(
                                            if (route == Screen.Library.route) Icons.Filled.Person else Icons.Outlined.Person,
                                            contentDescription = "You"
                                        )
                                    },
                                    label = { Text("You", style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                ) { inner ->
                    // Only apply bottom padding (nav bar) — top is handled inside each screen
                    // This eliminates the double-header gap
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(bottom = inner.calculateBottomPadding())
                    ) {
                        AppNavHost(nav)
                    }
                }
            }
        }
    }
}
