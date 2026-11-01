@file:OptIn(ExperimentalTextApi::class)

package com.davipeter.iana.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.davipeter.iana.R

/** Paleta "noite e âmbar". */
object Iana {
    val Noite = Color(0xFF18121F) // fundo
    val Veludo = Color(0xFF231B2D) // superfícies
    val Linha = Color(0xFF3A2F47) // contornos
    val Lua = Color(0xFFF3ECE2) // texto principal
    val Nevoa = Color(0xFF9A8FA8) // texto secundário
    val Ambar = Color(0xFFF4B544) // a cor da IANA
    val AmbarClaro = Color(0xFFFFE2A3)
    val Lilas = Color(0xFFB8A4E3) // o que você disse
    val Coral = Color(0xFFFF8A7A) // erros
}

private fun bricolage(weight: Int, width: Float, opticalSize: Float) = Font(
    R.font.bricolage_grotesque,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.width(width),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

private fun onest(weight: Int) = Font(
    R.font.onest,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Bricolage Grotesque condensada: saudação e títulos. */
val Display = FontFamily(bricolage(700, 85f, 72f))

/** Logotipo "IANA": mais estreito e mais pesado. */
val Wordmark = FontFamily(bricolage(800, 75f, 24f))

/** Onest: todo o texto corrido. */
val Body = FontFamily(onest(400), onest(500), onest(600))

private val Colors = darkColorScheme(
    primary = Iana.Ambar,
    onPrimary = Iana.Noite,
    secondary = Iana.Lilas,
    onSecondary = Iana.Noite,
    background = Iana.Noite,
    onBackground = Iana.Lua,
    surface = Iana.Noite,
    onSurface = Iana.Lua,
    surfaceVariant = Iana.Veludo,
    onSurfaceVariant = Iana.Nevoa,
    surfaceContainerHighest = Iana.Linha,
    outline = Iana.Linha,
    outlineVariant = Iana.Linha,
    error = Iana.Coral,
)

// Escala em quartas (≈1,25): 12 · 14 · 16 · 19 · 24 · 44.
private val Type = Typography(
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight(700), fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-0.02).em),
    titleLarge = TextStyle(fontFamily = Wordmark, fontWeight = FontWeight(800), fontSize = 20.sp, letterSpacing = 0.04.em),
    headlineSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight(400), fontSize = 19.sp, lineHeight = 27.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = Body, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 18.sp),
)

@Composable
fun IanaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
