package dev.dicta.prototype.query

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dicta.prototype.data.DictaRepository
import dev.dicta.prototype.provider.ProviderConfig
import dev.dicta.prototype.provider.ProviderFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

// Deliberately excludes plaintext credentials from observable/UI state.
data class ConfigSummary(val endpoint: String = "https://api.deepseek.com", val model: String = "deepseek-flash", val hasKey: Boolean = false)


class LookupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DictaRepository.get(application)
    private val session = LookupSession(viewModelScope, repository::lookup, repository::remember)
    val state = session.state
    val history = repository.history
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
        repository.lookup("serendipity")
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
    fun lookup(raw: String) = session.lookup(raw)
    fun retry() = session.retry()
    fun cancel() = session.cancel()
    fun restoredWithoutRequest(query: String = "") = session.restoredWithoutRequest(query)
    fun openSnapshot(id: String): Boolean {
        val snapshot = repository.find(id) ?: return false
        session.open(snapshot)
        return true
    }
}
