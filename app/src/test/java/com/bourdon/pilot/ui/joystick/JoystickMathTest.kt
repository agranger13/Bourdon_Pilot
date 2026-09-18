package com.bourdon.pilot.ui.joystick

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class JoystickMathTest {

    private val travel = 100f

    @Test
    fun touchWithoutMovingKeepsPosition() {
        val start = Offset(0.2f, -0.6f)
        assertEquals(start, JoystickMath.drag(start, Offset(40f, 70f), Offset(40f, 70f), travel))
    }

    @Test
    fun dragUpIncreasesYAndRightIncreasesX() {
        val result = JoystickMath.drag(Offset.Zero, Offset(0f, 0f), Offset(50f, -25f), travel)
        assertEquals(Offset(0.5f, 0.25f), result)
    }

    @Test
    fun axesAreClampedIndependently() {
        val result = JoystickMath.drag(Offset.Zero, Offset.Zero, Offset(500f, -500f), travel)
        assertEquals(Offset(1f, 1f), result) // coin : plein gaz et plein lacet possibles
    }

    @Test
    fun releaseReturnsOnlyConfiguredAxes() {
        val value = Offset(0.4f, 0.8f)
        assertEquals(Offset(0f, 0.8f), JoystickMath.release(value, returnX = true, returnY = false))
        assertEquals(Offset.Zero, JoystickMath.release(value, returnX = true, returnY = true))
    }

    @Test
    fun throttleMapping() {
        assertEquals(0f, JoystickMath.throttle(JoystickMath.THROTTLE_ZERO.y), 0f)
        assertEquals(0.5f, JoystickMath.throttle(0f), 0f)
        assertEquals(1f, JoystickMath.throttle(1f), 0f)
    }
}
