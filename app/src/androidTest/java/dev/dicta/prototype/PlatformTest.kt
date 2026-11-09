package dev.dicta.prototype

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.data.DictaRepository
import dev.dicta.prototype.platform.MainActivity
import dev.dicta.prototype.platform.ProcessTextActivity
import dev.dicta.prototype.query.LookupState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Device-only checks of our own Activities; never changes global phone settings. */
@RunWith(AndroidJUnit4::class)
class PlatformTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun popupIntent(query: String = "ephemeral", readOnly: Boolean = true) =
        Intent(context, ProcessTextActivity::class.java)
            .setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, query)
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, readOnly)

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 65_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(60)
        }
        assertTrue(message, condition())
    }

    private fun awaitSuccess(scenario: ActivityScenario<ProcessTextActivity>): LookupState.Success {
        var success: LookupState.Success? = null
        await("Popup did not reach success") {
            scenario.onActivity { success = it.model.state.value as? LookupState.Success }
            success != null
        }
        return requireNotNull(success)
    }

    @Test fun popupRecreationAndAppScopedRotationKeepSnapshotAndHistory() {
        ActivityScenario.launch<ProcessTextActivity>(popupIntent()).use { scenario ->
            val first = awaitSuccess(scenario).entry.id
            val count = DictaRepository.get(context).history.value.size
            scenario.recreate()
            assertEquals(first, awaitSuccess(scenario).entry.id)
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            await("Landscape was not applied to test Activity") {
                var landscape = false
                scenario.onActivity { landscape = it.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
                landscape
            }
            assertEquals(first, awaitSuccess(scenario).entry.id)
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            await("Portrait was not restored on test Activity") {
                var portrait = false
                scenario.onActivity { portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
                portrait
            }
            assertEquals(first, awaitSuccess(scenario).entry.id)
            assertEquals(count, DictaRepository.get(context).history.value.size)
        }
    }

    @Test fun backgroundThenForegroundKeepsSnapshotWithoutQuery() {
        ActivityScenario.launch<ProcessTextActivity>(popupIntent()).use { scenario ->
            val id = awaitSuccess(scenario).entry.id
            val count = DictaRepository.get(context).history.value.size
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertEquals(id, awaitSuccess(scenario).entry.id)
            assertEquals(count, DictaRepository.get(context).history.value.size)
        }
    }

    @Test fun editableSourceReturnsCancelledWithNoReplacementText() {
        ActivityScenario.launchActivityForResult<ProcessTextActivity>(popupIntent(readOnly = false)).use { scenario ->
            awaitSuccess(scenario)
            scenario.onActivity { activity ->
                assertFalse(activity.sourceReadOnly)
                activity.dismiss()
            }
            await("Popup did not finish") { scenario.state == Lifecycle.State.DESTROYED }
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
            assertNull(scenario.result.resultData?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
        }
    }

    @Test fun backDismissesPopup() {
        ActivityScenario.launchActivityForResult<ProcessTextActivity>(popupIntent()).use { scenario ->
            awaitSuccess(scenario)
            Espresso.pressBackUnconditionally()
            await("Back must finish Popup host") { scenario.state == Lifecycle.State.DESTROYED }
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
    }

    @Test fun invalidActionAndMimeFinishWithoutQuery() {
        val before = DictaRepository.get(context).history.value.size
        val badAction = popupIntent().setAction(Intent.ACTION_VIEW)
        ActivityScenario.launchActivityForResult<ProcessTextActivity>(badAction).use { scenario ->
            await("Invalid action must finish") { scenario.state == Lifecycle.State.DESTROYED }
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
        val badMime = popupIntent().setType("text/html")
        ActivityScenario.launchActivityForResult<ProcessTextActivity>(badMime).use { scenario ->
            await("Invalid MIME must finish") { scenario.state == Lifecycle.State.DESTROYED }
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
        assertEquals(before, DictaRepository.get(context).history.value.size)
    }

    @Test fun emptyAndOverlongInputShowErrorWithoutSaving() {
        val before = DictaRepository.get(context).history.value.size
        for (input in listOf("   ", "x".repeat(101))) {
            ActivityScenario.launch<ProcessTextActivity>(popupIntent(input)).use { scenario ->
                scenario.onActivity {
                    val state = it.model.state.value
                    assertTrue(state is LookupState.Error)
                    assertTrue((state as LookupState.Error).message.contains("100"))
                }
            }
        }
        assertEquals(before, DictaRepository.get(context).history.value.size)
    }

    @Test fun cancellingLoadingDoesNotSaveSnapshot() {
        val before = DictaRepository.get(context).history.value.size
        ActivityScenario.launchActivityForResult<ProcessTextActivity>(popupIntent(":slow")).use { scenario ->
            scenario.onActivity {
                assertTrue(it.model.state.value is LookupState.Loading)
                it.dismiss()
            }
            await("Loading close must finish") { scenario.state == Lifecycle.State.DESTROYED }
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
        assertEquals(before, DictaRepository.get(context).history.value.size)
    }

    @Test fun mainDetailsReusesPopupSnapshotWithoutAnotherQuery() {
        var snapshotId = ""
        ActivityScenario.launch<ProcessTextActivity>(popupIntent()).use { scenario ->
            snapshotId = awaitSuccess(scenario).entry.id
        }
        val before = DictaRepository.get(context).history.value.size
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java).putExtra("snapshotId", snapshotId)).use { scenario ->
            await("Saved details must load from Room") {
                var matches=false
                scenario.onActivity {matches=(it.model.state.value as? LookupState.Success)?.entry?.id==snapshotId}
                matches
            }
        }
        assertEquals(before, DictaRepository.get(context).history.value.size)
    }
}
