package com.teamshryne.wediyo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teamshryne.wediyo.data.model.UiChip

@Composable
fun ChipsRow(chips: List<UiChip>, onChipClick: (UiChip) -> Unit, onFilterClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // filter button
        Box(
            Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                .clickable { onFilterClick() }.padding(horizontal = 12.dp, vertical = 8.dp)
        ) { Icon(Icons.Default.Settings, contentDescription = "Filters") }
        chips.forEach { chip ->
            val bg = if (chip.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant
            val fg = if (chip.selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
            Box(
                Modifier.background(bg, RoundedCornerShape(8.dp))
                    .clickable { if (!chip.selected) onChipClick(chip) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(chip.title, color = fg, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
