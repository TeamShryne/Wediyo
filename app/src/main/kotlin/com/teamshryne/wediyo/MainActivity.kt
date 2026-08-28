package com.teamshryne.wediyo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.teamshryne.wediyo.ui.navigation.AppNavHost
import com.teamshryne.wediyo.ui.navigation.Screen
import com.teamshryne.wediyo.ui.theme.WediyoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WediyoTheme {
                val nav = rememberNavController()
                val backStack by nav.currentBackStackEntryAsState()
                val route = backStack?.destination?.route
                val showBottom = route in setOf(Screen.Home.route, Screen.Shorts.route, Screen.Subscriptions.route, Screen.Library.route)

                Scaffold(
                    bottomBar = {
                        if (showBottom) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = route == Screen.Home.route,
                                    onClick = { nav.navigate(Screen.Home.route) { popUpTo(Screen.Home.route) { inclusive = false }; launchSingleTop = true } },
                                    icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                                    label = { Text("Home") }
                                )
                                NavigationBarItem(
                                    selected = route == Screen.Shorts.route,
                                    onClick = { nav.navigate(Screen.Shorts.route) { launchSingleTop = true } },
                                    icon = { Icon(Icons.Filled.PlayCircle, contentDescription = "Shorts") },
                                    label = { Text("Shorts") }
                                )
                                NavigationBarItem(
                                    selected = route == Screen.Subscriptions.route,
                                    onClick = { nav.navigate(Screen.Subscriptions.route) { launchSingleTop = true } },
                                    icon = { Icon(Icons.Filled.Subscriptions, contentDescription = "Subs") },
                                    label = { Text("Subs") }
                                )
                                NavigationBarItem(
                                    selected = route == Screen.Library.route,
                                    onClick = { nav.navigate(Screen.Library.route) { launchSingleTop = true } },
                                    icon = { Icon(Icons.Filled.VideoLibrary, contentDescription = "Library") },
                                    label = { Text("You") }
                                )
                            }
                        }
                    }
                ) { inner ->
                    Box(Modifier.padding(inner)) {
                        AppNavHost(nav)
                    }
                }
            }
        }
    }
}
