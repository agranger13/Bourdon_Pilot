package com.drone.appcontrolleur.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlStateTest {

    @Test
    fun startsDisarmedWithZeroThrottle() {
        val state = ControlState()
        assertFalse(state.armed)
        assertEquals(0f, state.throttle, 0f)
    }

    @Test
    fun armRequiresZeroThrottleAndLink() {
        val throttleUp = ControlState().withLeftStick(yaw = 0f, throttle = 0.3f)
        assertEquals(ArmResult.REFUSED_THROTTLE, throttleUp.armResult(linkUp = true))
        assertFalse(throttleUp.tryArm(linkUp = true).armed)

        assertEquals(ArmResult.REFUSED_NO_LINK, ControlState().armResult(linkUp = false))
        assertTrue(ControlState().tryArm(linkUp = true).armed)
    }

    @Test
    fun emergencyStopLatchesUntilCleared() {
        val stopped = ControlState(throttle = 0.6f, armed = true).emergencyStopped()
        assertFalse(stopped.armed)
        assertTrue(stopped.estop)
        assertEquals(0f, stopped.throttle, 0f)

        // Gaz bloqués et armement refusé tant que le verrou est actif
        assertEquals(0f, stopped.withLeftStick(yaw = 0f, throttle = 0.5f).throttle, 0f)
        assertEquals(ArmResult.REFUSED_ESTOP, stopped.armResult(linkUp = true))

        val cleared = stopped.estopCleared()
        assertFalse(cleared.armed)
        assertEquals(ArmResult.ARMED, cleared.armResult(linkUp = true))
    }

    @Test
    fun linkLostDisarmsAndZeroesThrottle() {
        val lost = ControlState(roll = 0.2f, throttle = 0.7f, armed = true).linkLost()
        assertFalse(lost.armed)
        assertEquals(0f, lost.throttle, 0f)
    }

    @Test
    fun inputMirrorsState() {
        val input = ControlState(roll = 0.1f, pitch = -0.2f, yaw = 0.3f, throttle = 0.4f, armed = true).toInput()
        assertEquals(0.1f, input.roll, 0f)
        assertEquals(-0.2f, input.pitch, 0f)
        assertEquals(0.3f, input.yaw, 0f)
        assertEquals(0.4f, input.throttle, 0f)
        assertTrue(input.arm)
        assertFalse(input.estop)
    }
}
