package com.meetingnotes.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.ui.common.TodoDueFilter
import com.meetingnotes.ui.common.matchesDueFilter
import com.meetingnotes.ui.common.todoIsSnoozed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** ToDo 画面(下部ナビ)の1行。全クライアント横断の個別 ToDo。 */
data class TodoScreenItem(
    val todo: TodoEntity,
    val clientName: String
) {
    val id get() = todo.id
    val clientId get() = todo.clientId
}

/**
 * ToDo 画面(下部ナビ)の VM。クライアント詳細の ToDo タブと同じ「個別 ToDo」粒度。
 * - ToDo タブ  = 未完了(スヌーズ中も残す。末尾に寄せる)
 * - スヌーズタブ = スヌーズ中の未完了だけ(確認用)
 * - 完了タブ    = 完了済み(新しい順)
 */
class FollowupListViewModel(private val repository: MeetingRepository) : ViewModel() {

    private val source = combine(
        repository.observeAllTodos(),
        repository.observeClients()
    ) { todos, clients ->
        val nameById = clients.associate { it.id to it.name }
        todos.map { TodoScreenItem(it, nameById[it.clientId] ?: "(不明なクライアント)") }
    }

    private val openTodosAll = source
        .map { list -> list.filter { !it.todo.isDone } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _dueFilter = MutableStateFlow(TodoDueFilter.ALL)
    val dueFilter: StateFlow<TodoDueFilter> = _dueFilter

    /** ToDo タブの並び替え。クライアント詳細の ToDo タブと同じ `TodoSortOrder` を再利用(2026-09-15)。 */
    private val _sortOrder = MutableStateFlow(TodoSortOrder.DUE_DATE)
    val sortOrder: StateFlow<TodoSortOrder> = _sortOrder

    /** 未完了 ToDo。[dueFilter] で絞り込み、[sortOrder] で並び替え。スヌーズ中は常に最後尾へ。 */
    val openTodos: StateFlow<List<TodoScreenItem>> =
        combine(openTodosAll, _dueFilter, _sortOrder) { list, filter, sort ->
            val filtered = list.filter { matchesDueFilter(it.todo.dueDate, filter) }
            val snoozedLast = compareBy<TodoScreenItem> { todoIsSnoozed(it.todo.snoozedUntil) }
            when (sort) {
                TodoSortOrder.DUE_DATE ->
                    filtered.sortedWith(
                        snoozedLast.thenBy { it.todo.dueDate == null }
                            .thenBy { it.todo.dueDate ?: "" }
                            .thenByDescending { it.todo.id }
                    )
                TodoSortOrder.CREATED ->
                    filtered.sortedWith(snoozedLast.thenByDescending { it.todo.id })
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortOrder(order: TodoSortOrder) {
        _sortOrder.value = order
    }

    /** 手動 ToDo 追加のクライアント選択肢(名前順)。 */
    val clients: StateFlow<List<Pair<Long, String>>> = repository.observeClients()
        .map { list -> list.sortedBy { it.name }.map { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** ToDo 画面から手動 ToDo を追加する(クライアントを選んで登録)。 */
    fun addManualTodo(clientId: Long, task: String, dueDate: String?) {
        viewModelScope.launch { repository.addManualTodo(clientId, task, dueDate) }
    }

    /** タブの件数バッジ用。フィルタの影響を受けない「今やるべき」総数(スヌーズ中除く)。下部ナビのバッジと一致。 */
    val activeTodoCount: StateFlow<Int> = openTodosAll
        .map { list -> list.count { !todoIsSnoozed(it.todo.snoozedUntil) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setDueFilter(filter: TodoDueFilter) {
        _dueFilter.value = filter
    }

    /** 現在スヌーズ中の未完了 ToDo。再表示が近い順。 */
    val snoozedTodos: StateFlow<List<TodoScreenItem>> = source
        .map { list ->
            list.filter { !it.todo.isDone && todoIsSnoozed(it.todo.snoozedUntil) }
                .sortedBy { it.todo.snoozedUntil }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 完了済み ToDo。新しい順。 */
    val doneTodos: StateFlow<List<TodoScreenItem>> = source
        .map { list -> list.filter { it.todo.isDone }.sortedByDescending { it.todo.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun complete(todoId: Long) {
        viewModelScope.launch { repository.setTodoDone(todoId, true) }
    }

    fun reopen(todoId: Long) {
        viewModelScope.launch { repository.setTodoDone(todoId, false) }
    }

    fun snooze(todoId: Long, untilMillis: Long) {
        viewModelScope.launch { repository.setTodoSnooze(todoId, untilMillis) }
    }

    fun unsnooze(todoId: Long) {
        viewModelScope.launch { repository.setTodoSnooze(todoId, null) }
    }

    /** 完了済み ToDo をすべて削除する(確認ダイアログを経て呼ばれる想定)。 */
    fun deleteAllDone() {
        viewModelScope.launch { repository.deleteAllDoneTodos() }
    }

    companion object {
        fun factory(repository: MeetingRepository) = viewModelFactory {
            initializer { FollowupListViewModel(repository) }
        }
    }
}
