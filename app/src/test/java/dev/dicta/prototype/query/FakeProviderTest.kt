package dev.dicta.prototype.query

import dev.dicta.prototype.provider.FakeProvider
import dev.dicta.prototype.provider.ProviderFailure
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FakeProviderTest {
    @Test fun normalizationAndUnicodeLimit() {
        assertEquals("ephemeral", normalizeQuery("  ephemeral \n"))
        assertEquals("😀".repeat(100), normalizeQuery("😀".repeat(100)))
        assertThrows(InvalidQuery::class.java) { normalizeQuery("😀".repeat(101)) }
        assertThrows(InvalidQuery::class.java) { normalizeQuery(" \n ") }
    }

    @Test fun knownSampleMatchesOnlyItsOwnFixture() = runTest {
        val reads = mutableListOf<String>()
        val provider = FakeProvider { reads += it; "sample:$it" }
        assertEquals("sample:ephemeral.json", provider.generate("EPHEMERAL"))
        assertEquals(listOf("ephemeral.json"), reads)
    }

    @Test fun unsupportedQueryNeverReadsAReplacementFixture() = runTest {
        val provider = FakeProvider { error("Must not read fixture") }
        try { provider.generate("serendipity"); fail("Unknown query must be explicit") }
        catch (failure: ProviderFailure) { assertTrue(failure.userMessage.contains("暂无此词")) }
    }

    @Test fun cancelledSlowQueryCannotReadOrReturnFixture() = runTest {
        var reads = 0
        val result = async { FakeProvider { reads++; "sample" }.generate(":slow") }
        advanceTimeBy(1_000)
        result.cancel()
        advanceTimeBy(10_000)
        assertTrue(result.isCancelled)
        assertEquals(0, reads)
    }

    @Test fun timeoutIsBoundedAndDoesNotReadFixture() = runTest {
        val provider = FakeProvider { error("Must not read fixture") }
        try { provider.generate(":timeout"); fail("Timeout expected") }
        catch (_: TimeoutCancellationException) { assertEquals(1_200L, testScheduler.currentTime) }
    }

    @Test fun deliberateFailureHasSafeMessage() = runTest {
        try { FakeProvider { error("Must not read") }.generate(":error"); fail("Failure expected") }
        catch (failure: ProviderFailure) { assertTrue(failure.userMessage.contains("演示失败")) }
    }
}
