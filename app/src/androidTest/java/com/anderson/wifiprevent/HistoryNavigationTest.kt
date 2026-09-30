package com.anderson.wifiprevent

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.Build
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun allowLocalNetworkInTheTemporaryTestDevice() {
        if (Build.VERSION.SDK_INT >= 37) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(
                "pm grant com.anderson.wifiprevent android.permission.ACCESS_LOCAL_NETWORK"
            ).close()
        }
    }

    @Test
    fun oneHistoryDestinationRetainsTheSelectedCategoryAfterRecreation() {
        compose.onNode(hasText("Historial") and hasClickAction()).performClick()
        compose.onAllNodesWithText("Análisis").assertCountEquals(2)
        compose.onNodeWithText("Consultas rápidas").performClick().assertIsSelected()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Consultas rápidas").assertIsSelected()
        compose.onNode(hasText("Historial") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("Inicio") and hasClickAction()).performClick()
        compose.onNodeWithText("WiFiPrevent").assertIsDisplayed()
    }
}
