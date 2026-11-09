package dev.dicta.prototype.provider

import dev.dicta.prototype.profile.ProfileEngine
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class DeepSeekProviderTest {
    private fun fixture() = File(System.getProperty("dicta.root"), "profiles/default/example.json").readText()
    private fun envelope(content: String = fixture(), finish: String = "stop") = buildJsonObject {
        put("choices", buildJsonArray { add(buildJsonObject {
            put("finish_reason", finish); put("message", buildJsonObject { put("content", content) })
        }) })
    }.toString()
    private fun <T> server(block: (MockWebServer, OkHttpClient) -> T): T {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverCert = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trusted = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        return MockWebServer().use { server ->
            server.useHttps(serverCert.sslSocketFactory(), false)
            server.start()
            val client = DeepSeekProvider.defaultClient().newBuilder().sslSocketFactory(trusted.sslSocketFactory(), trusted.trustManager).build()
            block(server, client)
        }
    }
    private fun provider(server: MockWebServer, client: OkHttpClient, key: String = "test-only") =
        DeepSeekProvider({ ProviderConfig(server.url("/").toString(), "test-model", key) }, {"Return JSON"}, client)
    @Test fun sendsJsonEnvelopeAndValidatesRealProfile() = server { server, client -> runBlocking {
        server.enqueue(MockResponse().setBody(envelope()))
        val content = provider(server, client).generate("luminous")
        val root = File(System.getProperty("dicta.root"), "profiles/default")
        ProfileEngine(File(root,"entry.schema.json").readText(), File(root,"popup.template.json").readText()).decodeAndRender(content)
        val request = server.takeRequest(2, TimeUnit.SECONDS)!!
        assertEquals("/chat/completions", request.path)
        assertEquals("Bearer test-only", request.getHeader("Authorization"))
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("json_object", body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("disabled", body["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("luminous", Json.parseToJsonElement(body["messages"]!!.jsonArray[1].jsonObject["content"]!!.jsonPrimitive.content).jsonObject["query"]!!.jsonPrimitive.content)
    } }
    @Test fun dailyUsesSameEndpointSchemaProtocolAndOneRequestWithExcludes() = server {server,client->runBlocking {
        server.enqueue(MockResponse().setBody(envelope()))
        provider(server,client).daily(listOf("verdant","petrichor"))
        val request=server.takeRequest(2,TimeUnit.SECONDS)!!
        assertEquals("/chat/completions",request.path)
        val body=Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        val user=Json.parseToJsonElement(body.getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonPrimitive.content).jsonObject
        assertEquals("daily",user["mode"]?.jsonPrimitive?.content)
        assertEquals("",user["query"]?.jsonPrimitive?.content)
        assertEquals(listOf("verdant","petrichor"),user["exclude"]?.jsonArray?.map {it.jsonPrimitive.content})
        assertEquals(1,server.requestCount)
    } }
    @Test fun missingKeySendsNoRequest() = server { server, client -> runBlocking {
        try { provider(server,client,"").generate("word"); fail() } catch(e: ProviderFailure) { assertEquals(FailureKind.CONFIGURATION,e.kind) }
        assertEquals(0,server.requestCount)
    } }
    @Test fun httpErrorsAreCategorizedAndNeverRetriedOrEchoed() = server { server, client -> runBlocking {
        for ((code,kind) in listOf(401 to FailureKind.AUTH,403 to FailureKind.AUTH,402 to FailureKind.BALANCE,429 to FailureKind.RATE_LIMIT,503 to FailureKind.SERVER,400 to FailureKind.REQUEST)) {
            server.enqueue(MockResponse().setResponseCode(code).setBody("sensitive-upstream-body"))
            try { provider(server,client).generate("word"); fail() } catch(e:ProviderFailure) {
                assertEquals(kind,e.kind); assertFalse(e.message!!.contains("sensitive"))
            }
        }
        assertEquals(6,server.requestCount)
    } }
    @Test fun malformedBlankDuplicateTruncatedAndOversizedResponsesFail() = server { server, client -> runBlocking {
        for (body in listOf("not-json", "{}", envelope("   "), envelope("{}","length"), " ".repeat(131073), "{\"choices\":[],\"choices\":[]}")) {
            server.enqueue(MockResponse().setBody(body))
            try { provider(server,client).generate("word"); fail() } catch(e:ProviderFailure) { assertEquals(FailureKind.RESPONSE,e.kind) }
        }
    } }
    @Test fun timeoutTerminatesWithoutAutomaticRetry() = server { server, client -> runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val short = client.newBuilder().callTimeout(250,TimeUnit.MILLISECONDS).build()
        try { provider(server,short).generate("word"); fail() } catch(e:ProviderFailure) { assertEquals(FailureKind.TIMEOUT,e.kind) }
        assertEquals(1,server.requestCount)
    } }
    @Test fun cancellationCancelsActualHttpCall() = server { server, client -> runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch { provider(server,client).generate("word") }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(3,TimeUnit.SECONDS)) }
        job.cancelAndJoin()
        withTimeout(3000) { while(client.dispatcher.runningCallsCount()>0) delay(20) }
        assertTrue(job.isCancelled)
        assertEquals(1,server.requestCount)
    } }
    @Test fun networkFailureIsSafeAndTerminal() = server { server,client -> runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        try { provider(server,client).generate("word"); fail() } catch(e:ProviderFailure) { assertEquals(FailureKind.NETWORK,e.kind) }
    } }
    @Test fun redirectsNeverForwardCredentials() = server { server,client -> runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).addHeader("Location",server.url("/elsewhere")))
        try { provider(server,client).generate("word"); fail() } catch(e:ProviderFailure) { assertEquals(FailureKind.REQUEST,e.kind) }
        assertEquals(1,server.requestCount)
    } }
    @Test fun serviceUnavailableWithRetryAfterZeroMustNotRepeatPaidRequest() = server { server,client -> runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).addHeader("Retry-After","0"))
        server.enqueue(MockResponse().setBody(envelope()))
        try { provider(server,client).generate("word"); fail("Must not automatically retry") }
        catch(e:ProviderFailure) { assertEquals(FailureKind.SERVER,e.kind) }
        assertEquals(1,server.requestCount)
    } }
    @Test fun stalledResponseBodyIsClassifiedAsTimeout() = server { server,client -> runBlocking {
        server.enqueue(MockResponse().setBody(envelope()).setBodyDelay(2,TimeUnit.SECONDS))
        val short=client.newBuilder().readTimeout(250,TimeUnit.MILLISECONDS).build()
        try { provider(server,short).generate("word"); fail() }
        catch(e:ProviderFailure) { assertEquals(FailureKind.TIMEOUT,e.kind) }
    } }
    @Test fun endpointAndModelAreStrictAndSecretsAreRedacted() {
        for(url in listOf("http://localhost", "https://user:pass@example.com", "https://example.com?key=value", "https://example.com/#token", "garbage")) {
            try { ProviderConfig(url).validated(); fail() } catch (_:ProviderFailure) { }
        }
        assertEquals("https://api.deepseek.com/v1",ProviderConfig(" https://api.deepseek.com/v1/ ").validated().endpoint)
        assertFalse(ProviderConfig(apiKey="transient-secret").toString().contains("transient-secret"))
    }
}
