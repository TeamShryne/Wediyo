package com.teamshryne.wediyo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(onSearch: () -> Unit, onSettings: () -> Unit) {
    TopAppBar(
        title = { Text("Wediyo", style = MaterialTheme.typography.titleLarge) },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Search") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onBack: () -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    TopAppBar(
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) { Text("←", style = MaterialTheme.typography.titleLarge) }
        },
        actions = {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Go") }
        }
    )
}
