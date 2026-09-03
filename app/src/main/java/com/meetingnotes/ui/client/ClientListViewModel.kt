package com.meetingnotes.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientGroupEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClientListViewModel(private val repository: MeetingRepository) : ViewModel() {

    val clients: StateFlow<List<ClientEntity>> = repository.observeClients()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<ClientGroupEntity>> = repository.observeClientGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** F1: 次アクション未定で放置されている案件(フォローボード)。 */
    val followups: StateFlow<List<FollowupItem>> =
        combine(clients, repository.observeLatestMeetingPerClient()) { clientList, latest ->
            FollowupRules.compute(clientList, latest)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addClient(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addClient(trimmed) }
    }

    fun renameClient(clientId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renameClient(clientId, trimmed) }
    }

    fun deleteClient(clientId: Long) {
        viewModelScope.launch { repository.deleteClient(clientId) }
    }

    fun addGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addClientGroup(trimmed) }
    }

    fun renameGroup(groupId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renameClientGroup(groupId, trimmed) }
    }

    fun deleteGroup(groupId: Long) {
        viewModelScope.launch { repository.deleteClientGroup(groupId) }
    }

    fun moveClientToGroup(clientId: Long, groupId: Long?) {
        viewModelScope.launch { repository.moveClientToGroup(clientId, groupId) }
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { ClientListViewModel(repository) }
        }
    }
}
