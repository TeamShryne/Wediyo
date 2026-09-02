package com.teamshryne.wediyo.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.teamshryne.wediyo.player.SleepTimerManager
import kotlinx.coroutines.delay

// ── Presets ───────────────────────────────────────────────────────────────────
private val PRESETS_MIN = listOf(5, 10, 15, 30, 45, 60, 90, 120)

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

private fun formatShort(ms: Long): String {
    if (ms <= 0) return "0m"
    val m = ms / 60_000
    return when {
        m < 60 -> "${m}m"
        m % 60 == 0L -> "${m / 60}h"
        else -> "${m / 60}h ${m % 60}m"
    }
}

// ── Main Sheet ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class, UnstableApi::class)
@Composable
fun SleepTimerSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val timerState by SleepTimerManager.state.collectAsState()

    // Local UI states
    var showCustomPicker by remember { mutableStateOf(false) }
    var customHours by remember { mutableIntStateOf(0) }
    var customMinutes by remember { mutableIntStateOf(30) }
    var fadeEnabled by remember { mutableStateOf(true) }

    // Sync fade with current timer if active
    LaunchedEffect(timerState.isActive) {
        if (timerState.isActive) fadeEnabled = timerState.fadeEnabled
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp) },
        modifier = modifier
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(40.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Timer, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sleep timer", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold))
                    Text(
                        when {
                            timerState.isActive && timerState.mode == SleepTimerManager.Mode.END_OF_VIDEO -> "Ends when video finishes • fade ${if (timerState.fadeEnabled) "on" else "off"}"
                            timerState.isActive -> "${formatMs(timerState.remainingMs)} left • ${if (timerState.isFading) "fading…" else "fades in last 30s"}"
                            else -> "Pause playback automatically"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, modifier = Modifier.size(20.dp)) }
            }

            if (timerState.isActive) {
                // ── ACTIVE STATE ──
                ActiveTimerCard(
                    state = timerState,
                    onCancel = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        SleepTimerManager.cancel(ctx)
                    },
                    onAdd5 = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        SleepTimerManager.extend(ctx, 5 * 60_000L)
                    },
                    onAdd10 = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        SleepTimerManager.extend(ctx, 10 * 60_000L)
                    },
                    onAdd15 = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        SleepTimerManager.extend(ctx, 15 * 60_000L)
                    }
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // Quick switch to other timer without cancelling first
                Text("Change timer", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(112.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                    items(PRESETS_MIN.take(8)) { mins ->
                        PresetChip(minutes = mins, isSelected = false, compact = true) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            SleepTimerManager.startMinutes(ctx, mins, fadeEnabled)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { showCustomPicker = true }, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Custom")
                    }
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            SleepTimerManager.startEndOfVideo(ctx, fadeEnabled)
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Stop, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("End of video")
                    }
                }
            } else {
                // ── INACTIVE STATE ──
                // Fade toggle
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(36.dp)) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Filled.VolumeDown, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Fade out", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            Text("Gradually lower volume in last 30 seconds", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = fadeEnabled, onCheckedChange = { fadeEnabled = it })
                    }
                }

                Text("Quick presets", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(112.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
                    items(PRESETS_MIN.take(8)) { mins ->
                        PresetChip(minutes = mins, isSelected = false, compact = true) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            SleepTimerManager.startMinutes(ctx, mins, fadeEnabled)
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(onClick = { showCustomPicker = true }, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Custom")
                    }
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            SleepTimerManager.startEndOfVideo(ctx, fadeEnabled)
                        },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("End of video")
                    }
                }

                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Info, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(
                            "Reliable even in background & screen-off. Uses exact alarm + fade. Extends instantly with +5 / +10.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }

    if (showCustomPicker) {
        CustomTimePickerSheet(
            initialHours = customHours,
            initialMinutes = customMinutes,
            fadeEnabled = fadeEnabled,
            onDismiss = { showCustomPicker = false },
            onConfirm = { h, m, fade ->
                customHours = h; customMinutes = m; fadeEnabled = fade
                val totalMs = (h * 3600 + m * 60) * 1000L
                if (totalMs > 0) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    SleepTimerManager.startDuration(ctx, totalMs, fade)
                }
                showCustomPicker = false
            }
        )
    }
}

