package dev.dicta.prototype.query

import dev.dicta.prototype.data.EntrySnapshot
import dev.dicta.prototype.profile.RenderedEntry
import dev.dicta.prototype.provider.FakeProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LookupSessionTest {
    private fun entry(query: String) = EntrySnapshot(query, query, 0L,
        RenderedEntry(mapOf("title" to query), emptyList(), query), "{}", "{}", "{}")

    @Test fun latestWinsEvenWhenOldSourceIgnoresCancellation() = runTest {
        val saved = mutableListOf<EntrySnapshot>()
        val session = LookupSession(this, { query ->
            withContext(NonCancellable) { delay(if (query == "old") 1_000 else 100) }
            entry(query)
        }, saved::add)
        session.lookup("old")
        runCurrent()
        session.lookup("new")
        advanceUntilIdle()
        assertEquals("new", (session.state.value as LookupState.Success).entry.query)
        assertEquals(listOf("new"), saved.map { it.query })
    }

    @Test fun cancellationDoesNotSaveOrPublishLateResult() = runTest {
        val saved = mutableListOf<EntrySnapshot>()
        val session = LookupSession(this, { query ->
            withContext(NonCancellable) { delay(1_000) }; entry(query)
        }, saved::add)
        session.lookup("luminous")
        runCurrent()
        session.cancel()
        advanceUntilIdle()
        assertEquals(LookupState.Cancelled, session.state.value)
        assertTrue(saved.isEmpty())
    }

    @Test fun malformedInputIsRejectedBeforeProvider() = runTest {
        var calls = 0
        val session = LookupSession(this, { calls++; entry(it) }, {})
        session.lookup("x".repeat(101))
        advanceUntilIdle()
        assertEquals(0, calls)
        assertTrue(session.state.value is LookupState.Error)
    }

    @Test fun timeoutBecomesVisibleErrorAndExplicitRetryStartsOneRequest() = runTest {
        var calls = 0
        val provider = FakeProvider { "{}" }
        val session = LookupSession(this, { calls++; provider.generate(it); entry(it) }, {})
        session.lookup(":timeout")
        advanceUntilIdle()
        assertTrue((session.state.value as LookupState.Error).message.contains("超时"))
        assertEquals(1, calls)
        session.retry()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test fun openSnapshotDoesNotQueryOrAddHistory() = runTest {
        var calls = 0
        var saves = 0
        val session = LookupSession(this, { calls++; entry(it) }, { saves++ })
        val existing = entry("ephemeral")
        session.open(existing)
        advanceUntilIdle()
        assertEquals(existing, (session.state.value as LookupState.Success).entry)
        assertEquals(0, calls)
        assertEquals(0, saves)
    }

    @Test fun restoreDoesNotAutomaticallyReissueRequest() = runTest {
        var calls = 0
        val session = LookupSession(this, { calls++; entry(it) }, {})
        session.restoredWithoutRequest("luminous")
        advanceUntilIdle()
        assertEquals(0, calls)
        assertTrue(session.state.value is LookupState.Error)
        session.retry()
        advanceUntilIdle()
        assertEquals(1, calls)
    }
}
