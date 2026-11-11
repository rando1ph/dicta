package dev.dicta.prototype

import android.content.Context
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.data.*
import dev.dicta.prototype.platform.MainActivity
import dev.dicta.prototype.provider.*
import dev.dicta.prototype.ui.Appearance
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.junit.*
import java.io.File
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/** Local TLS only, test APK only. Restores exact credential bytes and only removes its own rows. */
open class T07MockFixture {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    protected val context:Context get()=InstrumentationRegistry.getInstrumentation().targetContext
    protected val repo get()=DictaRepository.get(context)
    protected lateinit var server:MockWebServer
    private lateinit var sourceModel:dev.dicta.prototype.query.LookupViewModel
    protected val requests=CopyOnWriteArrayList<Pair<String,String>>()
    @Volatile protected var modelStatus=200
    @Volatile protected var lookupStatus=200
    @Volatile protected var lookupDelay=0L
    private var originalClient:okhttp3.OkHttpClient?=null
    private var originalCredential:ByteArray?=null
    private lateinit var initialEntries:Set<String>
    private lateinit var initialProfiles:Set<String>
    private var originalPreferences=emptyList<Preference>()
    private var originalMode=Appearance.mode.value
    private var originalAccent=Appearance.accent.value
    private val credentialFile get()=File(context.noBackupFilesDir,"provider.enc")

    private fun ids(table:String):Set<String> = repo.database.openHelper.readableDatabase.query("SELECT id FROM $table").use {c->buildSet {while(c.moveToNext()) add(c.getString(0))}}
    @Before fun setUpMock() {
        sourceModel=compose.activity.model
        originalCredential=credentialFile.takeIf {it.exists()}?.readBytes()
        originalClient=repo.providerClient
        initialEntries=ids("entries");initialProfiles=ids("profiles")
        originalPreferences=runBlocking {repo.database.dao().preferences().first()}
        originalMode=Appearance.mode.value;originalAccent=Appearance.accent.value
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val serving=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        server=MockWebServer().apply {
            useHttps(serving.sslSocketFactory(),false)
            dispatcher=object:Dispatcher() {
                override fun dispatch(request:RecordedRequest):MockResponse {
                    val path=request.path.orEmpty();val body=request.body.readUtf8()
                    requests.add(path to body)
                    if(path=="/v1/models") return MockResponse().setResponseCode(modelStatus).setBody("""{"data":[{"id":"dicta-mock"},{"id":"dicta-mock-compact"},{"id":"sample-text-model"}]}""")
                    if(path!="/v1/chat/completions") return MockResponse().setResponseCode(404)
                    val query=runCatching {
                        val messages=Json.parseToJsonElement(body).jsonObject.getValue("messages").jsonArray
                        Json.parseToJsonElement(messages[1].jsonObject.getValue("content").jsonPrimitive.content).jsonObject["query"]?.jsonPrimitive?.content.orEmpty()
                    }.getOrDefault("")
                    val word=query.ifBlank {"ephemeral"}
                    val raw=JsonObject(Json.parseToJsonElement(repo.builtin.draft.example).jsonObject.toMutableMap().apply {put("word",JsonPrimitive(word))}).toString()
                    val envelope=buildJsonObject {put("choices",buildJsonArray {add(buildJsonObject {put("finish_reason","stop");put("message",buildJsonObject {put("content",raw)})})})}.toString()
                    return MockResponse().setResponseCode(lookupStatus).setBody(envelope).setBodyDelay(lookupDelay,TimeUnit.MILLISECONDS)
                }
            }
            start(InetAddress.getByName("127.0.0.1"),0)
        }
        repo.providerClient=OpenAiCompatibleProvider.defaultClient().newBuilder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
        val endpoint="https://127.0.0.1:${server.port}/v1"
        compose.runOnUiThread {
            compose.activity.model.saveConfig(endpoint,"dicta-mock","test-only-not-a-real-key",ProviderProtocol.COMPATIBLE)
        }
        compose.waitUntil(5000) {!compose.activity.model.configBusy.value && compose.activity.model.configuration.value.hasKey}
    }
    protected fun awaitSuccess() {
        compose.waitUntil(10000) {compose.activity.model.state.value is dev.dicta.prototype.query.LookupState.Success}
        compose.waitForIdle()
    }
    protected fun seed(word:String="ephemeral"):EntrySnapshot=runBlocking {
        val entry=repo.lookup(word,useCache=false)
        repo.remember(entry);repo.setBookmarks(listOf(entry.id),true);entry
    }
    protected fun posts()=requests.count {it.first.endsWith("chat/completions")}
    @After fun tearDownMock() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {sourceModel.closeDetail();sourceModel.cancelConfig();sourceModel.cancelDiscovery()}
        repo.providerClient=originalClient ?: OpenAiCompatibleProvider.defaultClient()
        originalCredential?.let {credentialFile.writeBytes(it)} ?: credentialFile.delete()
        val added=ids("entries")-initialEntries
        val addedProfiles=ids("profiles")-initialProfiles
        runBlocking {
            val db=repo.database.openHelper.writableDatabase
            added.forEach {id->
                db.execSQL("DELETE FROM history WHERE entryId = ?",arrayOf(id))
                db.execSQL("DELETE FROM bookmarks WHERE entryId = ?",arrayOf(id))
                db.execSQL("DELETE FROM daily WHERE entryId = ?",arrayOf(id))
                db.execSQL("DELETE FROM entries WHERE id = ?",arrayOf(id))
            }
            addedProfiles.forEach {db.execSQL("DELETE FROM profiles WHERE id = ?",arrayOf(it))}
            for(name in listOf("activeProfile","recentDaily")) {
                val original=originalPreferences.firstOrNull {it.name==name}
                if(original!=null) repo.database.dao().put(original) else db.execSQL("DELETE FROM preferences WHERE name = ?",arrayOf(name))
            }
        }
        Appearance.mode.value=originalMode;Appearance.accent.value=originalAccent
        repo.daily.value=DailyState()
        runBlocking {repo.ensureDaily(false)}
        server.shutdown()
    }
}
