package com.meetingnotes.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.local.FolderEntity
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.data.model.DealPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    /** プロジェクト絞り込みに関係なく、このクライアントが商談を1件以上持っているか。 */
    val hasAnyMeeting: StateFlow<Boolean> = meetings
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val todos: StateFlow<List<TodoEntity>> = repository.observeTodosByClient(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** このクライアントの未完了 ToDo(期限のあるものを先に、近い順)。 */
    val openTodos: StateFlow<List<TodoEntity>> = todos
        .map { list ->
            list.filter { !it.isDone }
                .sortedWith(compareBy({ it.dueDate == null }, { it.dueDate ?: "" }))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** このクライアントの完了済み ToDo(新しく起票された順の逆 = id 降順)。 */
    val doneTodos: StateFlow<List<TodoEntity>> = todos
        .map { list -> list.filter { it.isDone }.sortedByDescending { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<FolderEntity>> = repository.observeFolders(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** このクライアントの任意プロジェクト一覧。 */
    val projects: StateFlow<List<ClientProjectEntity>> = repository.observeClientProjects(clientId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** アーカイブのプロジェクト絞り込み。null=すべて / [NO_PROJECT]=プロジェクト未設定 / それ以外=プロジェクトID。 */
    private val _projectFilter = MutableStateFlow<Long?>(null)
    val projectFilter: StateFlow<Long?> = _projectFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(MeetingSortOrder.NEWEST)
    val sortOrder: StateFlow<MeetingSortOrder> = _sortOrder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private fun applyProjectFilter(list: List<MeetingEntity>, filter: Long?): List<MeetingEntity> = when (filter) {
        null -> list
        NO_PROJECT -> list.filter { it.projectId == null }
        else -> list.filter { it.projectId == filter }
    }

    /** 並び替え済みの商談一覧(フォルダ表示・非検索時に使う)。プロジェクト絞り込み適用後。 */
    val sortedMeetings: StateFlow<List<MeetingEntity>> =
        combine(meetings, _sortOrder, _projectFilter) { list, order, filter ->
            MeetingArchiveSearch.sort(applyProjectFilter(list, filter), order)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 検索結果(クエリが空なら空リスト)。プロジェクト絞り込み適用後。 */
    val searchResults: StateFlow<List<MeetingSearchResult>> =
        combine(meetings, todos, _searchQuery, _sortOrder, _projectFilter) { list, todoList, query, order, filter ->
            MeetingArchiveSearch.search(
                applyProjectFilter(list, filter), todoList.groupBy { it.meetingId }, query, order
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortOrder(order: MeetingSortOrder) {
        _sortOrder.value = order
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setProjectFilter(filter: Long?) {
        _projectFilter.value = filter
    }

    fun addProject(name: String) {
        viewModelScope.launch { repository.addClientProject(clientId, name) }
    }

    fun renameProject(projectId: Long, name: String) {
        viewModelScope.launch { repository.renameClientProject(projectId, name) }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            repository.deleteClientProject(projectId)
            if (_projectFilter.value == projectId) _projectFilter.value = null
        }
    }

    fun setMeetingProject(meetingId: Long, projectId: Long?) {
        viewModelScope.launch { repository.setMeetingProject(meetingId, projectId) }
    }

    fun renameClient(name: String) {
        viewModelScope.launch { repository.renameClient(clientId, name) }
    }

    fun completeTodo(todoId: Long) {
        viewModelScope.launch { repository.setTodoDone(todoId, true) }
    }

    fun reopenTodo(todoId: Long) {
        viewModelScope.launch { repository.setTodoDone(todoId, false) }
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
        /** プロジェクト絞り込みで「プロジェクト未設定」を表すセンチネル。 */
        const val NO_PROJECT = -1L

        fun factory(repository: MeetingRepository, clientId: Long) = viewModelFactory {
            initializer { ClientDetailViewModel(repository, clientId) }
        }
    }
}
