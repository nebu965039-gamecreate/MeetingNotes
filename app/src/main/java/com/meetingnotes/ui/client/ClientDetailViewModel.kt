package com.meetingnotes.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.FolderEntity
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.data.model.DealPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClientDetailViewModel(
    private val repository: MeetingRepository,
    val clientId: Long
) : ViewModel() {

    val client: StateFlow<ClientEntity?> = repository.observeClient(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val meetings: StateFlow<List<MeetingEntity>> = repository.observeMeetings(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val todos: StateFlow<List<TodoEntity>> = repository.observeTodosByClient(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> = repository.observeFolders(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sortOrder = MutableStateFlow(MeetingSortOrder.NEWEST)
    val sortOrder: StateFlow<MeetingSortOrder> = _sortOrder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** 並び替え済みの商談一覧(フォルダ表示・非検索時に使う)。 */
    val sortedMeetings: StateFlow<List<MeetingEntity>> =
        combine(meetings, _sortOrder) { list, order -> MeetingArchiveSearch.sort(list, order) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 検索結果(クエリが空なら空リスト)。 */
    val searchResults: StateFlow<List<MeetingSearchResult>> =
        combine(meetings, todos, _searchQuery, _sortOrder) { list, todoList, query, order ->
            MeetingArchiveSearch.search(list, todoList.groupBy { it.meetingId }, query, order)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortOrder(order: MeetingSortOrder) {
        _sortOrder.value = order
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

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

    fun setMeetingPhase(meetingId: Long, phase: DealPhase) {
        viewModelScope.launch { repository.setMeetingPhaseOverride(meetingId, phase) }
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
