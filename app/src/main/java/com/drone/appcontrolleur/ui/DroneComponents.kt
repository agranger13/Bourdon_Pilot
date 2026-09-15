package com.drone.appcontrolleur.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.max

/** Fond des écrans : dégradé radial depuis le haut (surface.raised vers surface.base). */
fun Modifier.screenBackground() = background(DroneColors.SurfaceBase).drawBehind {
    drawRect(
        Brush.radialGradient(
            0f to DroneColors.SurfaceRaised,
            0.45f to DroneColors.SurfaceMid,
            1f to DroneColors.SurfaceBase,
            center = Offset(size.width / 2, 0f),
            radius = max(size.width * 0.6f, 1f),
        ),
    )
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    color: Color = DroneColors.Glass,
    stroke: Color = DroneColors.StrokeSoft,
    content: @Composable BoxScope.() -> Unit,
) = Box(modifier.clip(shape).background(color).border(1.dp, stroke, shape), content = content)

@Composable
fun PanelDivider(height: Dp = 22.dp) =
    Box(Modifier.width(1.dp).height(height).background(DroneColors.StrokeHud))

enum class ButtonKind { PRIMARY, SECONDARY, DANGER }

/** Boutons de l'artboard 06 : normal, pressé, désactivé. */
@Composable
fun DroneButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    enabled: Boolean = true,
    height: Dp = 48.dp,
    fontSize: TextUnit = 15.sp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    var stroke = Color.Transparent
    val fill: Brush
    val fg: Color
    when (kind) {
        ButtonKind.PRIMARY -> {
            fill = when {
                !enabled -> SolidColor(DroneColors.Idle.copy(alpha = 0.22f))
                pressed -> Brush.verticalGradient(listOf(Color(0xFF2EB9D6), Color(0xFF1793AD)))
                else -> Brush.verticalGradient(listOf(Color(0xFF5FE9FF), Color(0xFF22BCDD)))
            }
            fg = if (enabled) DroneColors.OnCyan else DroneColors.Idle
        }
        ButtonKind.SECONDARY -> {
            fill = SolidColor(if (pressed) Color(0x2978B4D2) else Color.Transparent)
            stroke = if (enabled) Color(0x6678B4D2) else DroneColors.Idle.copy(alpha = 0.3f)
            fg = if (enabled) DroneColors.TextSoft else Color(0xFF4B586A)
        }
        ButtonKind.DANGER -> {
            fill = when {
                !enabled -> SolidColor(DroneColors.Danger.copy(alpha = 0.16f))
                pressed -> Brush.verticalGradient(listOf(Color(0xFFE0303C), Color(0xFF8D0A12)))
                else -> Brush.verticalGradient(listOf(Color(0xFFFF4A55), Color(0xFFC3121D)))
            }
            fg = if (enabled) Color.White else DroneColors.DangerLight.copy(alpha = 0.5f)
        }
    }
    Box(
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(fill)
            .border(1.5.dp, stroke, shape)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = DroneType.caps(fontSize, fg, FontWeight.Bold, tracking = 0.1f), maxLines = 1)
    }
}

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp) = Canvas(modifier.size(size)) {
    drawCircle(color.copy(alpha = 0.3f), radius = this.size.minDimension)
    drawCircle(color)
}

/** Icône d'état du lien : 4 barres, [level] barres allumées. */
@Composable
fun SignalBars(level: Int, color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        listOf(10, 12, 15, 18).forEachIndexed { i, h ->
            Box(
                Modifier.width(4.dp).height(h.dp).clip(RoundedCornerShape(1.dp))
                    .background(if (i < level) color else DroneColors.StrokeHud),
            )
        }
    }
}

@Composable
fun Spinner(color: Color, modifier: Modifier = Modifier, strokeWidth: Dp = 2.dp) {
    val rotation by rememberInfiniteTransition(label = "spinner").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rotation",
    )
    Canvas(modifier) {
        val w = strokeWidth.toPx()
        val arcSize = Size(size.width - w, size.height - w)
        drawArc(color.copy(alpha = 0.25f), 0f, 360f, false, topLeft = Offset(w / 2, w / 2), size = arcSize, style = Stroke(w))
        drawArc(color, rotation - 90f, 90f, false, topLeft = Offset(w / 2, w / 2), size = arcSize, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** État inactif : cercle en pointillés. */
@Composable
fun DashedCircle(color: Color, modifier: Modifier = Modifier) = Canvas(modifier) {
    val dash = 3.dp.toPx()
    drawCircle(
        color,
        radius = size.minDimension / 2 - 1.dp.toPx(),
        style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
    )
}

/** Pastille ronde avec un symbole (✓, ✕, !). [filled] : fond plein, sinon teinté et cerclé. */
@Composable
fun MarkIcon(symbol: String, color: Color, size: Dp, filled: Boolean = false) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.2f))
            .border(if (filled) 0.dp else 1.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = DroneType.body((size.value * 0.5f).sp, if (filled) Color(0xFF04231A) else color, FontWeight.Bold),
        )
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = color, dot: Boolean = true) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot) Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(text, style = DroneType.caps(10.sp, textColor, tracking = 0.08f), maxLines = 1)
    }
}

/** Bouton d'accès aux paramètres (roue dessinée, sans dépendance aux icônes Material). */
@Composable
fun GearButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(shape)
            .background(Color(0x0AFFFFFF))
            .border(1.dp, DroneColors.StrokeHud, shape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = "Paramètres", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val color = Color(0xFF9FB6C8)
            drawCircle(color, radius = 4.5.dp.toPx(), style = Stroke(1.6.dp.toPx()))
            val d = 7.dp.toPx()
            listOf(Offset(0f, -d), Offset(0f, d), Offset(-d, 0f), Offset(d, 0f)).forEach {
                drawCircle(color, radius = 1.6.dp.toPx(), center = center + it)
            }
        }
    }
}

