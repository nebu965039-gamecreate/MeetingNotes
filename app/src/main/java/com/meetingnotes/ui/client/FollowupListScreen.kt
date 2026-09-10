package com.meetingnotes.ui.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.common.FolderTab
import com.meetingnotes.ui.common.FolderTabRow
import com.meetingnotes.ui.common.SnoozeMenu
import com.meetingnotes.ui.common.TabTopBar
import com.meetingnotes.ui.common.TodoRow
import com.meetingnotes.ui.common.TodoRowData
import com.meetingnotes.ui.common.todoIsSnoozed

@Composable
fun FollowupListScreen(
    repository: MeetingRepository,
    onOpenClient: (Long) -> Unit,
    onOpenMeeting: (Long) -> Unit = {},
    onHome: () -> Unit = {}
) {
    val viewModel: FollowupListViewModel = viewModel(factory = FollowupListViewModel.factory(repository))
    val open by viewModel.openTodos.collectAsState()
    val snoozed by viewModel.snoozedTodos.collectAsState()
    val done by viewModel.doneTodos.collectAsState()

    var tab by remember { mutableIntStateOf(0) }

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
            // ToDo タブの件数はスヌーズ中を除いた「今やるべき」数に合わせる(下部ナビのバッジと一致)。
            val activeCount = open.count { !todoIsSnoozed(it.todo.snoozedUntil) }
            FolderTabRow(
                tabs = listOf(
                    FolderTab("ToDo", activeCount),
                    FolderTab("スヌーズ", snoozed.size),
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
                0 -> TodoItemList(
                    items = open,
                    emptyText = "現在ToDoはありません",
                    onOpen = ::openItem,
                    onToggle = { viewModel.complete(it) },
                    trailing = { item ->
                        SnoozeMenu(
                            snoozed = todoIsSnoozed(item.todo.snoozedUntil),
                            onSnooze = { until -> viewModel.snooze(item.id, until) },
                            onClearSnooze = { viewModel.unsnooze(item.id) }
                        )
                    }
                )
                1 -> SnoozedList(
                    items = snoozed,
                    onOpen = ::openItem,
                    onUnsnooze = { viewModel.unsnooze(it) }
                )
                else -> TodoItemList(
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

@Composable
private fun TodoItemList(
    items: List<TodoScreenItem>,
    emptyText: String,
    onOpen: (TodoScreenItem) -> Unit,
    onToggle: (Long) -> Unit,
    done: Boolean = false,
    trailing: (@Composable (TodoScreenItem) -> Unit)?
) {
    if (items.isEmpty()) {
        EmptyMessage(emptyText)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(items, key = { it.id }) { item ->
            TodoRow(
                data = TodoRowData(
                    id = item.id,
                    task = item.todo.task,
                    dueDate = item.todo.dueDate,
                    deadlineText = item.todo.deadline,
                    done = done,
                    snoozedUntil = item.todo.snoozedUntil,
                    clientName = item.clientName
                ),
                onToggle = { onToggle(item.id) },
                onClick = { onOpen(item) },
                trailing = trailing?.let { { it(item) } }
            )
        }
    }
}

@Composable
private fun SnoozedList(
    items: List<TodoScreenItem>,
    onOpen: (TodoScreenItem) -> Unit,
    onUnsnooze: (Long) -> Unit
) {
    if (items.isEmpty()) {
        EmptyMessage("スヌーズ中のToDoはありません")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(items, key = { it.id }) { item ->
            TodoRow(
                data = TodoRowData(
                    id = item.id,
                    task = item.todo.task,
                    dueDate = item.todo.dueDate,
                    deadlineText = item.todo.deadline,
                    snoozedUntil = item.todo.snoozedUntil,
                    clientName = item.clientName
                ),
                onToggle = {},
                onClick = { onOpen(item) },
                showCheckbox = false,
                trailing = {
                    TextButton(onClick = { onUnsnooze(item.id) }) {
                        Text("解除", style = MaterialTheme.typography.labelLarge)
                    }
                }
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
