package com.meetingnotes.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.common.ConfirmDialog
import com.meetingnotes.ui.common.FolderTab
import com.meetingnotes.ui.common.FolderTabRow
import com.meetingnotes.ui.common.LabeledDropdownField
import com.meetingnotes.ui.common.NextMeetingDateTimeDialog
import com.meetingnotes.ui.common.TabTopBar
import com.meetingnotes.ui.common.TodoDueFilter
import com.meetingnotes.ui.common.TodoNotifyButton
import com.meetingnotes.ui.common.TodoRow
import com.meetingnotes.ui.common.TodoRowData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowupListScreen(
    repository: MeetingRepository,
    onOpenClient: (Long) -> Unit,
    onOpenMeeting: (Long) -> Unit = {},
    onHome: () -> Unit = {},
    /** ホームの「期限切れ」「3日以内のToDo」から「すべて表示」で渡された絞り込み(あれば適用)。 */
    initialFilter: TodoDueFilter? = null
) {
    val viewModel: FollowupListViewModel = viewModel(factory = FollowupListViewModel.factory(repository))
    val open by viewModel.openTodos.collectAsState()
    val activeCount by viewModel.activeTodoCount.collectAsState()
    val dueFilter by viewModel.dueFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val done by viewModel.doneTodos.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(initialFilter) {
        if (initialFilter != null) {
            viewModel.setDueFilter(initialFilter)
            tab = 0
        }
    }

    Scaffold(
        topBar = {
            TabTopBar(icon = Icons.Filled.CheckCircle, title = "ToDo", onHome = onHome)
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(com.meetingnotes.ui.common.FolderTabDefaults.sheetColor)
        ) {
            // 2026-09-16、スヌーズ廃止に伴い「スヌーズ」タブを削除(ToDo / 完了 の2タブに)。
            // 通知予約中かどうかは各行の🔔ボタン・チップで分かるため、隠れた一覧を別途持つ必要が無くなった。
            FolderTabRow(
                tabs = listOf(
                    FolderTab("ToDo", activeCount),
                    FolderTab("完了", done.size)
                ),
                selectedIndex = tab,
                onSelect = { tab = it }
            )

            fun openItem(item: TodoScreenItem) {
                val mid = item.todo.meetingId
                if (mid != null) onOpenMeeting(mid) else onOpenClient(item.clientId)
            }

            when (tab) {
                0 -> Column(modifier = Modifier.fillMaxSize()) {
                    TodoDueFilterRow(selected = dueFilter, onSelect = { viewModel.setDueFilter(it) })
                    TodoListToolRow(
                        sortOrder = sortOrder,
                        onSortChange = { viewModel.setSortOrder(it) },
                        onAddClick = { showAdd = true }
                    )
                    TodoItemList(
                        items = open,
                        emptyText = "現在ToDoはありません",
                        onOpen = ::openItem,
                        onToggle = { viewModel.complete(it) },
                        // 通知予約チップ(🔔 M/d H:mm)はクライアント名も同じ行にあるとレイアウトが
                        // 崩れるため非表示に(2026-09-15)。予約有無は trailing のベルボタンの色で分かる。
                        showNotifyChip = false,
                        trailing = { item ->
                            TodoNotifyButton(
                                todoId = item.id,
                                notifyAt = item.todo.notifyAt,
                                onNotifyAtChange = { atMillis -> viewModel.setNotifyAt(item.id, atMillis) }
                            )
                        }
                    )
                }
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    if (done.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showDeleteAllConfirm = true }) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                                Text("すべて削除")
                            }
                        }
                    }
                    TodoItemList(
                        items = done,
                        emptyText = "完了したToDoはまだありません",
                        done = true,
                        onOpen = ::openItem,
                        onToggle = { viewModel.reopen(it) },
                        trailing = null
                    )
                }
            }
        }
    }

    if (showDeleteAllConfirm) {
        ConfirmDialog(
            title = "完了済みToDoをすべて削除",
            text = "完了済みのToDo(${done.size}件)をすべて削除します。元に戻せません。",
            confirmLabel = "削除",
            onDismiss = { showDeleteAllConfirm = false },
            onConfirm = {
                viewModel.deleteAllDone()
                showDeleteAllConfirm = false
            }
        )
    }
    if (showAdd) {
        GlobalTodoAddDialog(
            clients = clients,
            onDismiss = { showAdd = false },
            onConfirm = { clientId, task, dueDate ->
                viewModel.addManualTodo(clientId, task, dueDate)
                showAdd = false
            }
        )
    }
}

