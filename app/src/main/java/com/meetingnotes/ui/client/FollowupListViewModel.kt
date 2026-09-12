package com.meetingnotes.ui.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.ui.common.TodoDueFilter
import com.meetingnotes.ui.common.matchesDueFilter
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
 * - ToDo タブ = 未完了(すべて。通知予約の有無に関わらず常に表示)
 * - 完了タブ  = 完了済み(新しい順)
 *
 * **2026-09-16、スヌーズ廃止 → 通知予約に置き換え**: 旧「スヌーズ」(指定日時まで一覧・バッジ・
 * リマインドから隠す)は「ただ再表示されるだけで気づきにくい」というフィードバックを受けて廃止。
 * 代わりに ToDo 1件ごとに任意の日時を指定して**端末通知**を送る仕組み(`TodoNotificationScheduler`)
 * に置き換えた。ToDo は通知予約の有無に関わらず常に一覧・件数に含まれる(隠す概念が無くなった)。
 * 旧「スヌーズ」タブは廃止(`FollowupListScreen` 参照)。
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

    /** 未完了 ToDo。[dueFilter] で絞り込み、[sortOrder] で並び替え。 */
    val openTodos: StateFlow<List<TodoScreenItem>> =
        combine(openTodosAll, _dueFilter, _sortOrder) { list, filter, sort ->
            val filtered = list.filter { matchesDueFilter(it.todo.dueDate, filter) }
            when (sort) {
                TodoSortOrder.DUE_DATE ->
                    filtered.sortedWith(
                        compareBy<TodoScreenItem> { it.todo.dueDate == null }
                            .thenBy { it.todo.dueDate ?: "" }
                            .thenByDescending { it.todo.id }
                    )
                TodoSortOrder.CREATED -> filtered.sortedByDescending { it.todo.id }
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

    /** タブの件数バッジ用。フィルタの影響を受けない未完了 ToDo の総数。下部ナビのバッジと一致。 */
    val activeTodoCount: StateFlow<Int> = openTodosAll
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setDueFilter(filter: TodoDueFilter) {
        _dueFilter.value = filter
    }

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

    /** ToDo 1件に通知予約を設定/解除する(DB の更新のみ。WorkManager への登録・解除は呼び出し側の Composable が行う)。 */
    fun setNotifyAt(todoId: Long, atMillis: Long?) {
        viewModelScope.launch { repository.setTodoNotifyAt(todoId, atMillis) }
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
