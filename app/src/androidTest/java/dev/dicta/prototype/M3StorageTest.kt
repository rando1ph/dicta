package dev.dicta.prototype

import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.data.*
import dev.dicta.prototype.profile.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class M3StorageTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun asset(name:String)=context.assets.open("default/$name").bufferedReader().use {it.readText()}
    private fun entry(id:String)=StoredEntry(id,"ephemeral",123,asset("example.json"),asset("entry.schema.json"),asset("popup.template.json"),"builtin","test", "ephemeral","短暂的","", "adjective")
    private fun draft()=ProfileDraft("Test",asset("prompt.md"),asset("entry.schema.json"),asset("popup.template.json"),asset("example.json"))
    @Test fun historyBookmarkDailyAndProfilesSurviveCloseAndIndependentRemoval()=runBlocking {
        val name="m3-test-${UUID.randomUUID()}.db"
        var db=DictaDatabase.open(context,name)
        try {
            val dao=db.dao();val stored=entry("test-entry")
            dao.insert(stored);dao.insert(HistoryEvent("event","test-entry","popup",42));dao.insert(Bookmark("test-entry",43))
            dao.insert(Bookmark("test-entry",45)) // repeated save is idempotent
            dao.put(DailySelection("slot","test-entry","2026-11-10","Asia/Shanghai",44,0))
            val custom=draft();dao.insert(ProfileRevision("p1","p",1,custom.name,custom.prompt,custom.schema,custom.template,custom.example,"",custom.hash(),44));dao.put(Preference("activeProfile","p1"))
            db.close();db=DictaDatabase.open(context,name)
            assertEquals(1,db.dao().history().first().size);assertEquals(1,db.dao().vocabulary().first().size)
            assertEquals("test-entry",db.dao().daily("slot")?.entryId)
            assertEquals("p1",db.dao().preference("activeProfile")?.value)
            db.dao().deleteHistory(listOf("event"))
            assertTrue(db.dao().history().first().isEmpty());assertEquals(1,db.dao().vocabulary().first().size)
            assertNotNull(db.dao().entry("test-entry"))
            db.dao().insert(HistoryEvent("other-event","test-entry","home",50))
            db.dao().removeBookmarks(listOf("test-entry"))
            assertTrue(db.dao().vocabulary().first().isEmpty());assertEquals(1,db.dao().history().first().size)
            // Old entry still renders with its own template after a different Profile revision.
            val saved=db.dao().entry("test-entry")!!
            assertEquals("ephemeral",ProfileEngine(saved.schemaJson,saved.templateJson).decodeAndRender(saved.rawJson).summary["title"])
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun migrationOneToTwoPreservesRowsAndAddsRendererVersion()=runBlocking {
        val name="m3-migration-${UUID.randomUUID()}.db"
        var db=DictaDatabase.open(context,name)
        try {
            db.dao().insert(entry("legacy"));db.dao().insert(Bookmark("legacy",10))
            val sql=db.openHelper.writableDatabase
            sql.execSQL("ALTER TABLE entries DROP COLUMN rendererVersion")
            sql.version=1
            db.close();db=DictaDatabase.open(context,name)
            val restored=db.dao().entry("legacy")!!
            assertEquals(1,restored.rendererVersion);assertEquals(1,db.dao().vocabulary().first().size)
            assertEquals("ephemeral",ProfileEngine(restored.schemaJson,restored.templateJson).decodeAndRender(restored.rawJson).summary["title"])
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun futureDatabaseVersionIsRejectedWithoutDestructiveReset()=runBlocking {
        val name="m3-future-${UUID.randomUUID()}.db"
        var db=DictaDatabase.open(context,name)
        try {
            db.dao().insert(entry("keep"));db.openHelper.writableDatabase.version=99;db.close()
            db=DictaDatabase.open(context,name)
            try {db.dao().entry("keep");fail("Must reject unsupported downgrade")} catch(_:IllegalStateException) { }
            db.close()
            val raw=context.openOrCreateDatabase(name,0,null)
            try {raw.rawQuery("SELECT id FROM entries",null).use {assertTrue(it.moveToFirst());assertEquals("keep",it.getString(0))}} finally {raw.close()}
        } finally {db.close();context.deleteDatabase(name)}
    }
}
