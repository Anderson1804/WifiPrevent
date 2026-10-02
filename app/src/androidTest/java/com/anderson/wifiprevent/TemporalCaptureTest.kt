package com.anderson.wifiprevent

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anderson.wifiprevent.data.local.AnalysisSessionStore
import com.anderson.wifiprevent.domain.model.*
import com.anderson.wifiprevent.domain.traffic.CaptureMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class TemporalCaptureTest {
    private fun sample() = TemporalCapture("2026-09-30T12:00:00Z", 60_000, false, 0,
        listOf(TemporalObservation(500, "tcp", "other", 1, true)))

    @Test fun anonymousCaptureRoundTripsThroughAndroidJson() {
        val capture = sample()
        val encoded = capture.toJson().toString()
        assertEquals(capture, TemporalCapture.fromJson(JSONObject(encoded)))
        assertFalse(encoded.contains("ssid"))
        assertFalse(encoded.contains("192.168"))
    }

    @Test fun pendingCaptureSurvivesStoreRecreationWithoutTouchingRealPreferences() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val testName = "temporal-test-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                target.getSharedPreferences(testName, mode)
        }
        val id = UUID.randomUUID().toString()
        try {
            val store = AnalysisSessionStore(isolated)
            store.start(id, null, "WPA3_SAE", false, CaptureMode.FULL)
            store.updateRelayMetrics(id, RelayCaptureMetrics(1, 0, 0, 0, 0, 1, 1, sample()))
            store.complete()
            val restored = AnalysisSessionStore(isolated).snapshot()!!
            assertEquals(sample(), restored.relayMetrics.temporalCapture)
            assertFalse(restored.uploaded)
            store.markUploaded("another-session")
            assertFalse(store.snapshot()!!.uploaded)
        } finally { target.deleteSharedPreferences(testName) }
    }
}
