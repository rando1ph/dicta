package dev.dicta.prototype

import androidx.compose.ui.test.*
import androidx.lifecycle.Lifecycle
import dev.dicta.prototype.data.*
import dev.dicta.prototype.profile.ProfileEngine
import dev.dicta.prototype.provider.*
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class T07RevisionTest:T07MockFixture() {
    override val prepareDaily=false
    @Test fun missingTodayCacheGeneratesAutomatically() {
        val before=posts()
        runBlocking {repo.ensureDaily(date=LocalDate.of(2040,1,1),zone=ZoneId.of("UTC"))}
        assertEquals(before+1,posts())
        assertNotNull(repo.daily.value.entry)
        assertEquals("2040-01-01",repo.daily.value.localDate)
    }
    @Test fun changingHostWithoutReplacementRejectsSaveAndKeepsOriginalCredential() {
        val old=repo.credentials.load()
        compose.runOnUiThread {compose.activity.model.saveConfig("https://api.openai.com/v1","", "",ProviderProtocol.OPENAI)}
        compose.waitUntil(5000) {!compose.activity.model.configBusy.value}
        assertEquals(old.endpoint,repo.credentials.load().endpoint)
        assertEquals(old.apiKey,repo.credentials.load().apiKey)
        assertTrue(compose.activity.model.configMessage.value.contains("新服务"))
    }
    @Test fun homeAutoGeneratesOnceAcrossReturnRecreationAndResumeAndKeepsShuffle() {
        assertEquals(0,posts())
        compose.onNodeWithTag("navSearch").performClick()
        compose.waitUntil(10000) {posts()==1 && !repo.daily.value.busy && repo.daily.value.entry!=null}
        val first=repo.daily.value.entry!!.id
        compose.activityRule.scenario.recreate()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithTag("queryInput").performTextReplacement("luminous")
        compose.onNodeWithContentDescription("查询").performClick();awaitSuccess()
        compose.onNodeWithTag("detailBack").performClick()
        compose.waitForIdle()
        assertEquals(2,posts())
        assertEquals(first,repo.daily.value.entry!!.id)
        repo.daily.value=DailyState()
        runBlocking {repo.ensureDaily()}
        assertEquals(first,repo.daily.value.entry!!.id)
        assertEquals(2,posts())
        compose.onNodeWithTag("dailyShuffle").performScrollTo().assertTextContains("换一换").performClick()
        compose.waitUntil(10000) {posts()==3 && !repo.daily.value.busy && repo.daily.value.entry?.id!=first}
    }
    @Test fun dailyDateZoneAndConfigurationSlotsCacheAndCoalesceConcurrentCalls()=runBlocking {
        val date=LocalDate.of(2040,2,1);val utc=ZoneId.of("UTC")
        coroutineScope {repeat(6) {launch(Dispatchers.IO) {repo.ensureDaily(date=date,zone=utc)}}}
        val first=repo.daily.value.entry!!.id
        assertEquals(1,posts())
        repo.ensureDaily(date=date,zone=utc)
        assertEquals(first,repo.daily.value.entry!!.id);assertEquals(1,posts())
        repo.ensureDaily(date=date.plusDays(1),zone=utc);assertEquals(2,posts())
        repo.ensureDaily(date=date.plusDays(1),zone=ZoneId.of("Asia/Shanghai"));assertEquals(3,posts())
        val old=repo.credentials.load()
        repo.credentials.save(old.withModel("different-model"))
        repo.ensureDaily(date=date.plusDays(1),zone=utc);assertEquals(4,posts())
        repo.ensureDaily(date=date.plusDays(1),zone=utc);assertEquals(4,posts())
        assertTrue(repo.database.dao().history().first().isEmpty())
    }
    @Test fun dailyFailureWaitsForExplicitRetryAndFailedShuffleKeepsOldCard() {
        lookupStatus=503
        compose.onNodeWithTag("navSearch").performClick()
        compose.waitUntil(10000) {posts()==1 && !repo.daily.value.busy && repo.daily.value.message.isNotBlank()}
        compose.onNodeWithTag("navSettings").performClick()
        compose.onNodeWithTag("navSearch").performClick()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        assertEquals(1,posts())
        lookupStatus=200
        compose.onNodeWithTag("dailyShuffle").performScrollTo().assertTextContains("重试").performClick()
        compose.waitUntil(10000) {posts()==2 && !repo.daily.value.busy && repo.daily.value.entry!=null}
        val first=repo.daily.value.entry!!.id
        lookupStatus=503
        compose.onNodeWithTag("dailyShuffle").performScrollTo().performClick()
        compose.waitUntil(10000) {posts()==3 && !repo.daily.value.busy && repo.daily.value.message.isNotBlank()}
        assertEquals(first,repo.daily.value.entry!!.id)
        lookupStatus=200
        compose.onNodeWithTag("dailyShuffle").performScrollTo().performClick()
        compose.waitUntil(10000) {posts()==4 && !repo.daily.value.busy && repo.daily.value.entry?.id!=first}
    }
    @Test fun presetSwitchClearsDraftKeyAndRequiresANewServiceCredential() {
        val old=repo.credentials.load()
        compose.onNodeWithTag("replaceKey").performScrollTo().performClick()
        compose.onNodeWithTag("keyInput").performTextReplacement("draft-for-old-service")
        for(preset in listOf("DeepSeek","OpenAI")) {
            compose.onNodeWithText(preset,useUnmergedTree=true).performScrollTo().performClick()
            compose.onNodeWithTag("keyInput").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
            compose.onNodeWithTag("keyStatus").assertTextContains("新服务需要 API Key",substring=true)
            compose.onNodeWithTag("refreshModels").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("testLookup").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("saveConfig").performScrollTo().performClick()
            compose.waitUntil(5000) {!compose.activity.model.configBusy.value}
            assertEquals(old.endpoint,repo.credentials.load().endpoint)
            assertEquals(old.apiKey,repo.credentials.load().apiKey)
        }
        assertEquals(0,posts())
    }
    @Test fun newHostGetsOnlyExplicitNewKeyForModelsAndCompletions() {
        val next="https://localhost:${server.port}/v1"
        compose.onNodeWithTag("endpointInput").performScrollTo().performTextReplacement(next)
        compose.onNodeWithTag("keyInput").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
        compose.onNodeWithTag("saveConfig").performScrollTo().performClick()
        compose.waitUntil(5000) {!compose.activity.model.configBusy.value}
        assertTrue(credentialsSent.none {it.first.startsWith("localhost:")})
        compose.onNodeWithTag("keyInput").performScrollTo().performTextReplacement("new-host-test-only-key")
        compose.onNodeWithTag("saveConfig").performScrollTo().performClick()
        compose.waitUntil(10000) {credentialsSent.any {it.first.startsWith("localhost:")}}
        compose.onNodeWithTag("testLookup").performScrollTo().performClick()
        compose.waitUntil(10000) {!compose.activity.model.configBusy.value && posts()==1}
        assertTrue(compose.activity.model.configMessage.value.contains("测试查词成功"))
        assertTrue(credentialsSent.filter {it.first.startsWith("localhost:")}.all {it.second=="Bearer new-host-test-only-key"})
        assertTrue(requests.any {it.first=="/v1/models"})
        assertEquals("new-host-test-only-key",repo.credentials.load().apiKey)
    }
    @Test fun v100HistoryVocabularyAndDailyRenderCleanlyWithoutChangingStoredSnapshots()=runBlocking {
        val entry=repo.lookup("ephemeral",useCache=false)
        val legacy=InstrumentationRegistry.getInstrumentation().context.assets.open("t07/v100-popup.template.json").bufferedReader().use {it.readText()}
        val snapshot=entry.copy(templateJson=legacy,rendered=ProfileEngine(entry.schemaJson,legacy).decodeAndRender(entry.rawJson))
        repo.remember(snapshot);repo.setBookmarks(listOf(snapshot.id),true)
        val dao=repo.database.dao()
        val stored=dao.entry(snapshot.id)!!
        repo.ensureDaily()
        val selected=dao.daily().first().first()
        dao.put(selected.copy(entryId=snapshot.id))
        val before=posts()
        compose.waitUntil(5000) {repo.vocabulary.value.any {it.id==snapshot.id}}
        for(tab in listOf("History","Vocabulary")) {
            compose.onNodeWithTag("nav$tab").performClick()
            val row=if(tab=="History") repo.history.value.first {it.id==snapshot.id}.historyEventId else snapshot.id
            compose.onNodeWithTag("row$row").performClick();awaitSuccess()
            assertCleanEtymology()
            Espresso.pressBack()
        }
        compose.onNodeWithTag("navSearch").performClick()
        compose.waitUntil(5000) {repo.daily.value.entry?.id==snapshot.id}
        assertCleanEtymology()
        assertEquals(before,posts())
        assertEquals(stored,dao.entry(snapshot.id))
        assertEquals(legacy,dao.entry(snapshot.id)!!.templateJson)
    }
    private fun assertCleanEtymology() {
        compose.onAllNodesWithText("词源").assertCountEquals(0)
        compose.onAllNodesWithText("词源  +").assertCountEquals(0)
        compose.onAllNodesWithText("词源  −").assertCountEquals(0)
        compose.onAllNodesWithText("AI 生成",substring=true).assertCountEquals(0)
        compose.onAllNodesWithText("核验",substring=true).assertCountEquals(0)
        compose.onAllNodesWithText("established").assertCountEquals(0)
        compose.onNodeWithText("希腊语 ephēmeros 意为“只持续一天的”，后来用于表示短暂存在的事物。").performScrollTo().assertIsDisplayed()
    }
}
