package com.bourdon.pilot.ui.joystick

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.bourdon.pilot.ui.DroneColors
import kotlin.math.min

/**
 * Joystick virtuel à état remonté : [value] est la position normalisée (voir [JoystickMath]).
 * Chaque instance suit son propre doigt, donc deux joysticks s'utilisent simultanément (F-PIL-3).
 */
@Composable
fun Joystick(
    value: Offset,
    onValueChange: (Offset) -> Unit,
    modifier: Modifier = Modifier,
    returnX: Boolean = true,
    returnY: Boolean = true,
) {
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(returnX, returnY) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val start = currentValue
                    val travel = travelRadius(size.width.toFloat(), size.height.toFloat())
                    var last = start
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        last = JoystickMath.drag(start, down.position, change.position, travel)
                        currentOnValueChange(last)
                        change.consume()
                    }
                    currentOnValueChange(JoystickMath.release(last, returnX, returnY))
                }
            },
    ) {
        val radius = size.minDimension / 2f
        val travel = travelRadius(size.width, size.height)
        val knobRadius = travel * KNOB_RATIO
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))

        // Socle : disque, anneau pointillé, zone neutre et réticule
        drawCircle(Color(0xFF0A121C), radius)
        drawCircle(DroneColors.StrokeHud, radius - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        drawCircle(DroneColors.StrokeHud, radius * 0.855f, style = Stroke(1.dp.toPx(), pathEffect = dash))
        drawCircle(DroneColors.Cyan.copy(alpha = 0.5f), radius * 0.2f, style = Stroke(1.dp.toPx(), pathEffect = dash))
        drawLine(DroneColors.StrokeSoft, Offset(center.x, 0f), Offset(center.x, size.height))
        drawLine(DroneColors.StrokeSoft, Offset(0f, center.y), Offset(size.width, center.y))

        // Manche : aplat opaque et contour net, sans halo
        val knob = center + Offset(value.x * travel, -value.y * travel)
        drawCircle(
            Brush.verticalGradient(listOf(Color(0xFF2AA7C2), Color(0xFF0C4B5D)), startY = knob.y - knobRadius, endY = knob.y + knobRadius),
            knobRadius,
            knob,
        )
        drawCircle(DroneColors.CyanLight, knobRadius, knob, style = Stroke(1.5.dp.toPx()))
        drawCircle(Color(0xFFEAFCFF), knobRadius * 0.12f, knob)
    }
}

private const val KNOB_RATIO = 0.35f

/** Course du centre du manche, pour que le manche reste dans la zone dessinée. */
private fun travelRadius(width: Float, height: Float) = min(width, height) / 2f / (1f + KNOB_RATIO)
