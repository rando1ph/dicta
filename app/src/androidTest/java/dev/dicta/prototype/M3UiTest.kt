package dev.dicta.prototype

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import dev.dicta.prototype.platform.MainActivity
import dev.dicta.prototype.data.*
import dev.dicta.prototype.profile.*
import dev.dicta.prototype.query.LookupState
import dev.dicta.prototype.ui.Appearance
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import kotlinx.coroutines.flow.first
import java.util.UUID

class M3UiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val repo get()=DictaRepository.get(context)
    @Test fun liveHomeHistoryBookmarkSearchAndIndependentBatchRemoval() {
        val beforeIds=runBlocking {repo.database.dao().history().first().map {it.eventId}.toSet()}
        compose.onNodeWithTag("queryInput").performTextReplacement("mellifluous")
        compose.onNodeWithContentDescription("查询").performClick()
        compose.waitUntil(65000) {compose.activity.model.state.value is LookupState.Success}
        val entry=(compose.activity.model.state.value as LookupState.Success).entry
        compose.waitUntil(5000) {repo.history.value.any {it.id==entry.id}}
        val alreadySaved=runBlocking {repo.database.dao().vocabulary().first().any {it.id==entry.id}}
        if(!alreadySaved) compose.onNodeWithTag("entrySave").performScrollTo().performClick()
        compose.waitUntil(5000) {repo.vocabulary.value.any {it.id==entry.id}}
        compose.onNodeWithTag("navHistory").performClick()
        compose.onNodeWithTag("collectionSearch").performTextReplacement("mellifluous")
        val event=runBlocking {repo.database.dao().history().first().first {it.entry.id==entry.id && it.eventId !in beforeIds}}
        compose.waitUntil(5000) {repo.history.value.any {it.historyEventId==event.eventId}}
        val history=repo.history.value.first {it.historyEventId==event.eventId}
        compose.onNodeWithTag("manageCollection").performClick()
        compose.onNodeWithTag("row${history.historyEventId}").performClick()
        compose.onNodeWithTag("bulkRemove").performClick()
        compose.onNodeWithTag("confirmRemove").performClick()
        compose.waitUntil(5000) {repo.history.value.none {it.historyEventId==history.historyEventId}}
        assertTrue(repo.vocabulary.value.any {it.id==entry.id})
        compose.onNodeWithTag("navVocabulary").performClick()
        compose.onNodeWithTag("collectionSearch").performTextReplacement("no-such-word-for-m3-test")
        compose.onNodeWithText("没有匹配词条").assertExists()
        compose.onNodeWithTag("collectionSearch").performTextReplacement("mellifluous")
        compose.onNodeWithTag("row${entry.id}").performClick()
        compose.waitUntil(5000) {(compose.activity.model.state.value as? LookupState.Success)?.entry?.id==entry.id}
        // Reopen by ID uses the already saved snapshot; no new history event.
        assertTrue(repo.history.value.none {it.historyEventId==history.historyEventId})
    }
    @Test fun profileReadonlyCopyEditPreviewInvalidSaveAndRestore() {
        compose.onNodeWithTag("navSettings").performClick()
        compose.onNodeWithTag("openProfiles").performScrollTo().performClick()
        compose.onNodeWithTag("restoreDefault").performScrollTo().performClick()
        compose.waitUntil(5000) {!compose.activity.model.profileBusy.value}
        compose.onNodeWithTag("copyProfile").performScrollTo().performClick()
        compose.onNodeWithTag("profileName").performScrollTo().performTextReplacement("M3 Literary")
        compose.onNodeWithTag("editPrompt").performScrollTo().performClick()
        val prompt=runBlocking {repo.currentProfile().draft.prompt}
        compose.onNodeWithTag("profileEditor").performScrollTo().performTextReplacement(prompt+"\nPrefer concise definitions.")
        compose.onNodeWithTag("saveProfile").performScrollTo()
        compose.waitUntil(5000) {compose.onAllNodesWithText("校验通过 · 本地即时预览").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("saveProfile").performClick()
        compose.waitUntil(5000) {compose.activity.model.activeProfile.value?.revision?.name=="M3 Literary"}
        val saved=runBlocking {repo.currentProfile()}
        compose.onNodeWithTag("editSchema").performScrollTo().performClick()
        compose.onNodeWithTag("profileEditor").performScrollTo().performTextReplacement("{invalid")
        compose.onNodeWithTag("saveProfile").performScrollTo()
        compose.waitUntil(5000) {compose.onAllNodesWithText("校验通过 · 本地即时预览").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("saveProfile").assertIsNotEnabled()
        assertEquals(saved.revision.id,runBlocking {repo.currentProfile()}.revision.id)
        compose.onNodeWithTag("restoreDefault").performScrollTo().performClick()
        compose.waitUntil(5000) {compose.activity.model.activeProfile.value?.builtin==true}
        assertTrue(repo.profiles.value.any {it.revision.name=="M3 Literary"})
    }

    @Test fun vocabularyFilterClearsSelectionAndBatchRemoveKeepsHistory() {
        val entryId=UUID.randomUUID().toString();val eventId=UUID.randomUUID().toString()
        val word="m3-fixture-${UUID.randomUUID()}"
        val draft=repo.builtin.draft
        val raw=draft.example.replace("ephemeral",word)
        val entry=StoredEntry(entryId,word,System.currentTimeMillis(),raw,draft.schema,draft.template,"builtin","",word,"Fixture meaning","","adjective")
        try {
            runBlocking {
                repo.database.dao().insert(entry)
                repo.database.dao().insert(HistoryEvent(eventId,entryId,"home",System.currentTimeMillis()))
                repo.setBookmarks(listOf(entryId),true)
            }
            compose.waitUntil(5000) {repo.vocabulary.value.any {it.id==entryId} && repo.history.value.any {it.historyEventId==eventId}}
            compose.onNodeWithTag("navVocabulary").performClick()
            compose.onNodeWithTag("collectionSearch").performTextReplacement(word)
            compose.onNodeWithTag("manageCollection").performClick()
            compose.onNodeWithTag("row$entryId").performClick()
            compose.onNodeWithTag("collectionSearch").performTextReplacement("no-result-for-m3")
            compose.onNodeWithTag("bulkRemove").assertIsNotEnabled()
            compose.onNodeWithText("已选 0").assertExists()
            compose.onNodeWithTag("collectionSearch").performTextReplacement(word)
            compose.onNodeWithText("全选当前结果").performClick()
            compose.onNodeWithTag("bulkRemove").performClick()
            compose.onNodeWithTag("confirmRemove").performClick()
            compose.waitUntil(5000) {repo.vocabulary.value.none {it.id==entryId}}
            assertTrue(repo.history.value.any {it.historyEventId==eventId})
        } finally {runBlocking {
            repo.setBookmarks(listOf(entryId),false);repo.deleteHistory(listOf(eventId))
            repo.database.openHelper.writableDatabase.execSQL("DELETE FROM entries WHERE id = ?",arrayOf(entryId))
        }}
    }
    @Test fun schemaTemplateAndExampleEditsProduceImmediateNativePreview() {
        val original=runBlocking {repo.currentProfile()}
        try {
            compose.onNodeWithTag("navSettings").performClick()
            compose.onNodeWithTag("openProfiles").performScrollTo().performClick()
            compose.onNodeWithTag("restoreDefault").performScrollTo().performClick()
            compose.waitUntil(5000) {compose.activity.model.activeProfile.value?.builtin==true}
            val base=runBlocking {repo.currentProfile().draft}
            compose.onNodeWithTag("copyProfile").performScrollTo().performClick()
            compose.onNodeWithTag("profileName").performScrollTo().performTextReplacement("M3 Compact")
            val template=Json.parseToJsonElement(base.template).jsonObject.toMutableMap()
            template["nodes"]=buildJsonArray {add(buildJsonObject {put("type","text");put("bind","/word");put("style","headline");put("label","Preview")})}
            compose.onNodeWithTag("editTemplate").performScrollTo().performClick()
            compose.onNodeWithTag("profileEditor").performScrollTo().performTextReplacement(JsonObject(template).toString())
            compose.onNodeWithTag("editSchema").performScrollTo().performClick()
            compose.onNodeWithTag("profileEditor").performScrollTo().performTextReplacement(base.schema.replace("Dicta default lexical entry v1","M3 custom schema"))
            compose.onNodeWithTag("editExample").performScrollTo().performClick()
            compose.onNodeWithTag("profileEditor").performScrollTo().performTextReplacement(base.example.replace("ephemeral","vernal"))
            compose.onNodeWithTag("saveProfile").performScrollTo()
            compose.waitUntil(5000) {compose.onAllNodesWithText("校验通过 · 本地即时预览").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Preview  vernal").assertExists()
            compose.onNodeWithTag("saveProfile").performClick()
            compose.waitUntil(5000) {compose.activity.model.activeProfile.value?.revision?.name=="M3 Compact"}
            assertEquals("vernal",runBlocking {repo.currentProfile().draft.preview()}.summary["title"])
        } finally {runBlocking {repo.activate(original)}}
    }

    @Test fun dailyCacheRefreshAndUserProfilesPersistInDatabase()=runBlocking {
        compose.waitUntil(65000) {!repo.daily.value.busy && repo.daily.value.entry!=null}
        val first=repo.daily.value.entry!!
        repo.ensureDaily(false)
        assertEquals(first.id,repo.daily.value.entry?.id)
        repo.ensureDaily(true)
        assertFalse(repo.daily.value.busy)
        assertTrue(repo.daily.value.message,repo.daily.value.message.isBlank())
        val next=repo.daily.value.entry!!
        assertNotEquals(first.rendered.summary["title"]?.lowercase(),next.rendered.summary["title"]?.lowercase())
        repo.ensureDaily(false)
        assertEquals(next.id,repo.daily.value.entry?.id)
        assertNotNull(repo.database.dao().entry(next.id))
        assertTrue(repo.credentials.load().apiKey.isNotBlank())
    }
}
