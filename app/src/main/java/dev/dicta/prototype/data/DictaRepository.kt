package dev.dicta.prototype.data

import android.content.Context
import androidx.room.withTransaction
import dev.dicta.prototype.profile.*
import dev.dicta.prototype.provider.*
import dev.dicta.prototype.security.CredentialStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Each saved entry owns its validated data, schema and template. */
data class EntrySnapshot(
    val id:String, val query:String, val createdAt:Long, val rendered:RenderedEntry,
    val rawJson:String, val schemaJson:String, val templateJson:String,
    val profileRevision:String="builtin", val cacheKey:String="", val rendererVersion:Int=1,
    val historyEventId:String=id, val queriedAt:Long=createdAt, val compatibilityWarning:String?=null,
)
data class ProfileChoice(val revision:ProfileRevision, val builtin:Boolean=false) {
    val draft get()=ProfileDraft(revision.name,revision.prompt,revision.schema,revision.template,revision.example,revision.model)
}
data class DailyState(val entry:EntrySnapshot?=null, val busy:Boolean=false, val message:String="", val localDate:String="")
object DailyPolicy {
    fun slot(date:LocalDate,zone:ZoneId,identity:String)="${date}|${zone.id}|$identity"
    fun normalized(word:String)=word.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+")," ")
    fun requireNew(word:String,exclude:List<String>) {
        if(word.isBlank() || normalized(word) in exclude) throw ProviderFailure("模型返回了重复单词；已保留原卡片，可手动再换一次。")
    }
    fun excludes(recent:List<String>,current:String?)=(listOfNotNull(current)+recent).map(::normalized).filter {it.isNotBlank()}.distinct().take(21)
}

