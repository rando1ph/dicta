package dev.dicta.prototype

import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Run at 840x1920 / 420 dpi (320dp), font_scale=2.0; no production preferences altered. */
class T07ResponsiveTest:T07MockFixture() {
    @Test fun narrowLargeFontUsesAccessibleIconsAndKeepsAllScreensAndProviderActionsReachable() {
        val config=compose.activity.resources.configuration
        assumeTrue(config.screenWidthDp<=360 && config.fontScale>=1.8f)
        for(tab in listOf("Search","Vocabulary","History","Settings")) {
            compose.onNodeWithTag("nav$tab").assertIsDisplayed().assertHasClickAction().assertContentDescriptionEquals(tab)
            compose.onNodeWithText(tab,useUnmergedTree=true).assertDoesNotExist()
        }
        val entry=seed()
        compose.waitUntil(5000) {repo.vocabulary.value.any {it.id==entry.id}}
        compose.onNodeWithTag("navVocabulary").performClick()
        compose.onNodeWithTag("row${entry.id}").performClick();awaitSuccess()
        compose.onNodeWithTag("detailBack").assertIsDisplayed().performClick()
        compose.onNodeWithTag("navVocabulary").assertIsSelected()
        compose.onNodeWithTag("navHistory").performClick()
        compose.onNodeWithTag("collectionSearch").assertIsDisplayed()
        compose.onNodeWithTag("navSettings").performClick()
        compose.onNodeWithTag("saveConfig").performScrollTo().assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("testLookup").performScrollTo().assertIsDisplayed().assertHasClickAction()
        val save=compose.onNodeWithTag("saveConfig").fetchSemanticsNode().boundsInRoot
        val test=compose.onNodeWithTag("testLookup").fetchSemanticsNode().boundsInRoot
        assertTrue(save.bottom<=test.top)
        assertEquals(save.width,test.width,2f)
        assertEquals(1,posts()) // seed only; screen changes/discovery never generate an entry
    }
}
