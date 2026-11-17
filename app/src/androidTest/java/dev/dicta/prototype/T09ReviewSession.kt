package dev.dicta.prototype

import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.ui.Appearance
import dev.dicta.prototype.ui.ThemeMode
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in, on-device TLS mock for actual reader/browser UI interaction and original PNG capture. */
class T09ReviewSession : T07MockFixture() {
    override val prepareDaily = false
    override val keepScreenOn = true

    init {
        check(context.packageName == "dev.randolf.dicta.debug") { "T09 requires the isolated Debug package" }
        check(!File(context.noBackupFilesDir, "provider.enc").exists()) {
            "T09 requires an unconfigured Debug package; existing credentials are preserved and must not be used"
        }
    }

    @Test fun readerAndChromeWithLocalTlsProvider() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("review") == "true")
        val ready = File(context.filesDir, "t09-review-ready")
        val stop = File(context.filesDir, "t09-review-stop")
        ready.delete()
        stop.delete()
        compose.runOnUiThread {
            Appearance.mode.value = ThemeMode.Dark
            Appearance.accent.value = 0
        }
        val description = "T09 isolated Debug; device-local TLS mock; no real API key or paid provider calls."
        ready.writeText("$description lookupPosts=${posts()}")
        try {
            val deadline = System.currentTimeMillis() + 45 * 60 * 1000
            var lastPosts = posts()
            while (!stop.exists() && System.currentTimeMillis() < deadline) {
                // Keep the instrumented frame clock moving while an external app owns the
                // foreground. Awaiting Compose roots would block on Readest/Chrome instead.
                compose.mainClock.advanceTimeBy(200)
                val currentPosts = posts()
                if (currentPosts != lastPosts) {
                    ready.writeText("$description lookupPosts=$currentPosts")
                    lastPosts = currentPosts
                }
                Thread.sleep(200)
            }
        } finally {
            ready.delete()
            stop.delete()
        }
    }
}