class DictaRepository private constructor(context:Context) {
    val database=DictaDatabase.open(context)
    private val dao=database.dao()
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    val credentials=CredentialStore(context.applicationContext)
    private val assets=context.applicationContext.assets
    private fun asset(path:String)=assets.open("default/$path").bufferedReader().use {it.readText()}
    val builtin:ProfileChoice by lazy {
        val draft=ProfileDraft("Standard Literary · 文学精读",asset("prompt.md"),asset("entry.schema.json"),asset("popup.template.json"),asset("example.json"))
        draft.preview()
        ProfileChoice(ProfileRevision("builtin","builtin",1,draft.name,draft.prompt,draft.schema,draft.template,draft.example,"",draft.hash(),0),true)
    }
    val warning=MutableStateFlow("")
    private val mutableHistory=MutableStateFlow<List<EntrySnapshot>>(emptyList())
    val history=mutableHistory.asStateFlow()
    private val mutableVocabulary=MutableStateFlow<List<EntrySnapshot>>(emptyList())
    val vocabulary=mutableVocabulary.asStateFlow()
    val profiles=MutableStateFlow<List<ProfileChoice>>(emptyList())
    val activeProfile=MutableStateFlow<ProfileChoice?>(null)
    val daily=MutableStateFlow(DailyState())
    private val dailyMutex=Mutex()
    init {
        scope.launch {
            try { dao.history().collect { rows-> mutableHistory.value=rows.map {row->decode(row.entry).copy(historyEventId=row.eventId,queriedAt=row.queriedAt)} } }
            catch(_:Exception) { warning.value="无法读取历史。已保留本地数据，请重新打开应用。" }
        }
        scope.launch {
            try { dao.vocabulary().collect { rows->mutableVocabulary.value=rows.map(::decode) } }
            catch(_:Exception) { warning.value="无法读取收藏。已保留本地数据。" }
        }
        scope.launch {
            try { dao.profiles().combine(dao.preferences()) { rows,prefs->
                val choices=listOf(builtin)+rows.groupBy {it.profileId}.values.map { list->ProfileChoice(list.maxBy {it.revision}) }
                profiles.value=choices
                val requested=prefs.firstOrNull {it.name=="activeProfile"}?.value
                val selected=rows.firstOrNull {it.id==requested}?.let {ProfileChoice(it)} ?: builtin
                activeProfile.value=try { selected.draft.preview();selected } catch(_:Exception) {
                    warning.value="当前 Profile 损坏，已安全使用预置默认；用户配置已保留。";builtin
                }
            }.collect() } catch(_:Exception) { warning.value="配置存储暂不可用。已保留本地数据。" }
        }
    }
    private fun decode(stored:StoredEntry):EntrySnapshot {
        var warning:String?=null
        val rendered=try {
            if(stored.rendererVersion!=1) throw ProfileFailure("renderer_version")
            ProfileEngine(stored.schemaJson,stored.templateJson).decodeAndRender(stored.rawJson)
        } catch(_:Exception) {
            warning="此记录的展示版本不兼容或数据损坏。以下为已保存的原始数据；可在设置恢复默认 Profile。"
            RenderedEntry(mapOf("title" to stored.title,"subtitle" to stored.subtitle,"badge" to stored.badge),listOf(RenderNode("text",value=stored.rawJson.take(131072))),"")
        }
        return EntrySnapshot(stored.id,stored.query,stored.createdAt,rendered,stored.rawJson,stored.schemaJson,stored.templateJson,stored.profileRevision,stored.cacheKey,stored.rendererVersion,compatibilityWarning=warning)
    }
    private fun EntrySnapshot.stored()=StoredEntry(id,query,createdAt,rawJson,schemaJson,templateJson,profileRevision,cacheKey,rendered.summary["title"].orEmpty(),rendered.summary["subtitle"].orEmpty(),rendered.summary["secondary"].orEmpty(),rendered.summary["badge"].orEmpty(),rendererVersion)
    suspend fun currentProfile():ProfileChoice=withContext(Dispatchers.IO) {
        val id=dao.preference("activeProfile")?.value
        val choice=if(id==null || id=="builtin") builtin else dao.profile(id)?.let {ProfileChoice(it)} ?: builtin
        try {choice.draft.preview();choice} catch(_:Exception) {warning.value="Profile 校验失败，已使用预置默认，原配置已保留。";builtin}
    }
    private fun provider(choice:ProfileChoice,config:ProviderConfig)=DeepSeekProvider({ProviderConfig(config.endpoint,choice.draft.model.ifBlank {config.model},config.apiKey)},choice.draft::systemPrompt)
    private fun identity(choice:ProfileChoice,config:ProviderConfig)=ProfileDraft.digest(choice.revision.contentHash+"|"+config.endpoint+"|"+choice.draft.model.ifBlank {config.model})
    suspend fun lookup(query:String,useCache:Boolean=true):EntrySnapshot=withContext(Dispatchers.IO) {
        val choice=currentProfile();val config=credentials.load().validated()
        if(config.apiKey.isBlank()) throw ProviderFailure("请先在设置中添加 API Key。",FailureKind.CONFIGURATION)
        val key=ProfileDraft.digest(DailyPolicy.normalized(query)+"|"+identity(choice,config))
        val cached=if(useCache) dao.cached(key) else null
        if(cached!=null) {
            val decoded=decode(cached)
            if(decoded.compatibilityWarning==null) return@withContext decoded.copy(query=query)
        }
        val raw=provider(choice,config).generate(query)
        val rendered=ProfileEngine(choice.draft.schema,choice.draft.template).decodeAndRender(raw)
        currentCoroutineContext().ensureActive()
        EntrySnapshot(UUID.randomUUID().toString(),query,System.currentTimeMillis(),rendered,raw,choice.draft.schema,choice.draft.template,choice.revision.id,key)
    }
    suspend fun remember(snapshot:EntrySnapshot,source:String="home")=withContext(Dispatchers.IO) {
        database.withTransaction {
            dao.insert(snapshot.stored())
            dao.insert(HistoryEvent(UUID.randomUUID().toString(),snapshot.id,source,System.currentTimeMillis()))
        }
    }
    suspend fun find(id:String)=withContext(Dispatchers.IO) {dao.entry(id)?.let(::decode)}
    suspend fun setBookmarks(ids:List<String>,saved:Boolean)=withContext(Dispatchers.IO) {
        database.withTransaction { if(saved) ids.distinct().forEach { if(dao.entry(it)!=null) dao.insert(Bookmark(it,System.currentTimeMillis())) } else dao.removeBookmarks(ids) }
    }
    suspend fun deleteHistory(ids:List<String>)=withContext(Dispatchers.IO) {dao.deleteHistory(ids)}
    suspend fun saveProfile(draft:ProfileDraft,profileId:String?=null):ProfileChoice=withContext(Dispatchers.IO) {
        draft.preview()
        database.withTransaction {
            val id=profileId?.takeUnless {it=="builtin"} ?: UUID.randomUUID().toString()
            val revision=(dao.latestProfile(id)?.revision ?: 0)+1
            val row=ProfileRevision(UUID.randomUUID().toString(),id,revision,draft.name,draft.prompt,draft.schema,draft.template,draft.example,draft.model,draft.hash(),System.currentTimeMillis())
            dao.insert(row);dao.put(Preference("activeProfile",row.id));ProfileChoice(row)
        }
    }
    suspend fun activate(choice:ProfileChoice)=withContext(Dispatchers.IO) {choice.draft.preview();dao.put(Preference("activeProfile",choice.revision.id))}
    suspend fun ensureDaily(refresh:Boolean=false,date:LocalDate=LocalDate.now(),zone:ZoneId=ZoneId.systemDefault()) {
        if(!dailyMutex.tryLock()) return
        try { withContext(Dispatchers.IO) {
            val choice=currentProfile();val config=credentials.load().validated()
            val slot=DailyPolicy.slot(date,zone,identity(choice,config))
            val previous=dao.daily(slot)
            val entry=previous?.let {find(it.entryId)}
            if(entry!=null && entry.compatibilityWarning==null && !refresh) {daily.value=DailyState(entry,localDate=date.toString());return@withContext}
            val latest=if(entry==null && daily.value.entry==null) dao.daily().first().firstOrNull() else null
            val oldEntry=entry ?: daily.value.entry ?: latest?.let {find(it.entryId)}
            val oldDate=if(entry!=null) date.toString() else daily.value.localDate.ifBlank {latest?.localDate.orEmpty()}
            daily.value=DailyState(oldEntry,true,localDate=oldDate)
            try {
                val recent=dao.preference("recentDaily")?.value?.let {raw->try {kotlinx.serialization.json.Json.parseToJsonElement(raw).let {it as kotlinx.serialization.json.JsonArray}.map { (it as kotlinx.serialization.json.JsonPrimitive).content }} catch(_:Exception) {emptyList()} } ?: dao.dailyWords()
                val exclude=DailyPolicy.excludes(recent,daily.value.entry?.rendered?.summary?.get("title"))
                val raw=provider(choice,config).daily(exclude)
                val rendered=ProfileEngine(choice.draft.schema,choice.draft.template).decodeAndRender(raw)
                val word=rendered.summary["title"].orEmpty()
                DailyPolicy.requireNew(word,exclude)
                val snapshot=EntrySnapshot(UUID.randomUUID().toString(),word,System.currentTimeMillis(),rendered,raw,choice.draft.schema,choice.draft.template,choice.revision.id)
                currentCoroutineContext().ensureActive()
                database.withTransaction {
                    dao.insert(snapshot.stored())
                    dao.put(Preference("recentDaily",kotlinx.serialization.json.JsonArray((listOf(DailyPolicy.normalized(word))+recent).distinct().take(20).map {kotlinx.serialization.json.JsonPrimitive(it)}).toString()))
                    dao.put(DailySelection(slot,snapshot.id,date.toString(),zone.id,System.currentTimeMillis(),(previous?.refreshCounter ?: -1)+1))
                }
                daily.value=DailyState(snapshot,localDate=date.toString())
            } catch(cancelled:CancellationException) {throw cancelled}
            catch(error:Exception) {daily.value=daily.value.copy(busy=false,message=(error as? ProviderFailure)?.userMessage ?: "每日词暂不可用，已保留原卡片。")}
        } } catch(cancelled:CancellationException) {daily.value=daily.value.copy(busy=false);throw cancelled}
        catch(_:Exception) {daily.value=daily.value.copy(busy=false,message="无法读取每日词配置；请检查设置。")}
        finally {dailyMutex.unlock()}
    }
    companion object {
        @Volatile private var instance:DictaRepository?=null
        fun get(context:Context)=instance ?: synchronized(this) { instance ?: DictaRepository(context.applicationContext).also {instance=it} }
    }
}
