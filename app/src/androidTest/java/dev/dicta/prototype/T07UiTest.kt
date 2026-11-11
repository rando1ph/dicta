package dev.dicta.prototype

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.test.espresso.Espresso
import dev.dicta.prototype.query.LookupState
import dev.dicta.prototype.provider.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class T07UiTest:T07MockFixture() {
    private fun SemanticsNodeInteraction.assertInput(value:String)=assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText,AnnotatedString(value)))
    @Test fun collectionScrollPositionSurvivesDetailReturn() {
        val entries=(1..12).map {seed("ephemeral $it")}
        compose.waitUntil(5000) {repo.history.value.count {it.id in entries.map {e->e.id}}>11}
        compose.onNodeWithTag("navHistory").performClick()
        compose.onNodeWithTag("collectionList").performScrollToIndex(6)
        val entry=repo.history.value[6]
        compose.onNodeWithTag("row${entry.historyEventId}").assertIsDisplayed().performClick();awaitSuccess()
        val before=posts()
        compose.onNodeWithTag("detailBack").performClick()
        compose.onNodeWithTag("row${entry.historyEventId}").assertIsDisplayed()
        compose.onNodeWithTag("navHistory").assertIsSelected()
        assertEquals(before,posts())
    }
    @Test fun dailyActionsShareARowAtNormalWidthAndManualRefreshRemainsAvailable() {
        assertEquals(0,posts())
        val before=repo.daily.value.entry?.id
        compose.runOnUiThread {compose.activity.model.daily(true)}
        compose.waitUntil(10000) {!repo.daily.value.busy && repo.daily.value.entry?.id!=before}
        compose.onNodeWithTag("dailyActions").performScrollTo()
        val saved=compose.onNodeWithTag("dailySave").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val shuffle=compose.onNodeWithTag("dailyShuffle").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(saved.top,shuffle.top,2f)
        assertTrue(saved.right<=shuffle.left)
        assertEquals(1,posts())
    }
    @Test fun searchDetailHidesNavigationAndBackRestoresInputWithoutPaidDailyRequest() {
        val before=posts()
        compose.onNodeWithTag("queryInput").performTextReplacement("ephemeral")
        compose.onNodeWithContentDescription("查询").performClick();awaitSuccess()
        val entry=(compose.activity.model.state.value as LookupState.Success).entry
        compose.onNodeWithTag("wordDetail").assertIsDisplayed()
        compose.onNodeWithTag("bottomNavigation").assertDoesNotExist()
        compose.onNodeWithTag("speakUK").assertExists()
        compose.onNodeWithTag("entrySave").performScrollTo().performClick()
        compose.onNodeWithTag("detailBack").performClick()
        compose.onNodeWithTag("queryInput").assertInput("ephemeral")
        compose.onNodeWithTag("dailyShuffle").assertExists()
        assertEquals(before+1,posts())
        // Home recent opens the persisted snapshot with no additional request.
        compose.onNodeWithTag("homeScroll").performTouchInput {swipeUp()}
        compose.onNodeWithTag("recent${entry.id}").performClick();awaitSuccess()
        assertEquals(before+1,posts())
        Espresso.pressBack()
        compose.onNodeWithTag("bottomNavigation").assertIsDisplayed()
    }
    @Test fun historyAndVocabularyKeepTheirOwnFiltersAndScrollAfterDetailAndRecreation() {
        val entry=seed();compose.waitUntil(5000) {repo.vocabulary.value.any {it.id==entry.id} && repo.history.value.any {it.id==entry.id}}
        val before=posts()
        for(tab in listOf("History","Vocabulary")) {
            compose.onNodeWithTag("nav$tab").performClick()
            compose.onNodeWithTag("collectionSearch").performTextReplacement("ephem")
            val rowId=if(tab=="Vocabulary") entry.id else repo.history.value.first {it.id==entry.id}.historyEventId
            compose.onNodeWithTag("row$rowId").performClick();awaitSuccess()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("wordDetail").assertIsDisplayed()
            Espresso.pressBack()
            compose.onNodeWithTag("collectionSearch").assertInput("ephem")
            compose.onNodeWithTag("nav$tab").assertIsSelected()
        }
        assertEquals(before,posts())
    }
    @Test fun detailErrorRetryCancelAndBackAreTerminalAndDoNotLeakToHome() {
        lookupStatus=503
        compose.onNodeWithTag("queryInput").performTextReplacement("ephemeral")
        compose.onNodeWithContentDescription("查询").performClick()
        compose.waitUntil(10000) {compose.activity.model.state.value is LookupState.Error}
        assertEquals(1,posts())
        compose.onNodeWithText("重试").performClick()
        compose.waitUntil(10000) {posts()==2 && compose.activity.model.state.value is LookupState.Error}
        compose.onNodeWithTag("detailBack").performClick()
        lookupStatus=200;lookupDelay=2000
        compose.onNodeWithContentDescription("查询").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("已取消。").assertExists()
        compose.onNodeWithTag("detailBack").performClick()
        compose.waitUntil(10000) {repo.providerClient.dispatcher.runningCallsCount()==0}
        assertEquals(LookupState.Idle,compose.activity.model.state.value)
        compose.onNodeWithTag("queryInput").assertInput("ephemeral")
        compose.onNodeWithTag("wordDetail").assertDoesNotExist()
    }
    @Test fun discoverySearchSelectionMaskPreservationAndClearConfirmation() {
        compose.onNodeWithTag("navSettings").performClick()
        compose.waitUntil(10000) {compose.activity.model.modelDiscovery.value.models.isNotEmpty()}
        assertEquals(0,posts())
        compose.runOnUiThread {assertEquals(0,compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE)}
        compose.onNodeWithTag("keyInput").assertInput("••••••••")
        compose.onNodeWithTag("chooseModel").performScrollTo().performClick()
        compose.onNodeWithTag("modelSearch").performTextReplacement("compact")
        compose.onNodeWithTag("modelOptiondicta-mock-compact").performClick()
        compose.onNodeWithTag("saveConfig").performScrollTo().performClick()
        compose.waitUntil(5000) {!compose.activity.model.configBusy.value && compose.activity.model.configuration.value.model=="dicta-mock-compact"}
        assertEquals("test-only-not-a-real-key",repo.credentials.load().apiKey)
        compose.onNodeWithTag("keyInput").performScrollTo().assertInput("••••••••")
        assertEquals(0,posts())
        compose.onNodeWithTag("providerMore").performScrollTo().performClick()
        compose.onNodeWithTag("clearConfig").performClick()
        compose.onNodeWithText("取消",useUnmergedTree=true).performClick()
        assertTrue(repo.credentials.load().apiKey.isNotBlank())
        compose.onNodeWithTag("providerMore").performClick();compose.onNodeWithTag("clearConfig").performClick()
        compose.onNodeWithTag("confirmClearConfig").performClick()
        compose.waitUntil(5000) {!compose.activity.model.configuration.value.hasKey}
        assertTrue(repo.credentials.load().apiKey.isBlank())
    }
    @Test fun discoveryFailureKeepsManualModelAndPaidTestIsExplicit() {
        modelStatus=401
        compose.onNodeWithTag("navSettings").performClick()
        compose.waitUntil(10000) {compose.activity.model.modelDiscovery.value.failureKind==FailureKind.AUTH}
        assertEquals(0,posts())
        modelStatus=404
        compose.onNodeWithTag("refreshModels").performScrollTo().performClick()
        compose.waitUntil(10000) {compose.activity.model.modelDiscovery.value.failureKind==FailureKind.DISCOVERY_UNSUPPORTED}
        compose.onNodeWithTag("modelInput").performScrollTo().performTextReplacement("manual-model")
        compose.onNodeWithTag("saveConfig").performScrollTo().performClick()
        compose.waitUntil(5000) {compose.activity.model.configuration.value.model=="manual-model"}
        compose.onNodeWithTag("testLookup").performScrollTo().performClick()
        compose.waitUntil(10000) {!compose.activity.model.configBusy.value}
        assertTrue(compose.activity.model.configMessage.value.contains("测试查词成功"))
        assertEquals(1,posts())
        assertEquals("manual-model",Json.parseToJsonElement(requests.last {it.first.endsWith("chat/completions")}.second).jsonObject["model"]?.jsonPrimitive?.content)
    }
    @Test fun keyReplacementClearsTypedStateAndProfileOverrideMatchesActualRequest() {
        val choice=runBlocking {repo.saveProfile(repo.builtin.draft.copy(name="T07 model override",model="profile-model"))}
        compose.waitUntil(5000) {repo.activeProfile.value?.revision?.id==choice.revision.id}
        compose.onNodeWithTag("navSettings").performClick()
        compose.onNodeWithTag("replaceKey").performScrollTo().performClick()
        compose.onNodeWithTag("keyInput").performTextReplacement("another-test-only-key")
        compose.onNodeWithTag("modelInput").performScrollTo().performTextReplacement("")
        compose.onNodeWithTag("saveConfig").performScrollTo().performClick()
        compose.waitUntil(5000) {!compose.activity.model.configBusy.value}
        assertEquals("another-test-only-key",repo.credentials.load().apiKey)
        assertEquals("",repo.credentials.load().model)
        compose.onNodeWithTag("keyInput").performScrollTo().assertInput("••••••••")
        compose.onNodeWithTag("effectiveModel").performScrollTo().assertTextContains("profile-model",substring=true)
        compose.onNodeWithTag("testLookup").performScrollTo().performClick()
        compose.waitUntil(10000) {!compose.activity.model.configBusy.value}
        assertEquals("profile-model",Json.parseToJsonElement(requests.last {it.first.endsWith("chat/completions")}.second).jsonObject["model"]?.jsonPrimitive?.content)
        val beforeDaily=posts()
        runBlocking {repo.ensureDaily(true)}
        assertEquals(beforeDaily+1,posts())
        assertEquals("profile-model",Json.parseToJsonElement(requests.last {it.first.endsWith("chat/completions")}.second).jsonObject["model"]?.jsonPrimitive?.content)
    }
}
