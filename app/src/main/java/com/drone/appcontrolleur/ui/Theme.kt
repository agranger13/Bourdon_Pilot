package com.drone.appcontrolleur.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.drone.appcontrolleur.R

/** Tokens de la maquette Claude Design « Drone RC - App Android », artboard 06. */
object DroneColors {
    // Ajusté sur écran réel : fond plus neutre, panneaux opaques (les transparences bavaient)
    val SurfaceBase = Color(0xFF05080C)
    val SurfaceMid = Color(0xFF080D14)
    val SurfaceRaised = Color(0xFF0E1826)
    val Glass = Color(0xF00C1520)
    val Cyan = Color(0xFF38E1FF)
    val CyanLight = Color(0xFF9FE8FF)
    val CyanDim = Color(0xFF0A7F96)
    val OnCyan = Color(0xFF03212A)
    val Ok = Color(0xFF38D98A)
    val OkLight = Color(0xFF7FE4B3)
    val Warn = Color(0xFFFFB020)
    val WarnLight = Color(0xFFFFCE74)
    val Danger = Color(0xFFFF3B47)
    val DangerLight = Color(0xFFFF8A92)
    val Idle = Color(0xFF5C6B7E)
    val TextPrimary = Color(0xFFE8F2F8)
    val TextSoft = Color(0xFFCFE3EF)
    val TextMuted = Color(0xFFA7B8C8)
    val TextFaint = Color(0xFF8798A9)
    val StrokeHud = Color(0x4778B4D2)
    val StrokeSoft = Color(0x3378B4D2)
}

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_semi_condensed_extrabold, FontWeight.ExtraBold),
)

// Police variable : la graisse est appliquée par l'axe « wght »
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold),
)

/** Échelle typographique : titres condensés, libellés en capitales espacées, télémétrie en chasse fixe. */
object DroneType {
    val display = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = DroneColors.TextPrimary,
    )

    fun caps(
        size: TextUnit,
        color: Color = DroneColors.TextMuted,
        weight: FontWeight = FontWeight.SemiBold,
        tracking: Float = 0.16f,
    ) = TextStyle(fontFamily = BarlowCondensed, fontWeight = weight, fontSize = size, letterSpacing = tracking.em, color = color)

    fun body(size: TextUnit, color: Color = DroneColors.TextPrimary, weight: FontWeight = FontWeight.Medium) =
        TextStyle(fontFamily = Barlow, fontWeight = weight, fontSize = size, color = color)

    fun mono(size: TextUnit, color: Color = DroneColors.TextPrimary, weight: FontWeight = FontWeight.Bold) =
        TextStyle(fontFamily = JetBrainsMono, fontWeight = weight, fontSize = size, color = color)
}

private val DroneColorScheme = darkColorScheme(
    primary = DroneColors.Cyan,
    onPrimary = DroneColors.OnCyan,
    error = DroneColors.Danger,
    background = DroneColors.SurfaceBase,
    onBackground = DroneColors.TextPrimary,
    surface = DroneColors.SurfaceMid,
    onSurface = DroneColors.TextPrimary,
    outline = DroneColors.StrokeHud,
)

private val DroneTypography = Typography().run {
    copy(
        titleMedium = titleMedium.copy(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontFamily = Barlow),
        bodyMedium = bodyMedium.copy(fontFamily = Barlow),
        bodySmall = bodySmall.copy(fontFamily = Barlow),
        labelLarge = labelLarge.copy(fontFamily = BarlowCondensed),
    )
}

@Composable
fun AppControlleurTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DroneColorScheme, typography = DroneTypography) {
        // Surface fournit la couleur de texte par défaut (sinon noir sur fond noir)
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}
