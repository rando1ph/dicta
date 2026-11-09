package dev.dicta.prototype

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ActivityScenario
import dev.dicta.prototype.platform.MainActivity
import dev.dicta.prototype.platform.ProcessTextActivity
import dev.dicta.prototype.query.LookupState
import android.content.Intent
import androidx.lifecycle.Lifecycle
import org.junit.Before
import org.junit.After
import java.net.InetAddress
import dev.dicta.prototype.data.DictaRepository
import dev.dicta.prototype.profile.ProfileEngine
import dev.dicta.prototype.provider.*
import dev.dicta.prototype.security.CredentialStore
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class ByokDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var foreground: ActivityScenario<MainActivity>
    @Before fun openForegroundApp() { foreground=ActivityScenario.launch(MainActivity::class.java) }
    @After fun closeForegroundApp() { foreground.close() }
    @Test fun encryptedStorageReplacementClearAndReload() {
        val store = CredentialStore(context)
        val original = store.load()
        try {
            val sentinel = "test-credential-not-a-real-key"
            store.save(ProviderConfig("https://example.com/v1", "alternate-model",sentinel))
            val loaded = CredentialStore(context).load()
            assertTrue(loaded.apiKey == sentinel)
            assertEquals("alternate-model",loaded.model)
            assertEquals("https://example.com/v1",loaded.endpoint)
            val encrypted = File(context.noBackupFilesDir,"provider.enc").readBytes()
            assertFalse(encrypted.toString(Charsets.UTF_8).contains(sentinel))
            store.clear()
            assertTrue(CredentialStore(context).load().apiKey.isBlank())
        } finally { store.save(original) }
    }
    @Test fun missingAndWrongKeyHaveTerminalErrorsAndNoSavedResult() = runBlocking {
        val store = CredentialStore(context)
        val original = store.load()
        val repository = DictaRepository.get(context)
        val count = repository.history.value.size
        try {
            store.clear()
            try { repository.lookup("resilient"); fail("Expected missing key") } catch(e:ProviderFailure) { assertEquals(FailureKind.CONFIGURATION,e.kind) }
            store.save(ProviderConfig(apiKey="invalid-test-key"))
            try { repository.lookup("resilient"); fail("Expected authentication failure") } catch(e:ProviderFailure) { assertEquals(FailureKind.AUTH,e.kind) }
            assertEquals(count,repository.history.value.size)
        } finally { store.save(original) }
    }
    @Test fun actualDeviceTlsTransportErrorsJsonTimeoutAndCancellation() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val serving = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use { server ->
            server.useHttps(serving.sslSocketFactory(),false); server.start(InetAddress.getByName("127.0.0.1"),0)
            val client = DeepSeekProvider.defaultClient().newBuilder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
            val provider = DeepSeekProvider({ProviderConfig(server.url("/").toString().replace("localhost", "127.0.0.1"),"test-model","test-only")},{"JSON"},client)
            for ((code,kind) in listOf(429 to FailureKind.RATE_LIMIT,503 to FailureKind.SERVER)) {
                server.enqueue(MockResponse().setResponseCode(code))
                try { provider.generate("word"); fail() } catch(e:ProviderFailure) { assertEquals(kind,e.kind) }
            }
            server.enqueue(MockResponse().setBody("invalid-json"))
            try { provider.generate("word"); fail() } catch(e:ProviderFailure) { assertEquals(FailureKind.RESPONSE,e.kind) }
            val envelope=buildJsonObject { put("choices",buildJsonArray {add(buildJsonObject {
                put("finish_reason","stop");put("message",buildJsonObject {put("content","{}")})
            })}) }.toString()
            server.enqueue(MockResponse().setBody(envelope))
            val raw=provider.generate("word")
            val schema=context.assets.open("default/entry.schema.json").bufferedReader().readText()
            val template=context.assets.open("default/popup.template.json").bufferedReader().readText()
            assertThrows(dev.dicta.prototype.profile.ProfileFailure::class.java) { ProfileEngine(schema,template).decodeAndRender(raw) }
            val unavailable=MockWebServer().apply { useHttps(serving.sslSocketFactory(),false); start(InetAddress.getByName("127.0.0.1"),0) }
            val unavailableUrl=unavailable.url("/").toString().replace("localhost","127.0.0.1")
            unavailable.shutdown()
            val offline=DeepSeekProvider({ProviderConfig(unavailableUrl,"test","test-only")},{"JSON"},client)
            try { offline.generate("word"); fail() } catch(e:ProviderFailure) { assertEquals(FailureKind.NETWORK,e.kind) }
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val short=DeepSeekProvider({ProviderConfig(server.url("/").toString().replace("localhost", "127.0.0.1"),"test","test-only")},{"JSON"},client.newBuilder().callTimeout(400,TimeUnit.MILLISECONDS).build())
            try { short.generate("word"); fail() } catch(e:ProviderFailure) { assertEquals(FailureKind.TIMEOUT,e.kind) }
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val job=launch {provider.generate("word")}
            delay(300); job.cancelAndJoin()
            withTimeout(3000) {while(client.dispatcher.runningCallsCount()>0) delay(20)}
            assertTrue(job.isCancelled)
        }
    }
    @Test fun noKeyPopupDisplaysActionableErrorAndCloses() {
        val store=CredentialStore(context)
        val original=store.load()
        try {
            store.clear()
            val intent=Intent(context,ProcessTextActivity::class.java).setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                .putExtra(Intent.EXTRA_PROCESS_TEXT,"resilient")
            ActivityScenario.launch<ProcessTextActivity>(intent).use { scenario ->
                val deadline=System.currentTimeMillis()+5000
                var error:LookupState.Error?=null
                while(error==null && System.currentTimeMillis()<deadline) {
                    scenario.onActivity {error=it.model.state.value as? LookupState.Error}
                    Thread.sleep(50)
                }
                assertTrue(error?.message?.contains("API Key")==true)
                scenario.onActivity {it.dismiss()}
                val closeDeadline=System.currentTimeMillis()+5000
                while(scenario.state!=Lifecycle.State.DESTROYED && System.currentTimeMillis()<closeDeadline) Thread.sleep(50)
                assertEquals(Lifecycle.State.DESTROYED,scenario.state)
            }
        } finally {store.save(original)}
    }
    @Test fun persistedConfigurationMakesRealDeepSeekEntry() = runBlocking {
        assertTrue(CredentialStore(context).load().apiKey.isNotBlank())
        val result=DictaRepository.get(context).lookup("resilient")
        assertEquals("resilient",result.rendered.summary["title"]?.lowercase())
        assertTrue(result.rawJson.contains("definitionZh"))
    }
}
