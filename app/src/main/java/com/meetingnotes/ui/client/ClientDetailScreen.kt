package com.meetingnotes.ui.client

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.FolderEntity
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.ui.common.ConfirmDialog
import com.meetingnotes.ui.common.TextInputDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    repository: MeetingRepository,
    clientId: Long,
    onStartRecording: (Long) -> Unit,
    onMeetingSelected: (Long) -> Unit,
    onBack: () -> Unit,
    onClientDeleted: () -> Unit
) {
    val viewModel: ClientDetailViewModel = viewModel(
        factory = ClientDetailViewModel.factory(repository, clientId)
    )
    val client by viewModel.client.collectAsState()
    val meetings by viewModel.meetings.collectAsState()
    val folders by viewModel.folders.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameClientDialog by remember { mutableStateOf(false) }
    var showDeleteClientDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    // フォルダごとの展開状態。未登録(=このMapに無い)場合はデフォルトで未展開。
    val expandedFolders = remember { mutableStateMapOf<Long, Boolean>() }
    var meetingToRename by remember { mutableStateOf<MeetingEntity?>(null) }
    var meetingToMove by remember { mutableStateOf<MeetingEntity?>(null) }
    var meetingToDelete by remember { mutableStateOf<MeetingEntity?>(null) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(client?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("クライアント名を変更") },
                            onClick = {
                                menuExpanded = false
                                showRenameClientDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("クライアントを削除") },
                            onClick = {
                                menuExpanded = false
                                showDeleteClientDialog = true
                            }
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Button(
                    onClick = { onStartRecording(clientId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    Icon(Icons.Filled.Mic, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "録音開始")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "アーカイブ", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { showAddFolderDialog = true }) {
                        Text("+ フォルダ作成")
                    }
                }
            }

            if (meetings.isEmpty() && folders.isEmpty()) {
                item { Text("まだ商談の記録がありません。") }
            } else if (folders.isEmpty()) {
                items(meetings, key = { it.id }) { meeting ->
                    MeetingRow(
                        meeting = meeting,
                        onClick = { onMeetingSelected(meeting.id) },
                        onRename = { meetingToRename = meeting },
                        onMove = { meetingToMove = meeting },
                        onDelete = { meetingToDelete = meeting }
                    )
                }
            } else {
                folders.forEach { folder ->
                    val folderMeetings = meetings.filter { it.folderId == folder.id }
                    val isExpanded = expandedFolders[folder.id] == true
                    item {
                        FolderHeaderRow(
                            folder = folder,
                            isExpanded = isExpanded,
                            onToggleExpand = { expandedFolders[folder.id] = !isExpanded },
                            onRename = { folderToRename = folder },
                            onDelete = { folderToDelete = folder }
                        )
                    }
                    if (isExpanded) {
                        if (folderMeetings.isEmpty()) {
                            item { Text("(このフォルダには商談がありません)", style = MaterialTheme.typography.bodySmall) }
                        } else {
                            items(folderMeetings, key = { it.id }) { meeting ->
                                MeetingRow(
                                    meeting = meeting,
                                    onClick = { onMeetingSelected(meeting.id) },
                                    onRename = { meetingToRename = meeting },
                                    onMove = { meetingToMove = meeting },
                                    onDelete = { meetingToDelete = meeting }
                                )
                            }
                        }
                    }
                }

                val unclassified = meetings.filter { it.folderId == null }
                if (unclassified.isNotEmpty()) {
                    item {
                        Text(
                            text = "未分類",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(unclassified, key = { it.id }) { meeting ->
                        MeetingRow(
                            meeting = meeting,
                            onClick = { onMeetingSelected(meeting.id) },
                            onRename = { meetingToRename = meeting },
                            onMove = { meetingToMove = meeting },
                            onDelete = { meetingToDelete = meeting }
                        )
                    }
                }
            }
        }
    }

    if (showRenameClientDialog) {
        TextInputDialog(
            title = "クライアント名を変更",
            label = "クライアント名",
            initialValue = client?.name.orEmpty(),
            confirmLabel = "変更",
            onDismiss = { showRenameClientDialog = false },
            onConfirm = { name ->
                viewModel.renameClient(name)
                showRenameClientDialog = false
            }
        )
    }

    if (showDeleteClientDialog) {
        ConfirmDialog(
            title = "クライアントを削除",
            text = "「${client?.name}」を削除します。関連する商談・ToDoもすべて削除され、元に戻せません。",
            onDismiss = { showDeleteClientDialog = false },
            onConfirm = {
                showDeleteClientDialog = false
                viewModel.deleteClient(onClientDeleted)
            }
        )
    }

    if (showAddFolderDialog) {
        TextInputDialog(
            title = "フォルダを作成",
            label = "フォルダ名",
            confirmLabel = "作成",
            onDismiss = { showAddFolderDialog = false },
            onConfirm = { name ->
                viewModel.addFolder(name)
                showAddFolderDialog = false
            }
        )
    }

    meetingToRename?.let { meeting ->
        TextInputDialog(
            title = "商談タイトルを変更",
            label = "タイトル",
            initialValue = meeting.title,
            confirmLabel = "変更",
            onDismiss = { meetingToRename = null },
            onConfirm = { title ->
                viewModel.renameMeeting(meeting.id, title)
                meetingToRename = null
            }
        )
    }

    meetingToMove?.let { meeting ->
        FolderPickerDialog(
            folders = folders,
            currentFolderId = meeting.folderId,
            onDismiss = { meetingToMove = null },
            onSelect = { folderId ->
                viewModel.moveMeetingToFolder(meeting.id, folderId)
                meetingToMove = null
            }
        )
    }

    meetingToDelete?.let { meeting ->
        ConfirmDialog(
            title = "商談を削除",
            text = "「${meeting.title}」を削除します。元に戻せません。",
            onDismiss = { meetingToDelete = null },
            onConfirm = {
                viewModel.deleteMeeting(meeting.id)
                meetingToDelete = null
            }
        )
    }

    folderToRename?.let { folder ->
        TextInputDialog(
            title = "フォルダ名を変更",
            label = "フォルダ名",
            initialValue = folder.name,
            confirmLabel = "変更",
            onDismiss = { folderToRename = null },
            onConfirm = { name ->
                viewModel.renameFolder(folder.id, name)
                folderToRename = null
            }
        )
    }

    folderToDelete?.let { folder ->
        ConfirmDialog(
            title = "フォルダを削除",
            text = "「${folder.name}」を削除します。含まれる商談は削除されず、未分類に戻ります。",
            onDismiss = { folderToDelete = null },
            onConfirm = {
                viewModel.deleteFolder(folder.id)
                folderToDelete = null
            }
        )
    }
}

@Composable
private fun FolderHeaderRow(
    folder: FolderEntity,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpand)
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = if (isExpanded) "折りたたむ" else "展開する"
            )
            Text(text = folder.name, style = MaterialTheme.typography.titleSmall)
        }
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "フォルダメニュー")
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("名前を変更") },
                onClick = {
                    menuExpanded = false
                    onRename()
                }
            )
            DropdownMenuItem(
                text = { Text("削除") },
                onClick = {
                    menuExpanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun MeetingRow(
    meeting: MeetingEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                Text(text = meeting.title, style = MaterialTheme.typography.titleMedium)
                val recordedAt = Instant.ofEpochMilli(meeting.recordedAt).atZone(ZoneId.systemDefault())
                Text(text = recordedAt.format(dateFormatter), style = MaterialTheme.typography.bodySmall)
                Text(text = meeting.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }

            Column {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("タイトルを変更") },
                        onClick = { menuExpanded = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("フォルダに移動") },
                        onClick = { menuExpanded = false; onMove() }
                    )
                    DropdownMenuItem(
                        text = { Text("削除") },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderPickerDialog(
    folders: List<FolderEntity>,
    currentFolderId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("フォルダに移動") },
        text = {
            Column {
                FolderOptionRow(
                    label = "未分類",
                    selected = currentFolderId == null,
                    onClick = { onSelect(null) }
                )
                folders.forEach { folder ->
                    FolderOptionRow(
                        label = folder.name,
                        selected = currentFolderId == folder.id,
                        onClick = { onSelect(folder.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun FolderOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
