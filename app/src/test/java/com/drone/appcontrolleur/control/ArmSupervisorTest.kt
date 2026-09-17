package com.drone.appcontrolleur.control

import com.drone.appcontrolleur.control.ArmSupervisor.Action
import com.drone.appcontrolleur.link.DroneLink
import com.drone.appcontrolleur.link.DronePhase
import com.drone.appcontrolleur.protocol.DroneMessage
import com.drone.appcontrolleur.protocol.FlightState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmSupervisorTest {

    private val imuOk = DroneMessage.Telemetry.STATUS_IMU_OK

    private fun active(state: FlightState? = null, flags: Int = imuOk) = DroneLink(
        DronePhase.ACTIVE,
        telemetry = state?.let { DroneMessage.Telemetry(1, 0, 0, 0f, 0f, 0f, it, flags, 0, 250) },
    )

    @Test
    fun droneSilenceForcesLinkLost() {
        val sup = ArmSupervisor()
        sup.onLink(armed = true, active(), 0)
        val out = sup.onLink(armed = true, DroneLink(DronePhase.NO_RESPONSE), 10)
        assertEquals(Action.LINK_LOST, out.action)
        assertEquals("Liaison : le drone ne répond plus", out.message)
        // Retour du drone : aucun réarmement
        assertEquals(Action.NONE, sup.onLink(armed = false, active(FlightState.DISARMED), 20).action)
    }

    @Test
    fun stoppingTheLinkAlsoDisarms() {
        val sup = ArmSupervisor()
        sup.onLink(armed = true, active(), 0)
        assertEquals(Action.LINK_LOST, sup.onLink(armed = true, DroneLink(), 10).action)
    }

    @Test
    fun confirmedArmIsKept() {
        val sup = ArmSupervisor()
        assertEquals(Action.NONE, sup.onLink(true, active(FlightState.ARMED), 0).action)
        assertEquals(Action.NONE, sup.onLink(true, active(FlightState.ARMED), 5000).action)
    }

    @Test
    fun throttleRefusalDisarmsImmediately() {
        val sup = ArmSupervisor()
        sup.onLink(false, active(FlightState.DISARMED), 0)
        val flags = imuOk or DroneMessage.Telemetry.STATUS_ARM_REFUSED_THROTTLE
        val out = sup.onLink(true, active(FlightState.DISARMED, flags), 10)
        assertEquals(Action.DISARM, out.action)
        assertEquals("Armement refusé par le drone : gaz trop hauts", out.message)
    }

    @Test
    fun refusalFlagWithImuFaultBlamesImu() {
        val sup = ArmSupervisor()
        val out = sup.onLink(true, active(FlightState.DISARMED, DroneMessage.Telemetry.STATUS_ARM_REFUSED_THROTTLE), 0)
        assertEquals(Action.DISARM, out.action)
        assertEquals("Armement refusé par le drone : IMU absente ou en défaut", out.message)
    }

    @Test
    fun unconfirmedArmTimesOutWithReason() {
        val sup = ArmSupervisor(confirmTimeoutMs = 1000)
        sup.onLink(false, active(FlightState.DISARMED), 0)
        assertEquals(Action.NONE, sup.onLink(true, active(FlightState.DISARMED, flags = 0), 100).action)
        assertEquals(Action.NONE, sup.onLink(true, active(FlightState.DISARMED, flags = 0), 900).action)
        val out = sup.onLink(true, active(FlightState.DISARMED, flags = 0), 1100)
        assertEquals(Action.DISARM, out.action)
        assertEquals("Armement refusé par le drone : IMU absente ou en défaut", out.message)
    }

    @Test
    fun confirmationTimerRestartsOnEachArm() {
        val sup = ArmSupervisor(confirmTimeoutMs = 1000)
        sup.onLink(true, active(FlightState.DISARMED), 0)
        sup.onLink(false, active(FlightState.DISARMED), 500)
        assertEquals(Action.NONE, sup.onLink(true, active(FlightState.DISARMED), 1200).action)
    }

    @Test
    fun failsafeDisarms() {
        val sup = ArmSupervisor()
        sup.onLink(true, active(FlightState.ARMED), 0)
        val out = sup.onLink(true, active(FlightState.FAILSAFE), 10)
        assertEquals(Action.DISARM, out.action)
        assertTrue(out.message!!.startsWith("Failsafe"))
    }

    @Test
    fun withoutTelemetryNothingIsDecided() {
        val sup = ArmSupervisor()
        sup.onLink(true, active(), 0)
        val out = sup.onLink(true, active(), 10_000)
        assertEquals(Action.NONE, out.action)
        assertNull(out.message)
    }

    @Test
    fun incompatibleProtocolIsReported() {
        val sup = ArmSupervisor()
        val hello = DroneMessage.HelloAck(2, 0, 1, 0, 1)
        val out = sup.onLink(false, DroneLink(DronePhase.INCOMPATIBLE, hello), 0)
        assertEquals("Protocole incompatible : drone en v2, app en v1", out.message)
    }
}
