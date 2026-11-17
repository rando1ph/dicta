package dev.dicta.prototype

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.data.*
import dev.dicta.prototype.profile.ProfileDraft
import dev.dicta.prototype.profile.ProfileEngine
import dev.dicta.prototype.provider.*
import dev.dicta.prototype.security.CredentialStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Runs against actual non-debuggable, original-certificate APKs on a fresh emulator.
 * Never run this fixture on a physical device or an existing user's installation.
 * No network requests, real credentials, destructive resets or application uninstall.
 */
class UpgradeDataTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val marker get() = File(context.filesDir, "t11-upgrade-baseline.json")
    private val encrypted get() = File(context.noBackupFilesDir, "provider.enc")
    private val certificate = "6c7e7a2bb39749ef9d7cf6a0c534d6e7b39e3fdc5ab9b9558aafc7cdab6bceb8"
    private val tables = listOf("entries", "history", "bookmarks", "daily", "profiles", "preferences")
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun asset(name: String) = context.assets.open("default/$name").bufferedReader().use { it.readText() }

    private fun guard(version: String, code: Long) {
        check(Build.HARDWARE in listOf("ranchu", "goldfish")) { "Emulator only" }
        assertEquals("dev.randolf.dicta", context.packageName)
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        assertEquals(version, info.versionName)
        assertEquals(code, info.longVersionCode)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        assertEquals(listOf(certificate), info.signingInfo!!.apkContentsSigners.map { hash(it.toByteArray()) })
    }

    private fun snapshot(db: DictaDatabase): String {
        val result = JSONObject()
        for (table in tables) {
            val rows = JSONArray()
            db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY 1").use { cursor ->
                while (cursor.moveToNext()) {
                    val row = JSONArray()
                    for (i in 0 until cursor.columnCount) row.put(if (cursor.isNull(i)) JSONObject.NULL else cursor.getString(i))
                    rows.put(row)
                }
            }
            result.put(table, rows)
        }
        return hash(result.toString().toByteArray())
    }

    private fun checkCredentials() {
        val config = CredentialStore(context).load()
        assertEquals("https://127.0.0.1:9/v1", config.endpoint)
        assertEquals("t11-inert-model", config.model)
        assertEquals("t11-placeholder-not-a-real-api-key", config.apiKey)
        assertEquals(ProviderProtocol.COMPATIBLE, config.protocol)
        assertEquals(JsonOutput.PROMPT_ONLY, config.output)
    }

    @Test fun seedOfficialV110() = runBlocking {
        guard("1.1.0", 6)
        check(!marker.exists() && !encrypted.exists() && !context.getDatabasePath("dicta.db").exists()) { "Fresh test app data required; never reset existing data" }
        CredentialStore(context).save(ProviderConfig("https://127.0.0.1:9/v1", "t11-inert-model", "t11-placeholder-not-a-real-api-key", ProviderProtocol.COMPATIBLE, JsonOutput.PROMPT_ONLY))
        checkCredentials()
        val db = DictaDatabase.open(context)
        try {
            val dao = db.dao()
            val draft = ProfileDraft("T11 retained Profile", asset("prompt.md"), asset("entry.schema.json"), asset("popup.template.json"), asset("example.json"), "t11-profile-model")
            dao.insert(ProfileRevision("t11-profile-r1", "t11-profile", 1, draft.name, draft.prompt, draft.schema, draft.template, draft.example, draft.model, draft.hash(), 100))
            dao.put(Preference("activeProfile", "t11-profile-r1"))
            dao.put(Preference("recentDaily", "[\"ephemeral\"]"))
            for ((index, word) in listOf("ephemeral", "luminous", "verdant").withIndex()) {
                val raw = JSONObject(draft.example).put("word", word).toString()
                val rendered = ProfileEngine(draft.schema, draft.template).decodeAndRender(raw)
                dao.insert(StoredEntry("t11-$index", word, 100L + index, raw, draft.schema, draft.template, "t11-profile-r1", "t11-cache-$index", rendered.summary["title"].orEmpty(), "fixture", "", "test"))
            }
            dao.insert(HistoryEvent("t11-h0", "t11-0", "popup", 101))
            dao.insert(HistoryEvent("t11-h1", "t11-1", "home", 102))
            dao.insert(Bookmark("t11-0", 103))
            dao.insert(Bookmark("t11-2", 104)) // Saved entry independent of history.
            dao.put(DailySelection("t11-daily", "t11-0", "2026-11-18", "Asia/Shanghai", 105, 0))
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            marker.writeText(JSONObject().put("databaseSnapshot", snapshot(db)).put("ciphertextSha256", hash(encrypted.readBytes())).put("uid", context.applicationInfo.uid).put("firstInstallTime", packageInfo.firstInstallTime).toString())
        } finally { db.close() }
    }

    @Test fun verifyOfficialV120() = runBlocking {
        guard("1.2.0", 7)
        val baseline = JSONObject(marker.readText())
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        assertEquals(baseline.getInt("uid"), context.applicationInfo.uid)
        assertEquals(baseline.getLong("firstInstallTime"), packageInfo.firstInstallTime)
        assertEquals(baseline.getString("ciphertextSha256"), hash(encrypted.readBytes()))
        checkCredentials() // Actual Keystore decryption after the signed update.
        val db = DictaDatabase.open(context)
        try {
            val dao = db.dao()
            assertEquals(baseline.getString("databaseSnapshot"), snapshot(db))
            assertEquals(2, db.openHelper.readableDatabase.version)
            assertEquals(listOf("luminous", "ephemeral"), dao.history().first().map { it.entry.title })
            assertEquals(listOf("verdant", "ephemeral"), dao.vocabulary().first().map { it.title })
            assertEquals("t11-profile-r1", dao.preference("activeProfile")!!.value)
            assertEquals("t11-0", dao.daily("t11-daily")!!.entryId)
            val saved = dao.entry("t11-0")!!
            assertEquals("ephemeral", ProfileEngine(saved.schemaJson, saved.templateJson).decodeAndRender(saved.rawJson).summary["title"])
            assertEquals("t11-profile-model", dao.profile("t11-profile-r1")!!.model)
            // Verify the retained DB still accepts writes, then remove only this new fixture row.
            dao.insert(HistoryEvent("t11-write-probe", "t11-2", "home", 106))
            assertEquals(3, dao.history().first().size)
            dao.deleteHistory(listOf("t11-write-probe"))
            assertEquals(baseline.getString("databaseSnapshot"), snapshot(db))
            assertEquals(2, dao.vocabulary().first().size)
        } finally { db.close() }
    }
}