private val OctagonShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.3f * w, 0f)
    lineTo(0.7f * w, 0f)
    lineTo(w, 0.3f * h)
    lineTo(w, 0.7f * h)
    lineTo(0.7f * w, h)
    lineTo(0.3f * w, h)
    lineTo(0f, 0.7f * h)
    lineTo(0f, 0.3f * h)
    close()
}

/**
 * F-SEC-2 : arrêt d'urgence, toujours au centre haut, hors zone des pouces.
 * Déclenché dès le contact, sans attendre le relâchement.
 */
@Composable
fun EmergencyStopButton(onStop: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnStop by rememberUpdatedState(onStop)
    var pressed by remember { mutableStateOf(false) }
    val fill = if (pressed) {
        listOf(Color(0xFFE0303C), Color(0xFF8D0A12))
    } else {
        listOf(Color(0xFFFF4A55), Color(0xFFC3121D))
    }
    Box(
        modifier = modifier
            .size(88.dp)
            .shadow(14.dp, OctagonShape, ambientColor = DroneColors.Danger, spotColor = DroneColors.Danger)
            .background(Brush.verticalGradient(fill), OctagonShape)
            .border(3.dp, Color(0x24FFFFFF), OctagonShape)
            .semantics {
                role = Role.Button
                onClick(label = "Arrêt d'urgence") { currentOnStop(); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    currentOnStop()
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("E-STOP", style = DroneType.caps(18.sp, Color.White, FontWeight.ExtraBold, tracking = 0.04f))
            Text("COUPURE", style = DroneType.caps(8.sp, Color(0xD1FFFFFF), tracking = 0.18f))
        }
    }
}

enum class ArmMode { READY, UNAVAILABLE, ARMED, ESTOP }

/**
 * Bouton rond ARM / DÉSARMER avec anneau de progression.
 * F-SEC-1 : armement par appui maintenu [ARM_HOLD_MS] ; désarmement dès l'appui.
 */
@Composable
fun ArmButton(
    mode: ArmMode,
    hint: String,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onResetEstop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val currentMode by rememberUpdatedState(mode)
    val currentOnArm by rememberUpdatedState(onArm)
    val currentOnDisarm by rememberUpdatedState(onDisarm)
    val currentOnResetEstop by rememberUpdatedState(onResetEstop)

    val look = when (mode) {
        ArmMode.READY -> ArmLook(
            DroneColors.Cyan, listOf(DroneColors.Cyan.copy(alpha = 0.18f), Color(0x99082834)),
            DroneColors.CyanLight, "ARM", "APPUI LONG 1 s", DroneColors.TextMuted,
        )
        ArmMode.ARMED -> ArmLook(
            DroneColors.Danger, listOf(DroneColors.Danger.copy(alpha = 0.24f), Color(0x80780C14)),
            DroneColors.DangerLight, "DÉSARMER", "APPUI COURT", DroneColors.DangerLight,
        )
        ArmMode.ESTOP -> ArmLook(
            DroneColors.Warn, listOf(DroneColors.Warn.copy(alpha = 0.18f), Color(0x993A2800)),
            DroneColors.WarnLight, "RÉINIT.", "E-STOP ACTIF", DroneColors.WarnLight,
        )
        ArmMode.UNAVAILABLE -> ArmLook(
            DroneColors.StrokeHud, listOf(Color(0xB81A222E), Color(0xB81A222E)),
            DroneColors.Idle, "ARM", "INDISPONIBLE", DroneColors.Warn,
        )
    }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .drawBehind {
                    val w = 4.dp.toPx()
                    val arcSize = Size(size.width - w, size.height - w)
                    drawCircle(Color(0x12FFFFFF), radius = size.minDimension / 2 - w / 2, style = Stroke(w))
                    drawArc(look.ring, -90f, 360f * progress.value, false, topLeft = Offset(w / 2, w / 2), size = arcSize, style = Stroke(w))
                }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        when (currentMode) {
                            ArmMode.ARMED -> currentOnDisarm()
                            ArmMode.ESTOP -> currentOnResetEstop()
                            ArmMode.UNAVAILABLE -> Unit
                            ArmMode.READY -> {
                                val hold = scope.launch {
                                    progress.snapTo(0f)
                                    progress.animateTo(1f, tween(ARM_HOLD_MS, easing = LinearEasing))
                                    currentOnArm()
                                }
                                tryAwaitRelease()
                                hold.cancel()
                                scope.launch { progress.snapTo(0f) }
                            }
                        }
                    })
                }
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxSize().clip(CircleShape)
                    .background(Brush.verticalGradient(look.fill))
                    .border(1.5.dp, look.ring, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(look.label, style = DroneType.caps(if (look.label.length > 4) 13.sp else 17.sp, look.fg, FontWeight.ExtraBold, 0.06f))
                    Text(look.sub, style = DroneType.caps(7.sp, look.fg.copy(alpha = 0.7f), tracking = 0.12f))
                }
            }
        }
        Text(hint, style = DroneType.body(11.sp, look.hintColor), maxLines = 1)
    }
}

private data class ArmLook(
    val ring: Color,
    val fill: List<Color>,
    val fg: Color,
    val label: String,
    val sub: String,
    val hintColor: Color,
)

private const val ARM_HOLD_MS = 1000
