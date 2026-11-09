package dev.dicta.prototype.query

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dicta.prototype.data.DictaRepository

class LookupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = DictaRepository.get(application)
    private val session = LookupSession(viewModelScope, repository::lookup, repository::remember)
    val state = session.state
    val history = repository.history
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
