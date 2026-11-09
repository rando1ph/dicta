package dev.dicta.prototype

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import dev.dicta.prototype.data.*
import dev.dicta.prototype.profile.*
import dev.dicta.prototype.ui.ProfileFiles
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class M3ProfileDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val repo get()=DictaRepository.get(context)
    @Test fun promptVariablesExpandWithAndroidRegexAndPreserveInsertedLiterals()=runBlocking {
        val original=repo.currentProfile().draft
        val custom=original.copy(schema=original.schema.replace("Dicta default lexical entry v1","{{example_json}}"))
        val prompt=custom.systemPrompt()
        assertTrue(prompt.contains(custom.schema));assertTrue(prompt.contains(custom.example))
        assertTrue(prompt.contains("{{example_json}}"))
    }
    @Test fun fileImportExportRevisionSwitchAndOldSnapshotRemainReadable()=runBlocking {
        val original=repo.currentProfile()
        val file=File(context.cacheDir,"m3-profile-${UUID.randomUUID()}.json")
        val id=UUID.randomUUID().toString()
        var testProfileId=""
        try {
            val draft=original.draft.copy(name="Device file roundtrip",prompt=original.draft.prompt+"\nUse short examples.")
            ProfileFiles.write(context,Uri.fromFile(file),draft)
            val imported=ProfileDraft.import(ProfileFiles.read(context,Uri.fromFile(file)))
            assertEquals(draft.hash(),imported.hash())
            assertFalse(file.readText().contains(repo.credentials.load().apiKey))
            val first=repo.saveProfile(imported);testProfileId=first.revision.profileId
            val next=repo.saveProfile(imported.copy(name="Device updated revision"),first.revision.profileId)
            assertEquals(first.revision.revision+1,next.revision.revision)
            assertNotNull(repo.database.dao().profile(first.revision.id))
            val old=original.draft
            repo.database.dao().insert(StoredEntry(id,"ephemeral",10,old.example,old.schema,old.template,original.revision.id,"", "ephemeral","短暂的","","adjective"))
            val stored=repo.find(id)!!
            assertNull(stored.compatibilityWarning);assertEquals("ephemeral",stored.rendered.summary["title"])
            repo.activate(original)
            assertEquals(original.revision.id,repo.currentProfile().revision.id)
        } finally {
            repo.activate(original);file.delete()
            repo.database.openHelper.writableDatabase.execSQL("DELETE FROM entries WHERE id = ?",arrayOf(id))
            if(testProfileId.isNotBlank()) repo.database.openHelper.writableDatabase.execSQL("DELETE FROM profiles WHERE profileId = ?",arrayOf(testProfileId))
        }
    }
    @Test fun damagedActiveProfileFallsBackAndFutureRendererRetainsRawData()=runBlocking {
        val original=repo.currentProfile();val id=UUID.randomUUID().toString();val entryId=UUID.randomUUID().toString()
        try {
            val old=original.draft
            repo.database.dao().insert(ProfileRevision(id,id,1,"Damaged test",old.prompt,"{bad",old.template,old.example,"","invalid",1))
            repo.database.dao().put(Preference("activeProfile",id))
            assertTrue(repo.currentProfile().builtin)
            assertNotNull(repo.database.dao().profile(id))
            repo.database.dao().insert(StoredEntry(entryId,"legacy",10,old.example,old.schema,old.template,id,"", "legacy","original","","",99))
            val stored=repo.find(entryId)!!
            assertNotNull(stored.compatibilityWarning);assertEquals(old.example,stored.rawJson)
            assertEquals(old.example,stored.rendered.nodes.single().value)
        } finally {
            repo.activate(original)
            repo.database.openHelper.writableDatabase.execSQL("DELETE FROM entries WHERE id = ?",arrayOf(entryId))
            repo.database.openHelper.writableDatabase.execSQL("DELETE FROM profiles WHERE id = ?",arrayOf(id))
            repo.warning.value=""
        }
    }
}
