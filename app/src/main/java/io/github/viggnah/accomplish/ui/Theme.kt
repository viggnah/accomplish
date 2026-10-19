package io.github.viggnah.accomplish.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.viggnah.accomplish.R

/** The "Sorbet" palette. Card colours are gradient pairs; card text is always dark. */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color, val bg2: Color, val surface: Color, val pebble: Color, val pool: Color, val water: Color,
    val ink: Color, val inkSoft: Color, val cardInk: Color, val line: Color, val scrim: Color, val chip: Color,
    val heat: Color, val accent: Color, val onAccent: Color, val dotBig: Color, val dotSmall: Color,
    val hi: Color, val lo: Color, val drop: Color,
    val frostA: Color, val frostB: Color, val frostSpark: Color,
    /** Hint text in empty fields, and a faint outline that separates cards from a dark background. */
    val placeholder: Color, val edge: Color,
    /** Deadlines that are today, tomorrow or past: a fill for chips on cards, and a text colour for the page. */
    val urgent: Color, val urgentInk: Color,
    /** How far card colours lean toward the background; dark mode tones them down so they don't glare. */
    val cardDim: Float,
    val cards: List<Pair<Color, Color>>,
    val waitingMix: Float,
) {
    fun card(i: Int) = cards[Math.floorMod(i, cards.size)]
    /** One flat colour per card, a little softer than the most saturated end of the pair. */
    fun cardColor(i: Int) = card(i).let { androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.lerp(it.first, it.second, 0.3f), bg, cardDim) }
    fun cardBrush(i: Int): Brush = androidx.compose.ui.graphics.SolidColor(cardColor(i))
}

private val sorbetCards = listOf(
    Color(0xFFFF7EB6) to Color(0xFFFFB1D8), // rose
    Color(0xFFFFD84D) to Color(0xFFFFF09A), // lemon
    Color(0xFF3DDBB8) to Color(0xFF9AF2DA), // aqua
    Color(0xFF4FB7FF) to Color(0xFF9ED8FF), // sky
    Color(0xFF8F6BFF) to Color(0xFFC0A8FF), // violet
)
val COLOR_NAMES = listOf("Rose", "Lemon", "Aqua", "Sky", "Violet")

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF6F4FF), bg2 = Color(0xFFECE8FB), surface = Color(0xFFFFFFFF), pebble = Color(0xFFFFFFFF),
    pool = Color(0xFFE7F3FF), water = Color(0xFFCFE6FF),
    ink = Color(0xFF14112B), inkSoft = Color(0xFF6A6585), cardInk = Color(0xFF14112B), line = Color(0xFFE1DCF5),
    scrim = Color(0x5914112B), chip = Color(0x8CFFFFFF),
    heat = Color(0xFFFF7EB6), accent = Color(0xFF6C47FF), onAccent = Color.White, dotBig = Color(0xFF6C47FF), dotSmall = Color(0xFF6A6585),
    hi = Color(0x99FFFFFF), lo = Color(0x24281A5A), drop = Color(0x405A3CC8),
    frostA = Color(0xEBF4FAFF), frostB = Color(0xB3C4DCF8), frostSpark = Color(0xF7FFFFFF),
    placeholder = Color(0xFF9792B0), edge = Color.Transparent,
    urgent = Color(0xFFE5484D), urgentInk = Color(0xFFD13438), cardDim = 0f,
    cards = sorbetCards, waitingMix = 0.6f,
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF12101F), bg2 = Color(0xFF2D2949), surface = Color(0xFF221E3B), pebble = Color(0xFF38325E),
    pool = Color(0xFF1C2852), water = Color(0xFF2C4073),
    ink = Color(0xFFF7F4FF), inkSoft = Color(0xFFC6C0E3), cardInk = Color(0xFF14112B), line = Color(0xFF433D6B),
    scrim = Color(0x8C000000), chip = Color(0x6BFFFFFF),
    heat = Color(0xFFE86FA8), accent = Color(0xFF8F6BFF), onAccent = Color.White, dotBig = Color(0xFFAE97FF), dotSmall = Color(0xFFC6C0E3),
    hi = Color(0x38FFFFFF), lo = Color(0x40000000), drop = Color(0x99000000),
    frostA = Color(0xB3D5E2F5), frostB = Color(0x99ACC8EC), frostSpark = Color(0xC7DCE5F7),
    placeholder = Color(0xFF9F99C4), edge = Color(0x24FFFFFF),
    urgent = Color(0xFFE5484D), urgentInk = Color(0xFFFF8589), cardDim = 0.16f,
    cards = sorbetCards, waitingMix = 0.8f,
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

val Fredoka = FontFamily(Font(R.font.fredoka_semibold, FontWeight.SemiBold))
val Nunito = FontFamily(
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

/**
 * One family, Nunito, on a fixed scale (12 · 14 · 16 · 18 · 24 · 36). Fredoka appears in exactly one place:
 * the titles of IMP cards, so the cards are the memorable thing on the screen.
 */
object Type {
    fun display(size: TextUnit) = TextStyle(
        fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = size, lineHeight = size * 1.15f,
        letterSpacing = if (size.value >= 24f) (-0.4).sp else 0.sp,
    )
    fun card(size: TextUnit) = TextStyle(fontFamily = Fredoka, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size * 1.15f)
    fun body(size: TextUnit = 16.sp, weight: FontWeight = FontWeight.SemiBold) =
        TextStyle(fontFamily = Nunito, fontWeight = weight, fontSize = size, lineHeight = size * 1.4f)
    /** Section labels: sentence case, set apart by weight, not capitals. */
    val label = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, lineHeight = 18.sp)
}

@Composable
fun pal(): Palette = LocalPalette.current
