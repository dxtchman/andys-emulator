package com.swordfish.lemuroid.app.mobile.feature.settings.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AndyThemeStore
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.AndyThemes
import com.swordfish.lemuroid.app.mobile.shared.compose.ui.ThemePreset
import kotlin.math.roundToInt

private enum class ColorSlot(val label: String) {
    ACCENT("Accent"),
    BACKGROUND("Background"),
    SURFACE("Cards & panels"),
}

@Composable
fun ThemeSettingsScreen(modifier: Modifier = Modifier) {
    val theme by AndyThemeStore.theme.collectAsState()
    var editing by remember { mutableStateOf(ColorSlot.ACCENT) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ThemePreview()

        Section("Presets") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AndyThemes.presets.forEach { preset ->
                    PresetSwatch(preset, selected = theme.presetId == preset.id) {
                        AndyThemeStore.applyPreset(preset)
                    }
                }
            }
        }

        Section("Custom colors") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorSlot.values().forEach { slot ->
                    val color =
                        when (slot) {
                            ColorSlot.ACCENT -> Color(theme.accent)
                            ColorSlot.BACKGROUND -> Color(theme.background)
                            ColorSlot.SURFACE -> Color(theme.surface)
                        }
                    SlotChip(slot.label, color, selected = editing == slot, Modifier.weight(1f)) {
                        editing = slot
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val current =
                when (editing) {
                    ColorSlot.ACCENT -> Color(theme.accent)
                    ColorSlot.BACKGROUND -> Color(theme.background)
                    ColorSlot.SURFACE -> Color(theme.surface)
                }
            ColorPicker(
                color = current,
                onColorChange = { c ->
                    when (editing) {
                        ColorSlot.ACCENT -> AndyThemeStore.setAccent(c)
                        ColorSlot.BACKGROUND -> AndyThemeStore.setBackground(c)
                        ColorSlot.SURFACE -> AndyThemeStore.setSurface(c)
                    }
                },
            )
        }

        Section("Roundness") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Slider(
                    value = theme.cornerRadius,
                    onValueChange = { AndyThemeStore.setCorner(it) },
                    valueRange = 0f..28f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape((theme.cornerRadius * 0.8f).dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

/** A tiny mock of the library so changes are visible right away. */
@Composable
private fun ThemePreview() {
    val colors = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = colors.background),
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Continue playing", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0.0f, 0.25f, 0.5f).forEach { tint ->
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(0.72f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        androidx.compose.ui.graphics.lerp(colors.primary, colors.surfaceContainerHighest, tint),
                                        colors.surfaceContainerHigh,
                                    ),
                                ),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(colors.primary)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Play", color = colors.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PresetSwatch(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp).clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(preset.theme.background))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF),
                    shape = RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(34.dp, 22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(preset.theme.surface)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(Color(preset.theme.accent)))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            preset.name,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun SlotChip(
    label: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.small)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Color(0x55FFFFFF), CircleShape),
        )
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
    }
}

private val quickColors =
    listOf(
        0xFFC77DFF, 0xFF5865F2, 0xFF66C0F4, 0xFF00E5FF, 0xFF4ADE80, 0xFFFACC15,
        0xFFFF8A3D, 0xFFFF2A6D, 0xFFFF7EB6, 0xFFFFFFFF, 0xFF1B2838, 0xFF000000,
    ).map { Color(it.toInt()) }

/** Hue / saturation / brightness bars plus quick swatches. */
@Composable
private fun ColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
) {
    val hsv = remember(color) { FloatArray(3).also { android.graphics.Color.colorToHSV(colorToInt(color), it) } }
    val hue = hsv[0]
    val sat = hsv[1]
    val value = hsv[2]

    fun emit(
        h: Float = hue,
        s: Float = sat,
        v: Float = value,
    ) = onColorChange(Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))))

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickColors.forEach { c ->
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c)
                        .border(1.dp, Color(0x55FFFFFF), CircleShape)
                        .clickable { onColorChange(c) },
                )
            }
        }
        GradientBar(
            label = "Hue",
            brush = Brush.horizontalGradient((0..6).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it * 60f, 1f, 1f))) }),
            position = hue / 360f,
        ) { emit(h = it * 360f) }
        GradientBar(
            label = "Saturation",
            brush =
                Brush.horizontalGradient(
                    listOf(
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0f, value))),
                        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, value))),
                    ),
                ),
            position = sat,
        ) { emit(s = it) }
        GradientBar(
            label = "Brightness",
            brush =
                Brush.horizontalGradient(
                    listOf(Color.Black, Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, 1f)))),
                ),
            position = value,
        ) { emit(v = it) }
        Text(
            "#" + Integer.toHexString(colorToInt(color)).uppercase().takeLast(6),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun colorToInt(c: Color): Int =
    android.graphics.Color.argb(
        (c.alpha * 255).roundToInt(),
        (c.red * 255).roundToInt(),
        (c.green * 255).roundToInt(),
        (c.blue * 255).roundToInt(),
    )

@Composable
private fun GradientBar(
    label: String,
    brush: Brush,
    position: Float,
    onChange: (Float) -> Unit,
) {
    val barHeight: Dp = 28.dp
    val thumb: Dp = 34.dp
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(thumb),
            contentAlignment = Alignment.CenterStart,
        ) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            fun toFraction(x: Float) = (x / widthPx).coerceIn(0f, 1f)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(barHeight / 2))
                    .background(brush)
                    .pointerInput(widthPx) {
                        detectTapGestures { onChange(toFraction(it.x)) }
                    }
                    .pointerInput(widthPx) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            onChange(toFraction(change.position.x))
                        }
                    },
            )
            Box(
                Modifier
                    .offset(x = (maxWidth - thumb) * position.coerceIn(0f, 1f))
                    .size(thumb)
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.15f)),
            )
        }
    }
}
