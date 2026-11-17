package dev.dicta.prototype

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import dev.dicta.prototype.platform.ProcessTextActivity
import androidx.lifecycle.Lifecycle
import dev.dicta.prototype.query.LookupState
import org.junit.Assert.*
import org.junit.Test

class T07PopupTest:T07MockFixture() {
    private fun popup()=Intent(context,ProcessTextActivity::class.java).setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        .putExtra(Intent.EXTRA_PROCESS_TEXT,"ephemeral").putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,true)
    @Test fun popupOpenKeepsReceiverAndSnapshotAndBackFinishesExternalSession() {
        seed(legacy=true)
        val before=posts()
        ActivityScenario.launch<ProcessTextActivity>(popup()).use {scenario->
            compose.waitUntil(10000) {compose.onAllNodesWithTag("popupDetails").fetchSemanticsNodes().isNotEmpty()}
            compose.onAllNodesWithText("词源").assertCountEquals(0)
            compose.onAllNodesWithText("词源  +").assertCountEquals(0)
            compose.onAllNodesWithText("词源  −").assertCountEquals(0)
            compose.onNodeWithText("希腊语 ephēmeros 意为“只持续一天的”，后来用于表示短暂存在的事物。").performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText("AI 生成",substring=true).assertCountEquals(0)
            compose.onAllNodesWithText("核验",substring=true).assertCountEquals(0)
            compose.onAllNodesWithText("established").assertCountEquals(0)
            compose.onNodeWithTag("popupDetails").assertIsDisplayed().performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("wordDetail").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithTag("bottomNavigation").assertDoesNotExist()
            assertEquals(before,posts())
            var snapshotId=""
            var receiver:ProcessTextActivity?=null
            scenario.onActivity {
                receiver=it
                snapshotId=(it.model.state.value as LookupState.Success).entry.id
            }
            scenario.recreate()
            compose.waitUntil(10000) {compose.onAllNodesWithTag("wordDetail").fetchSemanticsNodes().isNotEmpty()}
            scenario.onActivity {
                assertNotSame(receiver,it)
                assertEquals(snapshotId,(it.model.state.value as LookupState.Success).entry.id)
            }
            compose.onNodeWithTag("bottomNavigation").assertDoesNotExist()
            compose.onNodeWithTag("dictaPopup").assertDoesNotExist()
            assertEquals(before,posts())
            Espresso.pressBackUnconditionally()
            val deadline=System.currentTimeMillis()+5000
            while(scenario.state!=Lifecycle.State.DESTROYED && System.currentTimeMillis()<deadline) Thread.sleep(50)
            assertEquals(Lifecycle.State.DESTROYED,scenario.state)
        }
    }
    @Test fun popupDismissAndRecreationKeepSnapshotAndNeverReplaceSelectedText() {
        val entry=seed()
        val before=posts()
        ActivityScenario.launchActivityForResult<ProcessTextActivity>(popup().putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY,false)).use {scenario->
            compose.waitUntil(10000) {
                var success=false;scenario.onActivity {success=(it.model.state.value as? LookupState.Success)?.entry?.id==entry.id};success
            }
            scenario.recreate()
            scenario.onActivity {assertEquals(entry.id,(it.model.state.value as LookupState.Success).entry.id);assertFalse(it.sourceReadOnly)}
            compose.onNodeWithTag("closePopup").assertIsDisplayed().performClick()
            assertEquals(android.app.Activity.RESULT_CANCELED,scenario.result.resultCode)
            assertNull(scenario.result.resultData?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
        }
        assertEquals(before,posts())
    }
}
