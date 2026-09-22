package com.anderson.wifiprevent.domain.traffic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureModeTest {
    @Test
    fun controlledModeOnlyRoutesDocumentationNetwork() {
        val plan = VpnCapturePlans.controlled
        assertEquals(listOf(VpnRoute("203.0.113.0", 24)), plan.routes)
        assertTrue(plan.generateValidationTraffic)
        assertFalse(plan.requiresPacketForwarder)
    }

    @Test
    fun fullModeCannotRunWithoutForwarder() {
        val plan = VpnCapturePlans.full
        assertTrue(plan.routes.contains(VpnRoute("0.0.0.0", 0)))
        assertFalse(plan.routes.contains(VpnRoute("::", 0)))
        assertTrue(plan.requiresPacketForwarder)
        assertFalse(plan.generateValidationTraffic)
    }
}