@Composable
private fun ActiveTimerCard(
    state: SleepTimerManager.State,
    onCancel: () -> Unit,
    onAdd5: () -> Unit,
    onAdd10: () -> Unit,
    onAdd15: () -> Unit
) {
    val isEndOfVideo = state.mode == SleepTimerManager.Mode.END_OF_VIDEO
    val progress = if (state.totalMs > 0 && state.remainingMs >= 0) (1f - state.remainingMs.toFloat() / state.totalMs).coerceIn(0f, 1f) else 0.62f

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Circular progress + time
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
                val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.16f)
                val progColor = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
                    if (!isEndOfVideo) drawArc(color = progColor, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
                    else drawArc(color = progColor, startAngle = -90f, sweepAngle = 260f * progress, useCenter = false, style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isEndOfVideo) {
                        Icon(Icons.Filled.Stop, null, modifier = Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text("End of video", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
                        if (state.remainingMs >= 0) Text(formatMs(state.remainingMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                        if (state.isFading) Text("fading…", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                    } else {
                        TickingTime(remainingMs = state.remainingMs)
                        Text("remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        if (state.isFading) {
                            Spacer(Modifier.height(4.dp))
                            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary) {
                                Text("  fading out  ", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
            }

            if (!isEndOfVideo) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text("${formatShort(state.totalMs)} timer • ${if (state.fadeEnabled) "fade on" else "instant pause"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAdd5, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) { Text("+5m", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
                OutlinedButton(onClick = onAdd10, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) { Text("+10m", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
                OutlinedButton(onClick = onAdd15, shape = RoundedCornerShape(24.dp), modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) { Text("+15m", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)) }
            }
            Button(onClick = onCancel, shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Close, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Cancel timer", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun TickingTime(remainingMs: Long) {
    var tick by remember { mutableLongStateOf(remainingMs) }
    LaunchedEffect(remainingMs) { tick = remainingMs }
    // smooth per-second tick without needing parent recomposition every 500ms for text alone, but we already drive from manager
    Text(formatMs(tick), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.5).sp), color = MaterialTheme.colorScheme.onPrimaryContainer, textAlign = TextAlign.Center)
}

@Composable
private fun PresetChip(minutes: Int, isSelected: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    val label = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        tonalElevation = 0.dp
    ) {
        Column(Modifier.padding(vertical = if (compact) 10.dp else 12.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Filled.Timer, null, modifier = Modifier.size(if (compact) 16.dp else 18.dp), tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = if (compact) 13.sp else 14.sp), color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomTimePickerSheet(
    initialHours: Int,
    initialMinutes: Int,
    fadeEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (hours: Int, minutes: Int, fade: Boolean) -> Unit
) {
    var hours by remember { mutableIntStateOf(initialHours) }
    var minutes by remember { mutableIntStateOf(initialMinutes) }
    var fade by remember { mutableStateOf(fadeEnabled) }
    val totalMs = (hours * 3600 + minutes * 60) * 1000L
    val enabled = totalMs in 60_000L..(12 * 3600 * 1000L)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.ArrowBack, null, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(8.dp))
                Text("Custom timer", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.Close, null) }
            }

            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(formatMs(totalMs), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, fontSize = 26.sp), color = MaterialTheme.colorScheme.onSurface)
                    Text("hours · minutes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        // Hours
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Hours", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Stepper(value = hours, range = 0..12, onChange = { hours = it })
                        }
                        Box(Modifier.width(1.dp).height(72.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)))
                        // Minutes
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("Minutes", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Stepper(value = minutes, range = 0..59, onChange = { minutes = it }, step = 5)
                        }
                    }
                }
            }

            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Fade out", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                        Text("Lower volume gently before pause", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = fade, onCheckedChange = { fade = it })
                }
            }

            // quick custom presets
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(15, 30, 45, 60).forEach { m ->
                    FilterChip(selected = hours == 0 && minutes == m, onClick = { hours = 0; minutes = m }, label = { Text("${m}m") }, shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f))
                }
            }

            Button(
                onClick = { onConfirm(hours, minutes, fade) },
                enabled = enabled,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Text(if (enabled) "Start ${formatShort(totalMs)} timer" else "Select duration", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            }
            if (!enabled) Text("Choose between 1 minute and 12 hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Stepper(value: Int, range: IntRange, onChange: (Int) -> Unit, step: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FilledTonalIconButton(onClick = { onChange((value - step).coerceIn(range.first, range.last)) }, modifier = Modifier.size(40.dp), shape = CircleShape) { Icon(Icons.Filled.Remove, null, modifier = Modifier.size(18.dp)) }
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.width(56.dp).height(40.dp)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(String.format("%02d", value), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold)) }
        }
        FilledTonalIconButton(onClick = { onChange((value + step).coerceIn(range.first, range.last)) }, modifier = Modifier.size(40.dp), shape = CircleShape) { Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp)) }
    }
}

// ── Small indicators for player ─────────────────────────────────────────────

@Composable
fun SleepTimerIndicator(
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by SleepTimerManager.state.collectAsState()
    if (!state.isActive) return

    val text = when (state.mode) {
        SleepTimerManager.Mode.END_OF_VIDEO -> {
            if (state.remainingMs in 1..SleepTimerManager.FADE_DURATION_MS) "Sleep • fading…" else "Sleep • end of video"
        }
        else -> {
            val ms = state.remainingMs.coerceAtLeast(0L)
            "Sleep ${formatMs(ms)}"
        }
    }

    val bg = if (state.isFading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (state.isFading) Color.White else MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier = modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        tonalElevation = 2.dp
    ) {
        Row(Modifier.padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 6.dp else 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Bedtime, null, modifier = Modifier.size(if (compact) 14.dp else 16.dp), tint = fg)
            Text(text, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = if (compact) 11.sp else 12.sp), color = fg, maxLines = 1)
            if (state.isActive && !compact) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF00C853)))
            }
        }
    }
}

@Composable
fun SleepTimerMiniChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val state by SleepTimerManager.state.collectAsState()
    val isActive = state.isActive
    val label = if (isActive) {
        when (state.mode) {
            SleepTimerManager.Mode.END_OF_VIDEO -> "End • video"
            else -> formatMs(state.remainingMs.coerceAtLeast(0L))
        }
    } else "Sleep timer"

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
        modifier = modifier.height(36.dp)
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                if (isActive) Icons.Filled.Bedtime else Icons.Outlined.Timer,
                null,
                modifier = Modifier.size(18.dp),
                tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium), color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (isActive) Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        }
    }
}
