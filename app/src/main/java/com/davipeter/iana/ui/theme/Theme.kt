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

/**
 * Paleta da SyncForge, na versão escura (o "card escuro de destaque" da marca),
 * que combina com o painel estilo Jarvis.
 */
object Iana {
    val Fundo = Color(0xFF00072D) // azul-noite da marca
    val Painel = Color(0xFF0A1440) // superfícies um tom acima do fundo
    val Borda = Color(0xFF1F2A5C)
    val Texto = Color(0xFFEEF2FF)
    val Corpo = Color(0xFFC9D3F5)
    val Rotulo = Color(0xFF7E9DFF) // textos secundários e detalhes do HUD
    val AzulVivo = Color(0xFF2459F5) // brilhos e o núcleo do HUD
    val AzulMarca = Color(0xFF0A4DB8)
    val Erro = Color(0xFFFF8A8A)
}

private fun dmSans(weight: Int, opticalSize: Float) = Font(
    R.font.dm_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize)),
)

/** DM Sans para tudo, como no padrão da SyncForge. */
val DmSans = FontFamily(
    dmSans(400, 14f),
    dmSans(500, 14f),
    dmSans(700, 32f),
)

private val Colors = darkColorScheme(
    primary = Iana.AzulVivo,
    onPrimary = Iana.Texto,
    secondary = Iana.Rotulo,
    background = Iana.Fundo,
    onBackground = Iana.Texto,
    surface = Iana.Fundo,
    onSurface = Iana.Texto,
    surfaceVariant = Iana.Painel,
    onSurfaceVariant = Iana.Corpo,
    outline = Iana.Borda,
    outlineVariant = Iana.Borda,
    error = Iana.Erro,
)

private val Type = Typography(
    headlineMedium = TextStyle(fontFamily = DmSans, fontWeight = FontWeight(700), fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em),
    titleLarge = TextStyle(fontFamily = DmSans, fontWeight = FontWeight(700), fontSize = 20.sp, letterSpacing = 0.12.em),
    titleMedium = TextStyle(fontFamily = DmSans, fontWeight = FontWeight(500), fontSize = 19.sp, lineHeight = 27.sp),
    bodyLarge = TextStyle(fontFamily = DmSans, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = DmSans, fontWeight = FontWeight(400), fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = DmSans, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = DmSans, fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = DmSans, fontWeight = FontWeight(500), fontSize = 11.sp, letterSpacing = 0.08.em),
)

@Composable
fun IanaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, typography = Type, content = content)
}
