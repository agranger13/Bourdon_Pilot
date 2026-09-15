package com.drone.appcontrolleur.ui.joystick

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val knobColor = MaterialTheme.colorScheme.primary

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
        val travel = travelRadius(size.width, size.height)
        val knobRadius = travel * KNOB_RATIO
        drawRoundRect(
            color = baseColor,
            topLeft = center - Offset(travel + knobRadius, travel + knobRadius),
            size = Size(2 * (travel + knobRadius), 2 * (travel + knobRadius)),
            cornerRadius = CornerRadius(knobRadius),
            style = Stroke(width = 3f),
        )
        drawLine(baseColor, center - Offset(travel, 0f), center + Offset(travel, 0f))
        drawLine(baseColor, center - Offset(0f, travel), center + Offset(0f, travel))
        drawCircle(
            color = knobColor,
            radius = knobRadius,
            center = center + Offset(value.x * travel, -value.y * travel),
        )
    }
}

private const val KNOB_RATIO = 0.35f

/** Course du centre du manche, pour que le manche reste dans la zone dessinée. */
private fun travelRadius(width: Float, height: Float) = min(width, height) / 2f / (1f + KNOB_RATIO)
