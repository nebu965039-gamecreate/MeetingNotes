package com.meetingnotes.ui.client

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.FolderEntity
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.ui.common.ConfirmDialog
import com.meetingnotes.ui.common.DealPhaseChip
import com.meetingnotes.ui.common.DealPhasePickerDialog
import com.meetingnotes.ui.common.TextInputDialog
import com.meetingnotes.ui.common.effectivePhase
import com.meetingnotes.ui.theme.CreateActionBlue
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

/** アーカイブ一覧用の簡潔な要約。先頭の1文(最長60字)だけを見せ、見切れを目立たせない。 */
private fun conciseSummary(summary: String): String {
    val flat = summary.replace(Regex("\\s+"), " ").trim()
    if (flat.isEmpty()) return "(要約なし)"
    val firstSentence = flat.split("。", "\n").firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val base = if (firstSentence.isNotEmpty()) firstSentence else flat
    val trimmed = if (base.length > 60) base.take(60) + "…" else base
    return if (trimmed.endsWith("…") || trimmed == flat || !flat.contains("。")) trimmed else "$trimmed。"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    repository: MeetingRepository,
    clientId: Long,
    onStartRecording: (Long) -> Unit,
    onShowBriefing: (Long) -> Unit,
    onMeetingSelected: (Long) -> Unit,
    onBack: () -> Unit,
    onClientDeleted: () -> Unit
) {
    val viewModel: ClientDetailViewModel = viewModel(
        factory = ClientDetailViewModel.factory(repository, clientId)
    )
    val client by viewModel.client.collectAsState()
    val openTodos by viewModel.openTodos.collectAsState()
    val meetings by viewModel.sortedMeetings.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    var menuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var showEditInfoDialog by remember { mutableStateOf(false) }
    var showDeleteClientDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    // フォルダごとの展開状態。未登録(=このMapに無い)場合はデフォルトで未展開。
    val expandedFolders = remember { mutableStateMapOf<Long, Boolean>() }
    var meetingToRename by remember { mutableStateOf<MeetingEntity?>(null) }
    var meetingToMove by remember { mutableStateOf<MeetingEntity?>(null) }
    var meetingToDelete by remember { mutableStateOf<MeetingEntity?>(null) }
    var meetingToPhase by remember { mutableStateOf<MeetingEntity?>(null) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "クライアント",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                        Text(
                            text = client?.name ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) viewModel.setSearchQuery("")
                    }) {
                        Icon(
                            if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchActive) "検索を閉じる" else "検索"
                        )
                    }
                    IconButton(onClick = { showAddFolderDialog = true }) {
                        Icon(
                            Icons.Filled.CreateNewFolder,
                            contentDescription = "フォルダを作成",
                            tint = CreateActionBlue
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        if (meetings.isNotEmpty()) {
                            DropdownMenuItem(
                                text = { Text("前回のおさらい") },
                                onClick = {
                                    menuExpanded = false
                                    onShowBriefing(clientId)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("クライアント情報") },
                            onClick = {
                                menuExpanded = false
                                showEditInfoDialog = true
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
            Column(modifier = Modifier.navigationBarsPadding()) {
                BannerAdView()
                Surface(shadowElevation = 4.dp) {
                    Button(
                        onClick = {
                            // 2回目以降は録音前に「前回のおさらい」を挟む
                            if (meetings.isNotEmpty()) onShowBriefing(clientId) else onStartRecording(clientId)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Icon(Icons.Filled.Mic, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(text = "録音開始")
                    }
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
            if (searchActive) {
                item {
                    ArchiveSearchField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        focusRequester = searchFocusRequester
                    )
                }
            }

            val searching = searchActive && searchQuery.isNotBlank()

            if (!searching && openTodos.isNotEmpty()) {
                item(key = "open_todos") {
                    OpenTodoSection(
                        todos = openTodos,
                        onOpenMeeting = onMeetingSelected,
                        onComplete = { viewModel.completeTodo(it) }
                    )
                }
            }

            if (searching) {
                item {
                    Text(
                        text = "「${searchQuery.trim()}」の検索結果 ${searchResults.size}件",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                if (searchResults.isEmpty()) {
                    item { Text("一致する商談がありません。", style = MaterialTheme.typography.bodyMedium) }
                } else {
                    items(searchResults, key = { it.meeting.id }) { result ->
                        MeetingRow(
                            meeting = result.meeting,
                            matchPreview = result.snippet,
                            onClick = { onMeetingSelected(result.meeting.id) },
                            onRename = { meetingToRename = result.meeting },
                            onMove = { meetingToMove = result.meeting },
                            onDelete = { meetingToDelete = result.meeting },
                            onChangePhase = { meetingToPhase = result.meeting }
                        )
                    }
                }
                return@LazyColumn
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "アーカイブ", style = MaterialTheme.typography.titleMedium)
                    if (meetings.size > 1) {
                        Box {
                            TextButton(onClick = { sortMenuExpanded = true }) {
                                Icon(
                                    Icons.Filled.SwapVert,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(sortOrder.label)
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                MeetingSortOrder.entries.forEach { order ->
                                    DropdownMenuItem(
                                        text = { Text(order.label) },
                                        onClick = {
                                            viewModel.setSortOrder(order)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
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
                        onDelete = { meetingToDelete = meeting },
                        onChangePhase = { meetingToPhase = meeting }
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
                                    onDelete = { meetingToDelete = meeting },
                                    onChangePhase = { meetingToPhase = meeting }
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
                            onDelete = { meetingToDelete = meeting },
                            onChangePhase = { meetingToPhase = meeting }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
    }

    if (showEditInfoDialog) {
        ClientInfoDialog(
            initialName = client?.name.orEmpty(),
            initialEmail = client?.email.orEmpty(),
            initialPhone = client?.phone.orEmpty(),
            onDismiss = { showEditInfoDialog = false },
            onConfirm = { name, email, phone ->
                viewModel.updateClientInfo(name, email, phone)
                showEditInfoDialog = false
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

    meetingToPhase?.let { meeting ->
        DealPhasePickerDialog(
            current = meeting.effectivePhase(),
            onDismiss = { meetingToPhase = null },
            onSelect = { phase ->
                viewModel.setMeetingPhase(meeting.id, phase)
                meetingToPhase = null
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

/** アーカイブの検索入力欄。丸みのあるコンパクトなバー。 */
@Composable
private fun ArchiveSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester
) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 9.dp)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            "タイトル・要約・文字起こしを検索",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            )
            if (value.isNotEmpty()) {
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "クリア",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
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
    onDelete: () -> Unit,
    onChangePhase: () -> Unit,
    matchPreview: String? = null
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
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // タイトル(強調)と、その右端にフェーズタグ。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = meeting.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    DealPhaseChip(
                        phase = meeting.effectivePhase(),
                        onClick = onChangePhase
                    )
                }
                val recordedAt = Instant.ofEpochMilli(meeting.recordedAt).atZone(ZoneId.systemDefault())
                Text(text = recordedAt.format(dateFormatter), style = MaterialTheme.typography.bodySmall)
                if (!matchPreview.isNullOrEmpty()) {
                    Text(
                        text = matchPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = conciseSummary(meeting.summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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

/** クライアント詳細の「未完了ToDo」セクション(アーカイブの上)。 */
@Composable
private fun OpenTodoSection(
    todos: List<com.meetingnotes.data.local.TodoEntity>,
    onOpenMeeting: (Long) -> Unit,
    onComplete: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "未完了のToDo (${todos.size}件)",
                style = MaterialTheme.typography.titleMedium
            )
            todos.forEach { t ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMeeting(t.meetingId) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onComplete(t.id) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.CheckCircleOutline,
                            contentDescription = "完了にする",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t.task,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val sub = buildString {
                            append("担当 ${t.assignee}")
                            if (t.deadline.isNotBlank() && t.deadline != "未定") append("・期限 ${t.deadline}")
                        }
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 名前・メール・電話を編集するダイアログ。 */
@Composable
private fun ClientInfoDialog(
    initialName: String,
    initialEmail: String,
    initialPhone: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, email: String?, phone: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var email by remember { mutableStateOf(initialEmail) }
    var phone by remember { mutableStateOf(initialPhone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("クライアント情報") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("クライアント名") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email, onValueChange = { email = it },
                    label = { Text("メールアドレス（任意）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("電話番号（任意）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { onConfirm(name.trim(), email.trim(), phone.trim()) },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}
