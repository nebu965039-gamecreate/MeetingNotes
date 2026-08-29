package com.meetingnotes.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.FolderEntity
import com.meetingnotes.data.local.MeetingEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClientDetailViewModel(
    private val repository: MeetingRepository,
    val clientId: Long
) : ViewModel() {

    val client: StateFlow<ClientEntity?> = repository.observeClient(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val meetings: StateFlow<List<MeetingEntity>> = repository.observeMeetings(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> = repository.observeFolders(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun renameClient(name: String) {
        viewModelScope.launch { repository.renameClient(clientId, name) }
    }

    fun deleteClient(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteClient(clientId)
            onDeleted()
        }
    }

    fun addFolder(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.addFolder(clientId, trimmed) }
    }

    fun renameFolder(folderId: Long, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renameFolder(folderId, trimmed) }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch { repository.deleteFolder(folderId) }
    }

    fun renameMeeting(meetingId: Long, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repository.renameMeeting(meetingId, trimmed) }
    }

    fun moveMeetingToFolder(meetingId: Long, folderId: Long?) {
        viewModelScope.launch { repository.moveMeetingToFolder(meetingId, folderId) }
    }

    fun deleteMeeting(meetingId: Long) {
        viewModelScope.launch { repository.deleteMeeting(meetingId) }
    }

    companion object {
        fun factory(repository: MeetingRepository, clientId: Long) = viewModelFactory {
            initializer { ClientDetailViewModel(repository, clientId) }
        }
    }
}
