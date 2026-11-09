package dev.dicta.prototype.query

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dicta.prototype.data.DictaRepository
import dev.dicta.prototype.profile.*
import dev.dicta.prototype.data.ProfileChoice
import dev.dicta.prototype.provider.ProviderConfig
import dev.dicta.prototype.provider.ProviderFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

// Deliberately excludes plaintext credentials from observable/UI state.
data class ConfigSummary(val endpoint: String = "https://api.deepseek.com", val model: String = "deepseek-flash", val hasKey: Boolean = false)


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
    init { refreshConfig() }
    private fun refreshConfig() {
        viewModelScope.launch {
            try {
                val config = withContext(Dispatchers.IO) { repository.credentials.load() }
                configuration.value = ConfigSummary(config.endpoint, config.model, config.apiKey.isNotBlank())
            } catch (e: ProviderFailure) { configMessage.value = e.userMessage }
        }
    }
    fun saveConfig(endpoint: String, model: String, replacement: String) = configAction {
        val old = try { repository.credentials.load() } catch (_: ProviderFailure) { ProviderConfig() }
        val next = ProviderConfig(endpoint, model, replacement.ifBlank { old.apiKey }).validated()
        repository.credentials.save(next)
        configuration.value = ConfigSummary(next.endpoint, next.model, next.apiKey.isNotBlank())
        configMessage.value = "配置已安全保存。"
    }
    fun clearConfig() = configAction {
        repository.credentials.clear()
        configuration.value = ConfigSummary()
        configMessage.value = "配置与密钥已清除。"
    }
    fun testConfig() = configAction {
        repository.lookup("serendipity",useCache=false)
        configMessage.value = "连接成功，词条格式校验通过。"
    }
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
    fun restoredWithoutRequest(query:String="")=session.restoredWithoutRequest(query)
    fun openSnapshot(id:String) {
        openJob?.cancel();session.cancel()
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
