package com.meetingnotes.ui.client

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.FolderEntity
import com.meetingnotes.data.local.MeetingEntity
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.model.DealPhase
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
    onEditClient: () -> Unit,
    onBack: () -> Unit,
    onClientDeleted: () -> Unit
) {
    val viewModel: ClientDetailViewModel = viewModel(
        factory = ClientDetailViewModel.factory(repository, clientId)
    )
    val client by viewModel.client.collectAsState()
    val openTodos by viewModel.openTodos.collectAsState()
    val doneTodos by viewModel.doneTodos.collectAsState()
    val meetings by viewModel.sortedMeetings.collectAsState()
    val hasAnyMeeting by viewModel.hasAnyMeeting.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val projectFilter by viewModel.projectFilter.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val projectNameOf: (MeetingEntity) -> String? = { m ->
        m.projectId?.let { pid -> projects.firstOrNull { it.id == pid }?.name }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var showDeleteClientDialog by remember { mutableStateOf(false) }
    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showManageProjects by remember { mutableStateOf(false) }
    // フォルダごとの展開状態。未登録(=このMapに無い)場合はデフォルトで未展開。
    val expandedFolders = remember { mutableStateMapOf<Long, Boolean>() }
    var meetingToRename by remember { mutableStateOf<MeetingEntity?>(null) }
    var meetingToMove by remember { mutableStateOf<MeetingEntity?>(null) }
    var meetingToDelete by remember { mutableStateOf<MeetingEntity?>(null) }
    var meetingToPhase by remember { mutableStateOf<MeetingEntity?>(null) }
    var meetingToProject by remember { mutableStateOf<MeetingEntity?>(null) }
    var folderToRename by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "クライアント",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = client?.name ?: "",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                if (hasAnyMeeting) {
                                    DropdownMenuItem(
                                        text = { Text("前回のおさらい") },
                                        onClick = { menuExpanded = false; onShowBriefing(clientId) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("プロジェクトを管理") },
                                    onClick = { menuExpanded = false; showManageProjects = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("クライアントを削除") },
                                    onClick = { menuExpanded = false; showDeleteClientDialog = true }
                                )
                            }
                        }
                    }
                )
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("アーカイブ") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(if (openTodos.isEmpty()) "ToDo" else "ToDo (${openTodos.size})") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("情報") }
                    )
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                BannerAdView()
                Surface(shadowElevation = 4.dp) {
                    Button(
                        onClick = {
                            // 2回目以降は録音前に「前回のおさらい」を挟む
                            if (hasAnyMeeting) onShowBriefing(clientId) else onStartRecording(clientId)
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
      Column(modifier = Modifier.fillMaxSize().padding(padding)) {
       if (projects.isNotEmpty() && selectedTab != 2) {
           ProjectFilterBar(
               projects = projects,
               selected = projectFilter,
               onSelect = { viewModel.setProjectFilter(it) },
               modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
           )
       }
       Box(modifier = Modifier.fillMaxSize()) {
       when (selectedTab) {
        1 -> TodoTab(
            openTodos = openTodos,
            doneTodos = doneTodos,
            onOpenMeeting = onMeetingSelected,
            onComplete = { viewModel.completeTodo(it) },
            onReopen = { viewModel.reopenTodo(it) },
            onAddTodo = { task, dueDate -> viewModel.addManualTodo(task, dueDate) },
            onEditTodo = { id, task, dueDate -> viewModel.updateTodo(id, task, dueDate) },
            onDeleteTodo = { viewModel.deleteTodo(it) }
        )
        2 -> ClientInfoContent(repository = repository, clientId = clientId, onEdit = onEditClient)
        else -> LazyColumn(
            modifier = Modifier
                .fillMaxSize()
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
            }

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
                            onChangePhase = { meetingToPhase = result.meeting },
                            onChangeProject = { meetingToProject = result.meeting },
                            projectName = projectNameOf(result.meeting)
                        )
                    }
                }
                return@LazyColumn
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
                        onChangePhase = { meetingToPhase = meeting },
                        onChangeProject = { meetingToProject = meeting },
                        projectName = projectNameOf(meeting)
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
                                    onChangePhase = { meetingToPhase = meeting },
                                    onChangeProject = { meetingToProject = meeting },
                                    projectName = projectNameOf(meeting)
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
                            onChangePhase = { meetingToPhase = meeting },
                            onChangeProject = { meetingToProject = meeting },
                            projectName = projectNameOf(meeting)
                        )
                    }
                }
            }
        }
       }
      }
     }
    }

    LaunchedEffect(searchActive) {
        if (searchActive) searchFocusRequester.requestFocus()
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

    if (showManageProjects) {
        ManageProjectsDialog(
            projects = projects,
            onAdd = { name, phase, currency, est, won, wonAt, lostReason, closeAt, prob ->
                viewModel.addProject(name, phase, currency, est, won, wonAt, lostReason, closeAt, prob)
            },
            onUpdate = { id, name, phase, currency, est, won, wonAt, lostReason, closeAt, prob ->
                viewModel.updateProject(id, name, phase, currency, est, won, wonAt, lostReason, closeAt, prob)
            },
            onDelete = { viewModel.deleteProject(it) },
            onDismiss = { showManageProjects = false }
        )
    }

    meetingToProject?.let { meeting ->
        ProjectPickerDialog(
            projects = projects,
            currentProjectId = meeting.projectId,
            onDismiss = { meetingToProject = null },
            onSelect = { projectId ->
                viewModel.setMeetingProject(meeting.id, projectId)
                meetingToProject = null
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
    onChangeProject: () -> Unit,
    projectName: String? = null,
    matchPreview: String? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
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
                        color = MaterialTheme.colorScheme.onSurface,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = recordedAt.format(dateFormatter), style = MaterialTheme.typography.bodySmall)
                    if (!projectName.isNullOrBlank()) {
                        Text(
                            text = "・📁 $projectName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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
                        text = { Text("プロジェクトを変更") },
                        onClick = { menuExpanded = false; onChangeProject() }
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

/** アーカイブ/ToDo の上に置くコンパクトなプロジェクト絞り込みバー。 */
@Composable
private fun ProjectFilterBar(
    projects: List<ClientProjectEntity>,
    selected: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        null -> "すべての商談"
        ClientDetailViewModel.NO_PROJECT -> "プロジェクトなし"
        else -> projects.firstOrNull { it.id == selected }?.name ?: "すべての商談"
    }
    Box(modifier) {
        TextButton(
            onClick = { expanded = true },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("すべての商談") },
                onClick = { onSelect(null); expanded = false }
            )
            DropdownMenuItem(
                text = { Text("プロジェクトなし") },
                onClick = { onSelect(ClientDetailViewModel.NO_PROJECT); expanded = false }
            )
            projects.forEach { project ->
                DropdownMenuItem(
                    text = { Text(project.name) },
                    onClick = { onSelect(project.id); expanded = false }
                )
            }
        }
    }
}

/** 商談を1つのプロジェクトに割り当てるピッカー。 */
@Composable
private fun ProjectPickerDialog(
    projects: List<ClientProjectEntity>,
    currentProjectId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("プロジェクトを変更") },
        text = {
            Column {
                FolderOptionRow(
                    label = "なし",
                    selected = currentProjectId == null,
                    onClick = { onSelect(null) }
                )
                projects.forEach { project ->
                    FolderOptionRow(
                        label = project.name,
                        selected = currentProjectId == project.id,
                        onClick = { onSelect(project.id) }
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

private typealias ProjectSaveArgs = (
    name: String, phase: DealPhase?, currency: String,
    estimatedAmount: Long?, wonAmount: Long?, wonAt: Long?, lostReason: String?,
    expectedCloseAt: Long?, probability: Int?
) -> Unit

/** 進行中案件の「確度 X% ・ 想定 M/d」行。どちらも未設定なら null。 */
private fun projectForecastLabel(p: ClientProjectEntity): String? {
    val phase = DealPhase.fromWire(p.phase)
    if (phase?.isActive == false) return null
    val parts = buildList {
        val prob = p.probability ?: phase?.defaultProbability
        if (p.probability != null) add("確度 ${p.probability}%")
        else if (prob != null) add("確度 ${prob}%（既定）")
        p.expectedCloseAt?.let {
            val d = java.time.Instant.ofEpochMilli(it)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            add("想定クローズ ${d.monthValue}/${d.dayOfMonth}")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ・ ")
}

private val LOST_REASON_PRESETS = listOf(
    "価格・予算が合わない",
    "時期・タイミングが合わない",
    "競合他社に決定",
    "社内で見送り・保留",
    "音信不通",
    "要件・スコープの不一致"
)

/** 案件(= プロジェクト)の一覧・追加・編集・削除。金額(見積/成約)・進捗・成約日を持つ。 */
@Composable
private fun ManageProjectsDialog(
    projects: List<ClientProjectEntity>,
    onAdd: ProjectSaveArgs,
    onUpdate: (Long, String, DealPhase?, String, Long?, Long?, Long?, String?, Long?, Int?) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var showForm by remember { mutableStateOf(false) }
    var projectToEdit by remember { mutableStateOf<ClientProjectEntity?>(null) }
    var projectToDelete by remember { mutableStateOf<ClientProjectEntity?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("案件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (projects.isEmpty()) {
                    Text(
                        "案件はまだありません。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                projects.forEach { p ->
                    val cur = com.meetingnotes.util.Currency.of(p.currency)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { projectToEdit = p }
                                .padding(vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                DealPhase.fromWire(p.phase)?.let {
                                    Spacer(Modifier.width(6.dp))
                                    DealPhaseChip(phase = it)
                                }
                            }
                            val amountLine = buildList {
                                p.estimatedAmount?.let { add("見積 " + com.meetingnotes.util.formatMoney(it, cur)) }
                                p.wonAmount?.let { add("成約 " + com.meetingnotes.util.formatMoney(it, cur)) }
                            }.joinToString(" ・ ")
                            if (amountLine.isNotEmpty()) {
                                Text(
                                    amountLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (DealPhase.fromWire(p.phase) == DealPhase.LOST && !p.lostReason.isNullOrBlank()) {
                                Text(
                                    "失注理由: ${p.lostReason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val forecastLine = projectForecastLabel(p)
                            if (forecastLine != null) {
                                Text(
                                    forecastLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { projectToDelete = p }) {
                            Icon(Icons.Filled.Delete, contentDescription = "削除", modifier = Modifier.size(18.dp))
                        }
                    }
                }
                TextButton(onClick = { showForm = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("案件を追加")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("閉じる") }
        }
    )

    if (showForm) {
        ProjectFormDialog(
            editing = null,
            onDismiss = { showForm = false },
            onConfirm = { name, phase, currency, est, won, wonAt, lostReason, closeAt, prob ->
                onAdd(name, phase, currency, est, won, wonAt, lostReason, closeAt, prob)
                showForm = false
            }
        )
    }

    projectToEdit?.let { p ->
        ProjectFormDialog(
            editing = p,
            onDismiss = { projectToEdit = null },
            onConfirm = { name, phase, currency, est, won, wonAt, lostReason, closeAt, prob ->
                onUpdate(p.id, name, phase, currency, est, won, wonAt, lostReason, closeAt, prob)
                projectToEdit = null
            }
        )
    }

    projectToDelete?.let { p ->
        ConfirmDialog(
            title = "案件を削除",
            text = "「${p.name}」を削除します。この案件の商談は削除されず、案件未設定に戻ります。",
            onDismiss = { projectToDelete = null },
            onConfirm = {
                onDelete(p.id)
                projectToDelete = null
            }
        )
    }
}

/** 案件の追加・編集フォーム。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectFormDialog(
    editing: ClientProjectEntity?,
    onDismiss: () -> Unit,
    onConfirm: ProjectSaveArgs
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var phase by remember { mutableStateOf(DealPhase.fromWire(editing?.phase)) }
    var currency by remember { mutableStateOf(com.meetingnotes.util.Currency.of(editing?.currency)) }
    var estText by remember { mutableStateOf(editing?.estimatedAmount?.toString().orEmpty()) }
    var wonText by remember { mutableStateOf(editing?.wonAmount?.toString().orEmpty()) }
    var wonDate by remember {
        mutableStateOf(
            editing?.wonAt?.let {
                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            } ?: java.time.LocalDate.now()
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var lostReason by remember { mutableStateOf(editing?.lostReason.orEmpty()) }
    var probText by remember { mutableStateOf(editing?.probability?.toString().orEmpty()) }
    var closeDate by remember {
        mutableStateOf(
            editing?.expectedCloseAt?.let {
                java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            }
        )
    }
    var showCloseDatePicker by remember { mutableStateOf(false) }

    val phaseOptions = buildList<Pair<DealPhase?, String>> {
        add(null to "設定なし")
        DealPhase.entries.forEach { add(it to it.label) }
    }
    val currencyOptions = com.meetingnotes.util.Currency.entries.map { it to it.label }
    val lostReasonOptions = buildList<Pair<String?, String>> {
        add(null to "プリセットから選ぶ")
        LOST_REASON_PRESETS.forEach { add(it to it) }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "案件を追加" else "案件を編集") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("案件名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                com.meetingnotes.ui.common.LabeledDropdownField(
                    label = "進捗",
                    options = phaseOptions,
                    selected = phase,
                    onSelect = { phase = it },
                    modifier = Modifier.fillMaxWidth()
                )
                com.meetingnotes.ui.common.LabeledDropdownField(
                    label = "通貨",
                    options = currencyOptions,
                    selected = currency,
                    onSelect = { currency = it },
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = estText,
                    onValueChange = { estText = it.filter { c -> c.isDigit() } },
                    label = { Text("見積額 (${currency.symbol})") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = wonText,
                    onValueChange = { wonText = it.filter { c -> c.isDigit() } },
                    label = { Text("成約額 (${currency.symbol})") },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (phase?.isActive != false) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showCloseDatePicker = true }) {
                            Text(
                                closeDate?.let { "想定クローズ日: ${it.monthValue}月${it.dayOfMonth}日" }
                                    ?: "想定クローズ日を設定"
                            )
                        }
                        if (closeDate != null) {
                            TextButton(onClick = { closeDate = null }) { Text("クリア") }
                        }
                    }
                    androidx.compose.material3.OutlinedTextField(
                        value = probText,
                        onValueChange = { probText = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("受注確度 (%)") },
                        placeholder = { Text("未入力なら ${(phase ?: DealPhase.FIRST_CONTACT).defaultProbability}%（フェーズ既定）") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (phase == DealPhase.WON) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text("成約日: ${wonDate.monthValue}月${wonDate.dayOfMonth}日")
                    }
                }
                if (phase == DealPhase.LOST) {
                    com.meetingnotes.ui.common.LabeledDropdownField(
                        label = "失注理由",
                        options = lostReasonOptions,
                        selected = LOST_REASON_PRESETS.firstOrNull { it == lostReason },
                        onSelect = { it?.let { v -> lostReason = v } },
                        modifier = Modifier.fillMaxWidth()
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = lostReason,
                        onValueChange = { lostReason = it },
                        label = { Text("失注理由（自由記述可）") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val wonAtMillis = if (phase == DealPhase.WON) {
                        wonDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } else null
                    val active = phase?.isActive != false
                    onConfirm(
                        name, phase, currency.code,
                        com.meetingnotes.util.parseMoneyInput(estText),
                        com.meetingnotes.util.parseMoneyInput(wonText),
                        wonAtMillis,
                        if (phase == DealPhase.LOST) lostReason.trim().ifBlank { null } else null,
                        if (active) closeDate?.atStartOfDay(java.time.ZoneId.systemDefault())
                            ?.toInstant()?.toEpochMilli() else null,
                        if (active) probText.toIntOrNull() else null
                    )
                },
                enabled = name.isNotBlank()
            ) { Text(if (editing == null) "追加" else "保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )

    if (showDatePicker) {
        com.meetingnotes.ui.common.NextMeetingDateTimeDialog(
            initial = wonDate.atStartOfDay(),
            initialHasTime = false,
            onDismiss = { showDatePicker = false },
            onConfirm = { dt, _ ->
                wonDate = dt.toLocalDate()
                showDatePicker = false
            }
        )
    }

    if (showCloseDatePicker) {
        com.meetingnotes.ui.common.NextMeetingDateTimeDialog(
            initial = (closeDate ?: java.time.LocalDate.now().plusWeeks(2)).atStartOfDay(),
            initialHasTime = false,
            onDismiss = { showCloseDatePicker = false },
            onConfirm = { dt, _ ->
                closeDate = dt.toLocalDate()
                showCloseDatePicker = false
            }
        )
    }
}

/** クライアント詳細の「ToDo」タブ。ToDo / 完了 の2サブタブ。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodoTab(
    openTodos: List<TodoEntity>,
    doneTodos: List<TodoEntity>,
    onOpenMeeting: (Long) -> Unit,
    onComplete: (Long) -> Unit,
    onReopen: (Long) -> Unit,
    onAddTodo: (task: String, dueDate: String?) -> Unit,
    onEditTodo: (todoId: Long, task: String, dueDate: String?) -> Unit,
    onDeleteTodo: (Long) -> Unit
) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TodoEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = sub) {
            Tab(selected = sub == 0, onClick = { sub = 0 }, text = { Text("ToDo (${openTodos.size})") })
            Tab(selected = sub == 1, onClick = { sub = 1 }, text = { Text("完了 (${doneTodos.size})") })
        }

        if (sub == 0) {
            TextButton(
                onClick = { showAdd = true },
                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("ToDoを追加")
            }
        }

        val list = if (sub == 0) openTodos else doneTodos
        if (list.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (sub == 0) "未完了のToDoはありません。「ToDoを追加」から登録できます。"
                    else "完了したToDoはありません。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(list, key = { it.id }) { t ->
                    TodoRowItem(
                        todo = t,
                        done = sub == 1,
                        onClick = {
                            if (t.meetingId != null) onOpenMeeting(t.meetingId)
                            else editing = t
                        },
                        onToggle = { if (sub == 0) onComplete(t.id) else onReopen(t.id) }
                    )
                }
            }
        }
    }

    if (showAdd) {
        TodoFormDialog(
            editing = null,
            onDismiss = { showAdd = false },
            onConfirm = { task, dueDate -> onAddTodo(task, dueDate); showAdd = false },
            onDelete = null
        )
    }
    editing?.let { t ->
        TodoFormDialog(
            editing = t,
            onDismiss = { editing = null },
            onConfirm = { task, dueDate -> onEditTodo(t.id, task, dueDate); editing = null },
            onDelete = { onDeleteTodo(t.id); editing = null }
        )
    }
}

/** 手動 ToDo の追加・編集フォーム(本文 + 任意の期限日)。 */
@Composable
private fun TodoFormDialog(
    editing: TodoEntity?,
    onDismiss: () -> Unit,
    onConfirm: (task: String, dueDate: String?) -> Unit,
    onDelete: (() -> Unit)?
) {
    var task by remember { mutableStateOf(editing?.task.orEmpty()) }
    var due by remember {
        mutableStateOf(editing?.dueDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() })
    }
    var showDatePicker by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "ToDoを追加" else "ToDoを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = task,
                    onValueChange = { task = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(
                            due?.let { "期限: ${it.monthValue}月${it.dayOfMonth}日" } ?: "期限日を設定"
                        )
                    }
                    if (due != null) {
                        TextButton(onClick = { due = null }) { Text("クリア") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(task, due?.toString()) },
                enabled = task.isNotBlank()
            ) { Text(if (editing == null) "追加" else "保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("削除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("キャンセル") }
            }
        }
    )

    if (showDatePicker) {
        com.meetingnotes.ui.common.NextMeetingDateTimeDialog(
            initial = (due ?: java.time.LocalDate.now()).atStartOfDay(),
            initialHasTime = false,
            onDismiss = { showDatePicker = false },
            onConfirm = { dt, _ -> due = dt.toLocalDate(); showDatePicker = false }
        )
    }
}

@Composable
private fun TodoRowItem(todo: TodoEntity, done: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = done, onCheckedChange = { onToggle() })
        Spacer(Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                todo.task,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val sub = buildString {
                if (todo.meetingId == null) append("手動") else append("担当 ${todo.assignee}")
                val dueLabel = todo.dueDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                if (dueLabel != null) {
                    append("・期限 ${dueLabel.monthValue}/${dueLabel.dayOfMonth}")
                } else if (todo.deadline.isNotBlank() && todo.deadline != "未定") {
                    append("・期限 ${todo.deadline}")
                }
            }
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

