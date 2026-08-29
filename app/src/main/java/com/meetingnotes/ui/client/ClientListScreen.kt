package com.meetingnotes.ui.client

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientGroupEntity
import com.meetingnotes.ui.common.ConfirmDialog
import com.meetingnotes.ui.common.TextInputDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientListScreen(
    repository: MeetingRepository,
    onClientSelected: (Long) -> Unit
) {
    val viewModel: ClientListViewModel = viewModel(factory = ClientListViewModel.factory(repository))
    val clients by viewModel.clients.collectAsState()
    val groups by viewModel.groups.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var clientToRename by remember { mutableStateOf<ClientEntity?>(null) }
    var clientToDelete by remember { mutableStateOf<ClientEntity?>(null) }
    var clientToMove by remember { mutableStateOf<ClientEntity?>(null) }
    // グループごとの展開状態。未登録(=このMapに無い)場合はデフォルトで未展開。
    val expandedGroups = remember { mutableStateMapOf<Long, Boolean>() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "クライアント一覧",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        bottomBar = { BannerAdView() },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "クライアント追加")
            }
        }
    ) { padding ->
        if (clients.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Text("右下の + からクライアントを追加してください。")
            }
        } else {
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
                        Text(text = "グループ", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = { showAddGroupDialog = true }) {
                            Text("+ グループ作成")
                        }
                    }
                }

                if (groups.isEmpty()) {
                    items(clients, key = { it.id }) { client ->
                        ClientRow(
                            client = client,
                            onClick = { onClientSelected(client.id) },
                            onRename = { clientToRename = client },
                            onMove = { clientToMove = client },
                            onDelete = { clientToDelete = client }
                        )
                    }
                } else {
                    groups.forEach { group ->
                        val groupClients = clients.filter { it.groupId == group.id }
                        val isExpanded = expandedGroups[group.id] == true
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expandedGroups[group.id] = !isExpanded }
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                                    contentDescription = if (isExpanded) "折りたたむ" else "展開する"
                                )
                                Text(text = group.name, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                        if (isExpanded) {
                            if (groupClients.isEmpty()) {
                                item { Text("(このグループにはクライアントがいません)", style = MaterialTheme.typography.bodySmall) }
                            } else {
                                items(groupClients, key = { it.id }) { client ->
                                    ClientRow(
                                        client = client,
                                        onClick = { onClientSelected(client.id) },
                                        onRename = { clientToRename = client },
                                        onMove = { clientToMove = client },
                                        onDelete = { clientToDelete = client }
                                    )
                                }
                            }
                        }
                    }

                    val unclassified = clients.filter { it.groupId == null }
                    if (unclassified.isNotEmpty()) {
                        item {
                            Text(
                                text = "未分類",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        items(unclassified, key = { it.id }) { client ->
                            ClientRow(
                                client = client,
                                onClick = { onClientSelected(client.id) },
                                onRename = { clientToRename = client },
                                onMove = { clientToMove = client },
                                onDelete = { clientToDelete = client }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        TextInputDialog(
            title = "クライアントを追加",
            label = "クライアント名",
            confirmLabel = "追加",
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                viewModel.addClient(name)
                showAddDialog = false
            }
        )
    }

    if (showAddGroupDialog) {
        TextInputDialog(
            title = "グループを作成",
            label = "グループ名",
            confirmLabel = "作成",
            onDismiss = { showAddGroupDialog = false },
            onConfirm = { name ->
                viewModel.addGroup(name)
                showAddGroupDialog = false
            }
        )
    }

    clientToRename?.let { client ->
        TextInputDialog(
            title = "クライアント名を変更",
            label = "クライアント名",
            initialValue = client.name,
            confirmLabel = "変更",
            onDismiss = { clientToRename = null },
            onConfirm = { name ->
                viewModel.renameClient(client.id, name)
                clientToRename = null
            }
        )
    }

    clientToMove?.let { client ->
        GroupPickerDialog(
            groups = groups,
            currentGroupId = client.groupId,
            onDismiss = { clientToMove = null },
            onSelect = { groupId ->
                viewModel.moveClientToGroup(client.id, groupId)
                clientToMove = null
            }
        )
    }

    clientToDelete?.let { client ->
        ConfirmDialog(
            title = "クライアントを削除",
            text = "「${client.name}」を削除します。関連する商談・ToDoもすべて削除され、元に戻せません。",
            onDismiss = { clientToDelete = null },
            onConfirm = {
                viewModel.deleteClient(client.id)
                clientToDelete = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientRow(
    client: ClientEntity,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = client.name,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Column {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
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
                        text = { Text("グループに移動") },
                        onClick = {
                            menuExpanded = false
                            onMove()
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupPickerDialog(
    groups: List<ClientGroupEntity>,
    currentGroupId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("グループに移動") },
        text = {
            Column {
                GroupOptionRow(
                    label = "未分類",
                    selected = currentGroupId == null,
                    onClick = { onSelect(null) }
                )
                groups.forEach { group ->
                    GroupOptionRow(
                        label = group.name,
                        selected = currentGroupId == group.id,
                        onClick = { onSelect(group.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

@Composable
private fun GroupOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
