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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import dev.dicta.prototype.ui.Appearance
import dev.dicta.prototype.ui.accentColors
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Font scale override is scoped to this composition; no system setting changes. */
@RunWith(AndroidJUnit4::class)
class LargeFontPopupTest {
    @get:Rule val compose = createComposeRule()


    @Test fun popupBookmarkUsesCurrentAccentAndSynchronizesSavedState() {
        val application=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val model=LookupViewModel(application)
        val originalAccent=Appearance.accent.value
        compose.setContent {DictaTheme {Popup(model,dismiss={},details={})}}
        compose.runOnIdle {model.lookup("ephemeral")}
        compose.waitUntil(65000) {model.state.value is LookupState.Success}
        val entry=(model.state.value as LookupState.Success).entry
        val originalSaved=model.vocabulary.value.any {it.id==entry.id}
        try {
            for(accent in listOf(0,2)) {
                compose.runOnIdle {Appearance.accent.value=accent;model.bookmarks(listOf(entry.id),true)}
                compose.waitUntil(5000) {model.vocabulary.value.any {it.id==entry.id}}
                compose.onNodeWithTag("popupSave").assertIsEnabled().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"已收藏"))
                compose.waitForIdle()
                val pixels=compose.onNodeWithTag("popupSave").captureToImage().toPixelMap()
                val expected=accentColors[accent]
                var matching=0
                for(y in 0 until pixels.height) for(x in 0 until pixels.width) {
                    val color=pixels[x,y]
                    if(kotlin.math.abs(color.red-expected.red)<.025 && kotlin.math.abs(color.green-expected.green)<.025 && kotlin.math.abs(color.blue-expected.blue)<.025) matching++
                }
                assertTrue("Saved button must use current Accent Colour",matching>pixels.width*pixels.height/3)
                compose.onNodeWithTag("popupSave").performClick()
                compose.waitUntil(5000) {model.vocabulary.value.none {it.id==entry.id}}
                compose.onNodeWithTag("popupSave").assertIsEnabled().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"未收藏"))
            }
        } finally {compose.runOnIdle {Appearance.accent.value=originalAccent;model.bookmarks(listOf(entry.id),originalSaved)}}
    }

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
