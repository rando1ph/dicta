package dev.dicta.prototype.query

import dev.dicta.prototype.data.EntrySnapshot
import dev.dicta.prototype.profile.ProfileFailure
import dev.dicta.prototype.provider.ProviderFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LookupState {
    data object Idle : LookupState
    data class Loading(val query: String) : LookupState
    data class Success(val entry: EntrySnapshot) : LookupState
    data class Error(val message: String, val query: String) : LookupState
    data object Cancelled : LookupState
}

/** Latest-request-wins coordinator shared by both Activity view models. */
class LookupSession(
    private val scope: CoroutineScope,
    private val resolve: suspend (String) -> EntrySnapshot,
    private val remember: (EntrySnapshot) -> Unit,
) {
    private val mutableState = MutableStateFlow<LookupState>(LookupState.Idle)
    val state: StateFlow<LookupState> = mutableState.asStateFlow()
    private var job: Job? = null
    private var generation = 0L
    private var lastQuery = ""

    fun lookup(raw: String) {
        job?.cancel()
        val current = ++generation
        val query = try { normalizeQuery(raw) } catch (_: InvalidQuery) {
            lastQuery = ""
            mutableState.value = LookupState.Error("请输入词或短语，最多 100 个字符。", "")
            return
        }
        lastQuery = query
        mutableState.value = LookupState.Loading(query)
        job = scope.launch {
            try {
                val entry = resolve(query)
                if (generation == current) {
                    remember(entry)
                    mutableState.value = LookupState.Success(entry)
                }
            } catch (_: TimeoutCancellationException) {
                if (generation == current) mutableState.value = LookupState.Error("演示查询超时，请重试。", query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: ProviderFailure) {
                if (generation == current) mutableState.value = LookupState.Error(failure.userMessage, query)
            } catch (_: ProfileFailure) {
                if (generation == current) mutableState.value = LookupState.Error("词条或展示配置格式不符，无法显示。", query)
            } catch (_: Exception) {
                if (generation == current) mutableState.value = LookupState.Error("演示数据暂不可用，请重试。", query)
            }
        }
    }

    fun retry() { if (lastQuery.isNotBlank()) lookup(lastQuery) }
    fun cancel() {
        ++generation
        job?.cancel()
        mutableState.value = LookupState.Cancelled
    }
    fun open(snapshot: EntrySnapshot) {
        cancel()
        lastQuery = snapshot.query
        mutableState.value = LookupState.Success(snapshot)
    }
    fun restoredWithoutRequest(query: String = "") {
        cancel()
        lastQuery = query
        mutableState.value = LookupState.Error("会话已恢复，请重新查询。", query)
    }
}
