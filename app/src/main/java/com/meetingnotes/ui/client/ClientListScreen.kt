package com.meetingnotes.ui.client

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
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
    onClientSelected: (Long) -> Unit,
    onHelp: () -> Unit
) {
    val viewModel: ClientListViewModel = viewModel(factory = ClientListViewModel.factory(repository))
    val clients by viewModel.clients.collectAsState()
    val groups by viewModel.groups.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddGroupDialog by remember { mutableStateOf(false) }
    var clientToRename by remember { mutableStateOf<ClientEntity?>(null) }
    var clientToDelete by remember { mutableStateOf<ClientEntity?>(null) }
    var clientToMove by remember { mutableStateOf<ClientEntity?>(null) }
    var groupToRename by remember { mutableStateOf<ClientGroupEntity?>(null) }
    var groupToDelete by remember { mutableStateOf<ClientGroupEntity?>(null) }
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
                },
                actions = {
                    IconButton(onClick = { showAddGroupDialog = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "グループを作成")
                    }
                    IconButton(onClick = onHelp) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "使い方・ヘルプ")
                    }
                }
            )
        },
        bottomBar = { BannerAdView(Modifier.navigationBarsPadding()) },
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
                    items(groups, key = { "group_${it.id}" }) { group ->
                        val groupClients = clients.filter { it.groupId == group.id }
                        val isExpanded = expandedGroups[group.id] == true
                        GroupFolderCard(
                            group = group,
                            clients = groupClients,
                            isExpanded = isExpanded,
                            onToggleExpand = { expandedGroups[group.id] = !isExpanded },
                            onRenameGroup = { groupToRename = group },
                            onDeleteGroup = { groupToDelete = group },
                            onClientClick = { onClientSelected(it.id) },
                            onClientRename = { clientToRename = it },
                            onClientMove = { clientToMove = it },
                            onClientDelete = { clientToDelete = it }
                        )
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

    groupToRename?.let { group ->
        TextInputDialog(
            title = "グループ名を変更",
            label = "グループ名",
            initialValue = group.name,
            confirmLabel = "変更",
            onDismiss = { groupToRename = null },
            onConfirm = { name ->
                viewModel.renameGroup(group.id, name)
                groupToRename = null
            }
        )
    }

    groupToDelete?.let { group ->
        ConfirmDialog(
            title = "グループを削除",
            text = "「${group.name}」を削除します。所属するクライアントは削除されず、未分類に戻ります。",
            onDismiss = { groupToDelete = null },
            onConfirm = {
                viewModel.deleteGroup(group.id)
                groupToDelete = null
            }
        )
    }
}

/**
 * クライアントグループを「フォルダ」として視覚的に表現するカード。
 * 外枠のCardが各クライアントを内包する形にすることで、クライアントより上位の階層であることを示す。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupFolderCard(
    group: ClientGroupEntity,
    clients: List<ClientEntity>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRenameGroup: () -> Unit,
    onDeleteGroup: () -> Unit,
    onClientClick: (ClientEntity) -> Unit,
    onClientRename: (ClientEntity) -> Unit,
    onClientMove: (ClientEntity) -> Unit,
    onClientDelete: (ClientEntity) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                    contentDescription = if (isExpanded) "折りたたむ" else "展開する",
                    modifier = Modifier.padding(end = 12.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${clients.size}件のクライアント",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "グループメニュー")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("名前を変更") },
                        onClick = {
                            menuExpanded = false
                            onRenameGroup()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("削除") },
                        onClick = {
                            menuExpanded = false
                            onDeleteGroup()
                        }
                    )
                }
            }

            if (isExpanded) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (clients.isEmpty()) {
                        Text(
                            text = "(このグループにはクライアントがいません)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        clients.forEach { client ->
                            ClientRow(
                                client = client,
                                onClick = { onClientClick(client) },
                                onRename = { onClientRename(client) },
                                onMove = { onClientMove(client) },
                                onDelete = { onClientDelete(client) }
                            )
                        }
                    }
                }
            }
        }
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
