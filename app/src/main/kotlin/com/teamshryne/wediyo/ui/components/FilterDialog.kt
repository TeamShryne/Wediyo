package com.teamshryne.wediyo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.teamshryne.wediyo.data.model.UiFilterGroup
import com.teamshryne.wediyo.util.FilterParamsBuilder

@Composable
fun FilterDialog(
    groups: List<UiFilterGroup>,
    initialSelected: Map<String, Set<String>>,
    onDismiss: () -> Unit,
    onApply: (String) -> Unit
) {
    var selected by remember { mutableStateOf(initialSelected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search filters") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                groups.forEach { group ->
                    Text(group.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(vertical = 8.dp))
                    // single choice for TYPE/DURATION/UPLOAD DATE/PRIORITIZE, multi for FEATURES
                    val isMulti = group.title == "FEATURES"
                    group.filters.forEach { filter ->
                        val isSel = selected[group.title]?.contains(filter.label) == true
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (isMulti) {
                                    val cur = selected[group.title]?.toMutableSet() ?: mutableSetOf()
                                    if (isSel) cur.remove(filter.label) else cur.add(filter.label)
                                    selected.toMutableMap().apply { put(group.title, cur) }
                                } else {
                                    val cur = selected[group.title]
                                    if (cur?.contains(filter.label) == true) {
                                        selected.toMutableMap().apply { put(group.title, emptySet()) }
                                    } else {
                                        selected.toMutableMap().apply { put(group.title, setOf(filter.label)) }
                                    }
                                }
                            }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(filter.label, style = MaterialTheme.typography.bodyMedium, color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            if (isSel) Text("✓", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Divider(Modifier.padding(vertical = 8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val type = selected["TYPE"]?.firstOrNull() ?: selected["Type"]?.firstOrNull() ?: ""
                val dur = selected["DURATION"]?.firstOrNull() ?: selected["Duration"]?.firstOrNull() ?: ""
                val up = selected["UPLOAD DATE"]?.firstOrNull() ?: selected["Upload date"]?.firstOrNull() ?: ""
                val feats = selected["FEATURES"]?.toList() ?: selected["Features"]?.toList() ?: emptyList()
                val pri = selected["PRIORITIZE"]?.firstOrNull() ?: selected["Prioritize"]?.firstOrNull() ?: ""
                val params = FilterParamsBuilder.build(type, dur, up, feats, pri)
                onApply(params)
            }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = {
                selected = emptyMap()
                onApply("")
            }) { Text("Clear") }
        }
    )
}
