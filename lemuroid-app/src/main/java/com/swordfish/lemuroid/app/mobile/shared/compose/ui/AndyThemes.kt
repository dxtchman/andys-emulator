package com.swordfish.lemuroid.app.mobile.shared.compose.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A full app look: three colors plus how round things are. Everything else is derived. */
data class AndyTheme(
    val presetId: String?,
    val accent: Int,
    val background: Int,
    val surface: Int,
    val cornerRadius: Float,
) {
    val isDark: Boolean get() = Color(background).luminance() < 0.5f

    fun colorScheme(): ColorScheme {
        val accent = Color(accent)
        val bg = Color(background)
        val surf = Color(surface)
        val dark = isDark
        val onAccent = if (accent.luminance() > 0.45f) Color(0xFF111111) else Color.White
        val onBg = if (dark) Color(0xFFEDEDF2) else Color(0xFF17171C)
        val onBgVariant = if (dark) Color(0xFFA8A8B4) else Color(0xFF55555F)
        val lift = if (dark) Color.White else Color.Black
        fun raised(f: Float) = lerp(surf, lift, f)

        val base = if (dark) darkColorScheme() else lightColorScheme()
        return base.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = lerp(surf, accent, 0.35f),
            onPrimaryContainer = onBg,
            secondary = lerp(accent, lift, 0.25f),
            onSecondary = onAccent,
            secondaryContainer = lerp(surf, accent, 0.22f),
            onSecondaryContainer = onBg,
            tertiary = lerp(accent, Color(0xFF00E5FF), 0.4f),
            onTertiary = Color(0xFF111111),
            tertiaryContainer = lerp(surf, accent, 0.18f),
            onTertiaryContainer = onBg,
            background = bg,
            onBackground = onBg,
            surface = bg,
            onSurface = onBg,
            surfaceVariant = raised(0.06f),
            onSurfaceVariant = onBgVariant,
            surfaceTint = accent,
            inverseSurface = onBg,
            inverseOnSurface = bg,
            inversePrimary = lerp(accent, Color.Black, 0.3f),
            outline = lerp(onBgVariant, bg, 0.4f),
            outlineVariant = raised(0.15f),
            surfaceBright = raised(0.12f),
            surfaceDim = bg,
            surfaceContainerLowest = lerp(bg, surf, 0.3f),
            surfaceContainerLow = surf,
            surfaceContainer = raised(0.03f),
            surfaceContainerHigh = raised(0.07f),
            surfaceContainerHighest = raised(0.11f),
        )
    }

    fun shapes(): Shapes {
        val r = cornerRadius
        return Shapes(
            extraSmall = RoundedCornerShape((r * 0.4f).dp),
            small = RoundedCornerShape((r * 0.6f).dp),
            medium = RoundedCornerShape(r.dp),
            large = RoundedCornerShape((r * 1.3f).dp),
            extraLarge = RoundedCornerShape((r * 1.7f).dp),
        )
    }
}

data class ThemePreset(val id: String, val name: String, val theme: AndyTheme)

object AndyThemes {
    private fun preset(id: String, name: String, accent: Long, bg: Long, surface: Long, corner: Float = 16f) =
        ThemePreset(id, name, AndyTheme(id, accent.toInt(), bg.toInt(), surface.toInt(), corner))

    val presets =
        listOf(
            preset("andy", "Andy Purple", 0xFFC77DFF, 0xFF111014, 0xFF1C1A22),
            preset("steam", "Steam", 0xFF66C0F4, 0xFF171A21, 0xFF1B2838, 6f),
            preset("discord", "Discord", 0xFF5865F2, 0xFF313338, 0xFF2B2D31, 12f),
            preset("amoled", "AMOLED", 0xFF00E5FF, 0xFF000000, 0xFF0E0E10),
            preset("synthwave", "Synthwave", 0xFFFF2A6D, 0xFF120458, 0xFF1D0B6B, 20f),
            preset("forest", "Forest", 0xFF4ADE80, 0xFF0D1711, 0xFF15241B),
            preset("sunset", "Sunset", 0xFFFF8A3D, 0xFF1A110E, 0xFF2A1B15),
            preset("rose", "Rose", 0xFFFF7EB6, 0xFF1A1016, 0xFF261821, 22f),
            preset("light", "Light", 0xFF7B2CBF, 0xFFF4F2F8, 0xFFFFFFFF),
        )

    val default: AndyTheme get() = presets.first().theme
}

/** Saves the current theme and lets every screen react when it changes. */
object AndyThemeStore {
    private const val PREFS = "andy_theme"
    private var prefs: SharedPreferences? = null
    private val state = MutableStateFlow(AndyThemes.default)

    val theme: StateFlow<AndyTheme> = state.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        if (p.contains("accent")) {
            state.value =
                AndyTheme(
                    presetId = p.getString("preset", null),
                    accent = p.getInt("accent", AndyThemes.default.accent),
                    background = p.getInt("background", AndyThemes.default.background),
                    surface = p.getInt("surface", AndyThemes.default.surface),
                    cornerRadius = p.getFloat("corner", AndyThemes.default.cornerRadius),
                )
        }
    }

    fun update(theme: AndyTheme) {
        state.value = theme
        prefs?.edit()
            ?.putString("preset", theme.presetId)
            ?.putInt("accent", theme.accent)
            ?.putInt("background", theme.background)
            ?.putInt("surface", theme.surface)
            ?.putFloat("corner", theme.cornerRadius)
            ?.apply()
    }

    fun applyPreset(preset: ThemePreset) = update(preset.theme)

    fun setAccent(color: Color) = update(state.value.copy(presetId = null, accent = color.toArgb()))

    fun setBackground(color: Color) = update(state.value.copy(presetId = null, background = color.toArgb()))

    fun setSurface(color: Color) = update(state.value.copy(presetId = null, surface = color.toArgb()))

    fun setCorner(radius: Float) = update(state.value.copy(cornerRadius = radius))
}
