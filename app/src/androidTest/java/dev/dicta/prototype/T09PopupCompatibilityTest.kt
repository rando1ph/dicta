package dev.dicta.prototype

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.text.SpannableString
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import dev.dicta.prototype.platform.MainActivity
import dev.dicta.prototype.platform.ProcessTextActivity
import dev.dicta.prototype.query.LookupState
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import java.io.File

/** Calling-contract checks. Real reader/browser selection is a separate, manual device journey. */
class T09PopupCompatibilityTest : T07MockFixture() {
    override val prepareDaily = false
    override val keepScreenOn = true

    init {
        check(context.packageName == "dev.randolf.dicta.debug") { "T09 requires the isolated Debug package" }
        check(!File(context.noBackupFilesDir, "provider.enc").exists()) {
            "T09 requires an unconfigured Debug package; existing credentials are preserved and must not be used"
        }
    }

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun popup(word: CharSequence = "ephemeral") =
        Intent(context, ProcessTextActivity::class.java)
            .setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, word)
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)

    private fun <T> onMain(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }

    private fun resumedPopup(): ProcessTextActivity? = onMain {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<ProcessTextActivity>().singleOrNull()
    }

    private fun awaitPopup(except: ProcessTextActivity? = null): ProcessTextActivity {
        var activity: ProcessTextActivity? = null
        compose.waitUntil(10_000) {
            activity = resumedPopup()
            activity != null && activity !== except
        }
        return requireNotNull(activity)
    }

    private fun success(activity: ProcessTextActivity): LookupState.Success {
        compose.waitUntil(10_000) { activity.model.state.value is LookupState.Success }
        return activity.model.state.value as LookupState.Success
    }

    private fun start(intent: Intent): ProcessTextActivity {
        onMain { compose.activity.startActivity(intent) }
        return awaitPopup()
    }

    @After fun closeTestActivities() {
        // Close only Activities belonging to this instrumented Debug process.
        onMain {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            val activities = Stage.values().filter { it != Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }.distinct()
            activities.filterIsInstance<ProcessTextActivity>().forEach { it.dismiss() }
            activities.filterIsInstance<MainActivity>().filter { it !== compose.activity }.forEach { it.finish() }
        }
        instrumentation.waitForIdleSync()
    }

    @Test fun implicitTextProcessingResolvesToClearlyNamedDebugTarget() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain").setPackage(context.packageName)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        assertNotNull(resolved)
        assertEquals(ProcessTextActivity::class.java.name, resolved!!.activityInfo.name)
        assertEquals(context.packageName, resolved.activityInfo.packageName)
        assertEquals("Dicta Debug", resolved.loadLabel(context.packageManager).toString())
        val activity = start(intent.putExtra(Intent.EXTRA_PROCESS_TEXT, SpannableString("ephemeral")))
        assertEquals("ephemeral", success(activity).entry.query)
    }

    @Test fun newTaskCallerCannotReuseExistingMainTask() {
        // Readest 0.12.20 adds NEW_TASK even when choosing an explicit Activity.
        // The pre-fix Manifest shares the app affinity, so this assertion fails there.
        val mainTask = compose.activity.taskId
        val activity = start(popup().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertNotEquals("NEW_TASK Popup must not be put over Dicta's existing main screen", mainTask, activity.taskId)
        assertEquals("ephemeral", success(activity).entry.query)
        onMain { activity.dismiss() }
        compose.waitUntil(5_000) { activity.isDestroyed }
        assertFalse(compose.activity.isFinishing)
    }

    @Test fun ordinaryCallerKeepsTaskAndReceivesCancellationWithoutReplacement() {
        val result = AtomicReference<ActivityResult>()
        val launcher = onMain {
            compose.activity.activityResultRegistry.register("t09-process-text", ActivityResultContracts.StartActivityForResult()) {
                result.set(it)
            }
        }
        try {
            onMain { launcher.launch(popup().putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)) }
            val activity = awaitPopup()
            assertEquals(compose.activity.taskId, activity.taskId)
            assertFalse(activity.sourceReadOnly)
            success(activity)
            onMain { activity.dismiss() }
            compose.waitUntil(5_000) { result.get() != null }
            assertEquals(Activity.RESULT_CANCELED, result.get().resultCode)
            assertNull(result.get().data?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
        } finally {
            onMain { launcher.unregister() }
        }
    }

    @Test fun repeatedSingleTopIntentUpdatesSamePopupToLatestSelection() {
        val activity = start(popup("ephemeral"))
        success(activity)
        onMain {
            activity.startActivity(popup("luminous").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false))
        }
        compose.waitUntil(10_000) {
            (activity.model.state.value as? LookupState.Success)?.entry?.query == "luminous"
        }
        assertSame(activity, resumedPopup())
        assertFalse(activity.sourceReadOnly)
        assertEquals("luminous", activity.intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString())
    }

    private fun returnToSourceTask(activity: Activity) {
        assertTrue(onMain { activity.moveTaskToBack(true) })
        compose.waitUntil(5_000) {
            onMain { ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(compose.activity) == Stage.RESUMED }
        }
    }

    @Test fun repeatedNewTaskAfterBackgroundShowsLatestSelectionWithoutSingleTopFlag() {
        val first = start(popup("ephemeral").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val firstTask = first.taskId
        success(first)
        returnToSourceTask(first)
        // A real Readest lookup has NEW_TASK only; it does not supply SINGLE_TOP.
        val next = start(popup("luminous").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(10_000) {
            (next.model.state.value as? LookupState.Success)?.entry?.query == "luminous"
        }
        fun livePopups(): List<Pair<ProcessTextActivity, Stage>> = onMain {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.values().filter { it != Stage.DESTROYED }.flatMap { stage ->
                monitor.getActivitiesInStage(stage).filterIsInstance<ProcessTextActivity>().map { it to stage }
            }
        }
        fun diagnostics(): String {
            val live = livePopups()
            return onMain {
                "firstTaskAtStart=$firstTask first.taskId=${first.taskId} next.taskId=${next.taskId} " +
                    "first.isDestroyed=${first.isDestroyed} first.isChangingConfigurations=${first.isChangingConfigurations} " +
                    "livePopupCount=${live.size} lifecycle=" + live.joinToString { (activity, stage) ->
                        "${System.identityHashCode(activity)}:task=${activity.taskId}:$stage:finishing=${activity.isFinishing}"
                    }
            }
        }
        val beforeDismiss = diagnostics()
        instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "T09 repeated NEW_TASK: $beforeDismiss\n") })
        val nextTask = next.taskId
        assertEquals("No stacked Popup in the current task; $beforeDismiss", 1,
            livePopups().count { (activity, _) -> activity.taskId == nextTask && !activity.isFinishing })
        assertEquals("luminous", next.intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString())
        onMain { next.dismiss() }
        val deadline = System.currentTimeMillis() + 5_000
        fun sourceResumed() = onMain {
            ActivityLifecycleMonitorRegistry.getInstance().getLifecycleStageOf(compose.activity) == Stage.RESUMED
        }
        while (!sourceResumed() && System.currentTimeMillis() < deadline) Thread.sleep(50)
        assertTrue("Closing the new lookup must return to the source task; before=$beforeDismiss after=${diagnostics()}", sourceResumed())
        instrumentation.waitForIdleSync()
        assertNull("An older Popup must not resurface; before=$beforeDismiss after=${diagnostics()}", resumedPopup())
        assertEquals("The closed Popup task must have no older stacked receiver; before=$beforeDismiss after=${diagnostics()}", 0,
            livePopups().count { (activity, _) -> activity.taskId == nextTask && !activity.isFinishing })
    }

    @Test fun newTaskLookupAfterBackgroundedFullEntryShowsNewPopup() {
        val first = start(popup("ephemeral").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        success(first)
        compose.onNodeWithTag("popupDetails").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("wordDetail").fetchSemanticsNodes().isNotEmpty() }
        assertSame(first, resumedPopup())
        returnToSourceTask(first)
        val next = start(popup("luminous").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(10_000) {
            (next.model.state.value as? LookupState.Success)?.entry?.query == "luminous" &&
                compose.onAllNodesWithTag("popupDetails").fetchSemanticsNodes().isNotEmpty()
        }
        assertSame(first, next)
        compose.onNodeWithTag("wordDetail").assertDoesNotExist()
    }

    @Test fun recreationAndCoveredActivityKeepSnapshotWithoutAnotherRequest() {
        val first = start(popup())
        val entry = success(first).entry
        val before = posts()
        onMain { first.recreate() }
        val recreated = awaitPopup(except = first)
        assertEquals(entry.id, success(recreated).entry.id)
        val cover = AtomicReference<MainActivity>()
        onMain {
            recreated.startActivity(Intent(context, MainActivity::class.java)
                .putExtra("snapshotId", entry.id).putExtra("fromPopup", true))
        }
        compose.waitUntil(10_000) {
            cover.set(onMain {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull { it !== compose.activity }
            })
            cover.get()?.model?.state?.value is LookupState.Success
        }
        onMain { cover.get().finish() }
        compose.waitUntil(5_000) { resumedPopup() === recreated }
        assertEquals(entry.id, success(recreated).entry.id)
        assertEquals(before, posts())
    }

    @Test fun fullEntryStaysInReceiverRecreatesWithoutRequestAndBackFinishesReceiver() {
        val activity = start(popup().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val entry = success(activity).entry
        val before = posts()
        compose.onNodeWithTag("popupDetails").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("wordDetail").fetchSemanticsNodes().isNotEmpty() }
        assertSame(activity, resumedPopup())
        assertEquals(entry.id, (activity.model.state.value as LookupState.Success).entry.id)
        compose.onNodeWithTag("bottomNavigation").assertDoesNotExist()
        compose.onNodeWithTag("dictaPopup").assertDoesNotExist()
        assertEquals(before, posts())
        onMain { activity.recreate() }
        val recreated = awaitPopup(except = activity)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("wordDetail").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(entry.id, success(recreated).entry.id)
        compose.onNodeWithTag("bottomNavigation").assertDoesNotExist()
        compose.onNodeWithTag("dictaPopup").assertDoesNotExist()
        assertEquals(before, posts())
        Espresso.pressBackUnconditionally()
        compose.waitUntil(5_000) { recreated.isDestroyed }
        assertFalse(compose.activity.isFinishing)
    }

    @Test fun fullEntrySurvivesAppScopedLandscapeAndPortraitWithoutAnotherRequest() {
        val first = start(popup().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val entry = success(first).entry
        compose.onNodeWithTag("popupDetails").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("wordDetail").fetchSemanticsNodes().isNotEmpty() }
        val before = posts()
        fun awaitOrientation(orientation: Int): ProcessTextActivity {
            var current: ProcessTextActivity? = null
            compose.waitUntil(10_000) {
                current = resumedPopup()
                current?.resources?.configuration?.orientation == orientation &&
                    current?.model?.state?.value is LookupState.Success &&
                    compose.onAllNodesWithTag("wordDetail").fetchSemanticsNodes().isNotEmpty()
            }
            return requireNotNull(current)
        }
        try {
            onMain { first.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            val landscape = awaitOrientation(Configuration.ORIENTATION_LANDSCAPE)
            assertEquals(entry.id, success(landscape).entry.id)
            compose.onNodeWithTag("dictaPopup").assertDoesNotExist()
            assertEquals(before, posts())
        } finally {
            // Only this test Activity's requested orientation changes; no global rotation setting.
            val current = resumedPopup()
            if (current != null) {
                onMain { current.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                val portrait = awaitOrientation(Configuration.ORIENTATION_PORTRAIT)
                assertEquals(entry.id, success(portrait).entry.id)
                assertEquals(before, posts())
            }
        }
    }

    @Test fun malformedExtrasAreSafeAndDoNotSendAnEmptyLookup() {
        val before = posts()
        val activity = start(popup().putExtra(Intent.EXTRA_PROCESS_TEXT, 42)
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, "wrong-type"))
        compose.waitUntil(5_000) { activity.model.state.value is LookupState.Error }
        assertTrue(activity.sourceReadOnly)
        assertEquals(before, posts())
    }

    @Test fun unsupportedActionOrMimeFinishesWithoutRequest() {
        val before = posts()
        for (intent in listOf(popup().setAction(Intent.ACTION_VIEW), popup().setType("text/html"))) {
            val monitor = instrumentation.addMonitor(ProcessTextActivity::class.java.name, null, false)
            try {
                onMain { compose.activity.startActivity(intent) }
                val activity = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
                assertNotNull("Explicit invalid input should reach and close our receiver", activity)
                compose.waitUntil(5_000) { activity!!.isDestroyed }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
        }
        assertEquals(before, posts())
    }
}
