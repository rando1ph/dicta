package dev.dicta.prototype.data

import android.content.Context
import dev.dicta.prototype.profile.ProfileEngine
import dev.dicta.prototype.profile.RenderedEntry
import dev.dicta.prototype.provider.FakeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/** Immutable session snapshot. M1 deliberately does not claim durable history. */
data class EntrySnapshot(
    val id: String,
    val query: String,
    val createdAt: Long,
    val rendered: RenderedEntry,
    val rawJson: String,
    val schemaJson: String,
    val templateJson: String,
)

class DictaRepository private constructor(context: Context) {
    private val assets = context.applicationContext.assets
    private fun asset(path: String) = assets.open(path).bufferedReader().use { it.readText() }
    private data class DefaultProfile(
        val schema: String,
        val template: String,
        val prompt: String,
        val manifest: String,
        val example: String,
        val engine: ProfileEngine,
    )
    // Reading/validating happens in lookup's background context, not app construction.
    private val profile by lazy {
        val schema = asset("default/entry.schema.json")
        val template = asset("default/popup.template.json")
        val prompt = asset("default/prompt.md")
        val manifest = asset("default/profile.json")
        val example = asset("default/example.json")
        val engine = ProfileEngine(schema, template)
        engine.decodeAndRender(example)
        DefaultProfile(schema, template, prompt, manifest, example, engine)
    }
    private val provider = FakeProvider { asset("examples/$it") }
    private val mutableHistory = MutableStateFlow<List<EntrySnapshot>>(emptyList())
    val history: StateFlow<List<EntrySnapshot>> = mutableHistory.asStateFlow()

    suspend fun lookup(query: String): EntrySnapshot = withContext(Dispatchers.Default) {
        val active = profile
        val raw = provider.generate(query)
        val rendered = active.engine.decodeAndRender(raw)
        currentCoroutineContext().ensureActive()
        EntrySnapshot(UUID.randomUUID().toString(), query, System.currentTimeMillis(), rendered,
            raw, active.schema, active.template)
    }

    @Synchronized fun remember(snapshot: EntrySnapshot) {
        if (mutableHistory.value.none { it.id == snapshot.id }) {
            mutableHistory.value = (listOf(snapshot) + mutableHistory.value).take(100)
        }
    }
    fun find(id: String): EntrySnapshot? = history.value.firstOrNull { it.id == id }

    companion object {
        @Volatile private var instance: DictaRepository? = null
        fun get(context: Context): DictaRepository = instance ?: synchronized(this) {
            instance ?: DictaRepository(context).also { instance = it }
        }
    }
}
