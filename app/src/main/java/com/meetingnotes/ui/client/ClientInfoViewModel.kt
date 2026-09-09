package com.meetingnotes.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.ClientContactEntity
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.model.DealPhase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** クライアント情報の閲覧 + 編集(名前/連絡先/備考/担当者)。 */
class ClientInfoViewModel(
    private val repository: MeetingRepository,
    val clientId: Long
) : ViewModel() {

    val client: StateFlow<ClientEntity?> = repository.observeClient(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val contacts: StateFlow<List<ClientContactEntity>> = repository.observeClientContacts(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 現在のステータス = 最新の商談の実効フェーズ(商談が無ければ null)。 */
    val latestPhase: StateFlow<DealPhase?> = repository.observeMeetings(clientId)
        .map { list ->
            list.maxByOrNull { it.recordedAt }
                ?.let { DealPhase.fromWire(it.phaseOverride ?: it.dealPhase) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val groups = repository.observeClientGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val projects = repository.observeClientProjects(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveInfo(name: String, email: String?, phone: String?, memo: String?, groupId: Long?) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.updateClientInfo(clientId, name, email, phone, memo)
            repository.moveClientToGroup(clientId, groupId)
        }
    }

    fun addContact(name: String, note: String?, email: String?, phone: String?) {
        viewModelScope.launch { repository.addClientContact(clientId, name, note, email, phone) }
    }

    fun updateContact(id: Long, name: String, note: String?, email: String?, phone: String?) {
        viewModelScope.launch { repository.updateClientContact(id, name, note, email, phone) }
    }

    fun deleteContact(id: Long) {
        viewModelScope.launch { repository.deleteClientContact(id) }
    }

    companion object {
        fun factory(repository: MeetingRepository, clientId: Long) = viewModelFactory {
            initializer { ClientInfoViewModel(repository, clientId) }
        }
    }
}
