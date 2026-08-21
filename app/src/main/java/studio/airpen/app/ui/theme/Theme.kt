package studio.airpen.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography

val Gold = Color(0xFFD4A84B)
val GoldDim = Color(0xFF8C6A22)
val Ink = Color(0xFF0E0F12)
val Panel = Color(0xFF181A20)
val Panel2 = Color(0xFF22242C)
val Mist = Color(0xFFE8E6E1)
val Teal = Color(0xFF3DDC97)

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A1408),
    secondary = Teal,
    background = Ink,
    surface = Panel,
    surfaceVariant = Panel2,
    onBackground = Mist,
    onSurface = Mist,
    outline = Color(0xFF3A3D48),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8C6A22),
    onPrimary = Color.White,
    secondary = Color(0xFF1F8A62),
    background = Color(0xFFF6F3EC),
    surface = Color.White,
    onBackground = Color(0xFF161616),
    onSurface = Color(0xFF161616),
)

val AirTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp),
)

@Composable
fun AirPenTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AirTypography,
        content = content,
    )
}
