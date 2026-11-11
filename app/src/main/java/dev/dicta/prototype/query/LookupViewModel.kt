package dev.dicta.prototype.query

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dicta.prototype.data.DictaRepository
import dev.dicta.prototype.profile.*
import dev.dicta.prototype.data.ProfileChoice
import dev.dicta.prototype.provider.ProviderConfig
import dev.dicta.prototype.provider.ProviderFailure
import dev.dicta.prototype.provider.ProviderProtocol
import dev.dicta.prototype.provider.JsonOutput
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

// Deliberately excludes plaintext credentials from observable/UI state.
data class ConfigSummary(val endpoint: String = "https://api.deepseek.com", val model: String = "deepseek-flash", val hasKey: Boolean = false,
    val protocol:ProviderProtocol=ProviderProtocol.AUTO,val output:JsonOutput=JsonOutput.JSON_OBJECT,val revision:Long=0)
data class ModelDiscovery(val models:List<String> = emptyList(),val busy:Boolean=false,val message:String="",val failureKind:dev.dicta.prototype.provider.FailureKind?=null)


class LookupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DictaRepository.get(application)
    private var source = "home"
    private var openJob:Job?=null
    private val session = LookupSession(viewModelScope, {repository.lookup(it)}, { repository.remember(it,source) })
    val state = session.state
    val history = repository.history
    val vocabulary=repository.vocabulary
    val profiles=repository.profiles
    val activeProfile=repository.activeProfile
    val daily=repository.daily
    val storageMessage=repository.warning
    val actionMessage=MutableStateFlow("")
    val profileMessage=MutableStateFlow("")
    val profileBusy=MutableStateFlow(false)
    val configuration = MutableStateFlow(ConfigSummary())
    val configMessage = MutableStateFlow("")
    val configBusy = MutableStateFlow(false)
    private var configJob: Job? = null
    private var configGeneration = 0L
    val modelDiscovery=MutableStateFlow(ModelDiscovery())
    private var discoveryJob:Job?=null
    private var discoveryGeneration=0L
    init { refreshConfig() }
    private fun refreshConfig() {
        viewModelScope.launch {
            try {
                val config = withContext(Dispatchers.IO) { repository.credentials.load() }
                configuration.value = ConfigSummary(config.endpoint, config.model, config.apiKey.isNotBlank(),config.protocol,config.output)
            } catch (e: ProviderFailure) { configMessage.value = e.userMessage }
        }
    }
    fun saveConfig(endpoint: String, model: String, replacement: String,protocol:ProviderProtocol=configuration.value.protocol,output:JsonOutput=configuration.value.output) = configAction {
        val old = try { repository.credentials.load() } catch (_: ProviderFailure) { ProviderConfig() }
        val next = ProviderConfig(endpoint, model, replacement,protocol,output).retainingKeyFrom(old)
        cancelDiscovery()
        repository.credentials.save(next)
        configuration.value = ConfigSummary(next.endpoint, next.model, next.apiKey.isNotBlank(),next.protocol,next.output,configuration.value.revision+1)
        configMessage.value = "配置已安全保存。"
    }
    fun clearConfig() = configAction {
        cancelDiscovery()
        repository.credentials.clear()
        configuration.value = ConfigSummary(revision=configuration.value.revision+1)
        configMessage.value = "配置与密钥已清除。"
    }
    fun testConfig() = configAction {
        repository.lookup("serendipity",useCache=false)
        configMessage.value = "测试查词成功 · 词条格式校验通过（未加入历史）。"
    }
    fun discoverModels() {
        discoveryJob?.cancel()
        val generation=++discoveryGeneration
        modelDiscovery.value=ModelDiscovery(busy=true,message="正在获取模型列表…")
        discoveryJob=viewModelScope.launch {
            try {
                val models=repository.discoverModels()
                if(generation==discoveryGeneration) modelDiscovery.value=ModelDiscovery(models,message="模型发现成功 · ${models.size} 个模型。列表可访问；查词能力尚未验证。")
            } catch(e:CancellationException) {throw e}
            catch(e:ProviderFailure) {if(generation==discoveryGeneration) modelDiscovery.value=ModelDiscovery(message=(if(e.kind==dev.dicta.prototype.provider.FailureKind.AUTH) "模型列表认证失败。" else "模型发现未完成。")+e.userMessage+" 可手动输入 Model ID。",failureKind=e.kind)}
            catch(_:Exception) {if(generation==discoveryGeneration) modelDiscovery.value=ModelDiscovery(message="模型发现未完成，请手动输入 Model ID 或刷新。")}
        }
    }
    fun cancelDiscovery() {++discoveryGeneration;discoveryJob?.cancel();modelDiscovery.value=ModelDiscovery()}
    fun cancelConfig() {
        if (!configBusy.value) return
        ++configGeneration
        configJob?.cancel()
        configBusy.value = false
        configMessage.value = "已取消操作。"
    }
    private fun configAction(block: suspend () -> Unit) {
        configJob?.cancel()
        val generation = ++configGeneration
        configBusy.value = true
        configMessage.value = ""
        configJob = viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: ProviderFailure) { configMessage.value = e.userMessage }
            catch (_: Exception) { configMessage.value = "操作未完成，请检查配置后重试。" }
            finally { if (generation == configGeneration) configBusy.value = false }
        }
    }
    fun lookup(raw:String, origin:String="home") {openJob?.cancel();source=origin;session.lookup(raw)}
    fun retry() {openJob?.cancel();session.retry()}
    fun cancel() {openJob?.cancel();session.cancel()}
    fun closeDetail() {openJob?.cancel();session.close()}
    fun restoredWithoutRequest(query:String="")=session.restoredWithoutRequest(query)
    fun openSnapshot(id:String) {
        openJob?.cancel();session.loadingSnapshot()
        openJob=viewModelScope.launch {
            try { val entry=repository.find(id);ensureActive();if(entry!=null) session.open(entry) else session.restoredWithoutRequest() }
            catch(e:CancellationException) {throw e}
            catch(_:Exception) {session.restoredWithoutRequest()}
        }
    }
    fun bookmarks(ids:List<String>,saved:Boolean)=action {repository.setBookmarks(ids,saved)}
    fun deleteHistory(ids:List<String>)=action {repository.deleteHistory(ids)}
    private fun action(block:suspend ()->Unit) {viewModelScope.launch {
        try {block();actionMessage.value=""} catch(_:Exception) {actionMessage.value="操作未保存，请重试；原有数据已保留。"}
    }}
    fun daily(refresh:Boolean=false) {viewModelScope.launch {repository.ensureDaily(refresh)}}
    fun saveProfile(draft:ProfileDraft,id:String?=null)=profileAction {repository.saveProfile(draft,id);"Profile 已保存并启用。"}
    fun activateProfile(choice:ProfileChoice)=profileAction {repository.activate(choice);"Profile 已切换。"}
    fun restoreDefault()=activateProfile(repository.builtin)
    suspend fun importValidatedProfile(draft:ProfileDraft) {repository.saveProfile(draft)}
    fun importProfile(raw:String)=profileAction {repository.saveProfile(ProfileDraft.import(raw));"Profile 已导入并启用。"}
    fun profileAction(block:suspend ()->String) {
        if(profileBusy.value) return
        profileBusy.value=true;profileMessage.value=""
        viewModelScope.launch {
            try {profileMessage.value=withContext(Dispatchers.IO) {block()}}
            catch(e:Exception) {profileMessage.value=dev.dicta.prototype.profile.profileMessage(e)}
            finally {profileBusy.value=false}
        }
    }
}
