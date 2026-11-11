package dev.dicta.prototype

import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.query.LookupState
import dev.dicta.prototype.ui.Appearance
import dev.dicta.prototype.ui.ThemeMode
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Explicit opt-in fixture for real CLI/ADB interaction and original PNG capture. */
class T07ReviewSession:T07MockFixture() {
    override val prepareDaily=false
    @Test fun reviewWithLocalTlsProvider() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("review")=="true")
        val stop=File(context.filesDir,"t07-review-stop")
        stop.delete()
        val seed=seed(legacy=true)
        // Keep a local history snapshot, while the first active lookup exercises the network path.
        repo.database.openHelper.writableDatabase.execSQL("UPDATE entries SET cacheKey = '' WHERE id = ?",arrayOf(seed.id))
        compose.runOnUiThread {Appearance.mode.value=ThemeMode.Dark;Appearance.accent.value=0}
        compose.onNodeWithTag("navSearch").performClick()
        compose.waitUntil(10000) {!repo.daily.value.busy && repo.daily.value.entry!=null}
        val ready=File(context.filesDir,"t07-review-ready")
        val action=File(context.filesDir,"t07-review-action")
        val done=File(context.filesDir,"t07-review-done")
        action.delete();done.delete()
        ready.writeText("Local TLS Mock Provider; API36; no real credentials or API usage.")
        try {
            val deadline=System.currentTimeMillis()+30*60*1000
            while(!stop.exists() && System.currentTimeMillis()<deadline) {
                compose.waitForIdle()
                if(action.exists()) {
                    val command=action.readText().trim();action.delete()
                    try {reviewAction(command);compose.waitForIdle();done.writeText(command)}
                    catch(e:Exception) {done.writeText("ERROR: ${e.javaClass.simpleName}: ${e.message.orEmpty().take(200)}")}
                }
                Thread.sleep(200)
            }
        } finally {stop.delete();ready.delete();action.delete();done.delete()}
    }
    private fun back() {
        if(compose.onAllNodesWithTag("modelSearch").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("关闭").performClick()
        if(compose.onAllNodesWithTag("detailBack").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("detailBack").performClick()
    }
    private fun page(name:String) {back();compose.onNodeWithTag("nav$name").performClick()}
    private fun history() {
        page("History");compose.onNodeWithTag("collectionSearch").performTextReplacement("ephem")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
    }
    private fun reviewAction(command:String) {
        when(command) {
            "home" -> {page("Search");compose.onNodeWithTag("queryInput").performScrollTo()}
            "daily" -> {page("Search");compose.onNodeWithTag("dailyShuffle").performScrollTo()}
            "daily_etymology" -> {page("Search");etymology()}
            "detail_etymology" -> {reviewAction("query");etymology()}
            "history_etymology" -> {reviewAction("history_detail");etymology()}
            "vocabulary_etymology" -> {reviewAction("vocabulary_detail");etymology()}
            "new_provider" -> {
                reviewAction("settings")
                compose.onNodeWithText("OpenAI",useUnmergedTree=true).performClick()
                compose.onNodeWithTag("keyStatus").performScrollTo()
            }
            "popup_etymology" -> {
                back()
                val intent=android.content.Intent(context,dev.dicta.prototype.platform.ProcessTextActivity::class.java)
                    .setAction(android.content.Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_PROCESS_TEXT,"ephemeral")
                    .putExtra(android.content.Intent.EXTRA_PROCESS_TEXT_READONLY,true)
                compose.runOnUiThread {compose.activity.startActivity(intent)}
                compose.waitUntil(10000) {compose.onAllNodesWithTag("popupDetails").fetchSemanticsNodes().isNotEmpty()}
                etymology()
            }
            "close_popup" -> {compose.onNodeWithTag("closePopup").performClick()}
            "query" -> {
                page("Search");compose.onNodeWithTag("queryInput").performScrollTo().performTextReplacement("ephemeral")
                compose.onNodeWithContentDescription("查询").performClick();awaitSuccess()
                val entry=(compose.activity.model.state.value as LookupState.Success).entry
                if(repo.vocabulary.value.none {it.id==entry.id}) compose.onNodeWithTag("entrySave").performScrollTo().performClick()
                compose.onNodeWithText("ephemeral").performScrollTo()
            }
            "history" -> history()
            "history_detail" -> {
                history()
                val entry=repo.history.value.first {it.query=="ephemeral"}
                compose.onNodeWithTag("collectionList").performScrollToIndex(0)
                compose.onNodeWithTag("row${entry.historyEventId}").performClick();awaitSuccess()
            }
            "vocabulary" -> {page("Vocabulary");compose.onNodeWithTag("collectionSearch").performTextReplacement("ephem");androidx.test.espresso.Espresso.closeSoftKeyboard()}
            "vocabulary_detail" -> {
                page("Vocabulary");compose.onNodeWithTag("collectionSearch").performTextReplacement("ephem")
                val entry=repo.vocabulary.value.first {it.query=="ephemeral"}
                compose.onNodeWithTag("collectionList").performScrollToIndex(0)
                compose.onNodeWithTag("row${entry.id}").performClick();awaitSuccess()
            }
            "settings" -> {page("Settings");compose.onNodeWithText("AI CONFIGURATION · 模型配置").performScrollTo()}
            "models" -> {
                page("Settings");compose.waitUntil(10000) {compose.activity.model.modelDiscovery.value.models.isNotEmpty()}
                compose.onNodeWithTag("chooseModel").performScrollTo().performClick()
            }
            "actions" -> {page("Settings");compose.onNodeWithTag("testLookup").performScrollTo()}
            "light" -> {
                page("Settings");compose.onNodeWithText("Light").performScrollTo().performClick()
                compose.onNodeWithContentDescription("Accent 3").performScrollTo().performClick()
                page("Search");compose.onNodeWithTag("queryInput").performScrollTo()
            }
            "dark" -> {
                page("Settings");compose.onNodeWithText("Dark").performScrollTo().performClick()
                compose.onNodeWithContentDescription("Accent 1").performScrollTo().performClick()
                page("Search");compose.onNodeWithTag("queryInput").performScrollTo()
            }
            else -> error("Unknown review action")
        }
    }
    private fun etymology() {
        val collapsed=compose.onAllNodesWithText("词源  +").fetchSemanticsNodes()
        if(collapsed.isNotEmpty()) compose.onNodeWithText("词源  +").performScrollTo().performClick()
        compose.onNodeWithText("词源  −").performScrollTo()
        compose.onNodeWithText("希腊语 ephēmeros 意为“只持续一天的”，后来用于表示短暂存在的事物。").performScrollTo()
        compose.onAllNodesWithText("AI 生成",substring=true).assertCountEquals(0)
        compose.onAllNodesWithText("核验",substring=true).assertCountEquals(0)
        compose.onAllNodesWithText("established").assertCountEquals(0)
    }
}
