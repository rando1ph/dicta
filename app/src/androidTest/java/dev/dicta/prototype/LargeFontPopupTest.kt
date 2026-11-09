package dev.dicta.prototype

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.platform.Popup
import dev.dicta.prototype.query.LookupState
import dev.dicta.prototype.query.LookupViewModel
import dev.dicta.prototype.ui.DictaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Font scale override is scoped to this composition; no system setting changes. */
@RunWith(AndroidJUnit4::class)
class LargeFontPopupTest {
    @get:Rule val compose = createComposeRule()

    @Test fun transparentSideGuttersDismissButCardBodyDoesNot() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val model = LookupViewModel(application)
        val visible = mutableStateOf(true)
        compose.setContent {
            DictaTheme { if (visible.value) Popup(model, dismiss = { visible.value = false }, details = {}) }
        }
        compose.runOnIdle { model.lookup("ephemeral") }
        compose.waitUntil(65_000) { model.state.value is LookupState.Success }
        // A normal text tap within the card must not invoke the outside handler.
        compose.onNodeWithText("ephemeral").performTouchInput { click() }
        compose.onNodeWithTag("dictaPopup").assertIsDisplayed()
        compose.onNodeWithTag("popupTouchBounds").performTouchInput { click(Offset(1f, height / 2f)) }
        compose.onNodeWithTag("dictaPopup").assertDoesNotExist()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithTag("popupTouchBounds").performTouchInput { click(Offset(width - 1f, height / 2f)) }
        compose.onNodeWithTag("dictaPopup").assertDoesNotExist()
        compose.runOnIdle { model.cancel() }
    }

    @Test fun twoHundredPercentFontKeepsFooterAndCloseReachableWhileBodyScrolls() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val model = LookupViewModel(application)
        val visible = mutableStateOf(true)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DictaTheme {
                    if (visible.value) Popup(model, dismiss = { visible.value = false; model.cancel() }, details = {})
                }
            }
        }
        compose.runOnIdle { model.lookup("ephemeral") }
        compose.waitUntil(65_000) { model.state.value is LookupState.Success }
        compose.onNodeWithTag("popupDetails").assertIsDisplayed()
        compose.onNodeWithTag("closePopup").assertIsDisplayed()
        compose.onNodeWithTag("popupScroll").performTouchInput { swipeUp() }
        compose.onNodeWithTag("popupDetails").assertIsDisplayed()
        compose.onNodeWithTag("closePopup").performClick()
        compose.runOnIdle { assertTrue(model.state.value == LookupState.Cancelled) }
        compose.onNodeWithTag("dictaPopup").assertDoesNotExist()
    }
}