/** ToDo タブ直下のツール行(並び替え + 追加)。クライアント詳細の ToDo タブと同じ配置・見た目。 */
@Composable
private fun TodoListToolRow(
    sortOrder: TodoSortOrder,
    onSortChange: (TodoSortOrder) -> Unit,
    onAddClick: () -> Unit
) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            TextButton(onClick = { sortMenu = true }) {
                Icon(Icons.Filled.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(sortOrder.label)
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                TodoSortOrder.entries.forEach { o ->
                    DropdownMenuItem(
                        text = { Text(o.label) },
                        onClick = { onSortChange(o); sortMenu = false }
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAddClick) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("ToDoを追加")
        }
    }
}

/** ToDo 画面から手動 ToDo を追加するフォーム。クライアント詳細の `TodoFormDialog` にクライアント選択を足したもの。 */
@Composable
private fun GlobalTodoAddDialog(
    clients: List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onConfirm: (clientId: Long, task: String, dueDate: String?) -> Unit
) {
    var clientId by remember { mutableStateOf(clients.firstOrNull()?.first) }
    var task by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ToDoを追加") },
        text = {
            if (clients.isEmpty()) {
                Text("クライアントがいません。先にクライアントを追加してください。")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledDropdownField(
                        label = "クライアント",
                        options = clients,
                        selected = clientId ?: clients.first().first,
                        onSelect = { clientId = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = task,
                        onValueChange = { task = it },
                        label = { Text("内容") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showDatePicker = true }) {
                            Text(due?.let { "期限: ${it.monthValue}月${it.dayOfMonth}日" } ?: "期限日を設定")
                        }
                        if (due != null) {
                            TextButton(onClick = { due = null }) { Text("クリア") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { clientId?.let { onConfirm(it, task, due?.toString()) } },
                enabled = clients.isNotEmpty() && clientId != null && task.isNotBlank()
            ) { Text("追加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )

    if (showDatePicker) {
        NextMeetingDateTimeDialog(
            initial = (due ?: java.time.LocalDate.now()).atStartOfDay(),
            initialHasTime = false,
            onDismiss = { showDatePicker = false },
            onConfirm = { dt, _ -> due = dt.toLocalDate(); showDatePicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoDueFilterRow(selected: TodoDueFilter, onSelect: (TodoDueFilter) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TodoDueFilter.entries.forEachIndexed { index, filter ->
            SegmentedButton(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                shape = SegmentedButtonDefaults.itemShape(index, TodoDueFilter.entries.size)
            ) { Text(filter.label) }
        }
    }
}

@Composable
private fun TodoItemList(
    items: List<TodoScreenItem>,
    emptyText: String,
    onOpen: (TodoScreenItem) -> Unit,
    onToggle: (Long) -> Unit,
    done: Boolean = false,
    showNotifyChip: Boolean = true,
    trailing: (@Composable (TodoScreenItem) -> Unit)?
) {
    if (items.isEmpty()) {
        EmptyMessage(emptyText)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { item ->
            TodoRow(
                data = TodoRowData(
                    id = item.id,
                    task = item.todo.task,
                    dueDate = item.todo.dueDate,
                    deadlineText = item.todo.deadline,
                    done = done,
                    notifyAt = item.todo.notifyAt,
                    clientName = item.clientName
                ),
                onToggle = { onToggle(item.id) },
                onClick = { onOpen(item) },
                showNotifyChip = showNotifyChip,
                trailing = trailing?.let { { it(item) } }
            )
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
