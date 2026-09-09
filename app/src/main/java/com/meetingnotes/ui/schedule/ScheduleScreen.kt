package com.meetingnotes.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.data.model.NextMeetingTime
import com.meetingnotes.ui.client.UpcomingItem
import com.meetingnotes.ui.common.ConfirmDialog
import com.meetingnotes.ui.common.DealPhaseChip
import com.meetingnotes.ui.common.LabeledDropdownField
import com.meetingnotes.ui.common.NextMeetingDateTimeDialog
import com.meetingnotes.ui.common.relativeDateTimeLabel
import com.meetingnotes.util.CalendarIntent
import com.meetingnotes.util.JapaneseHolidays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val SATURDAY_COLOR = Color(0xFF1565C0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    repository: MeetingRepository,
    onOpenClient: (Long) -> Unit,
    onHome: () -> Unit = {}
) {
    val viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.factory(repository))
    val allSchedules by viewModel.schedules.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val dueTodos by viewModel.dueTodos.collectAsState()

    var viewedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var scheduleToEdit by remember { mutableStateOf<UpcomingItem?>(null) }
    var scheduleToDelete by remember { mutableStateOf<UpcomingItem?>(null) }

    val today = LocalDate.now()
    val futureSchedules = allSchedules.filter { !it.start.toLocalDate().isBefore(today) }

    val todoDatesByDay = remember(dueTodos) {
        dueTodos.groupBy { runCatching { LocalDate.parse(it.dueDate) }.getOrNull() }
            .filterKeys { it != null }
            .mapKeys { it.key!! }
    }
    val markedDates = remember(allSchedules, todoDatesByDay) {
        allSchedules.map { it.start.toLocalDate() }.toSet() + todoDatesByDay.keys
    }
    val filteredItems = selectedDate?.let { d -> allSchedules.filter { it.start.toLocalDate() == d } }
        ?: futureSchedules
    val selectedDayTodos = selectedDate?.let { todoDatesByDay[it].orEmpty() } ?: emptyList()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "予定表",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onHome) {
                            Icon(Icons.Filled.Home, contentDescription = "ホーム")
                        }
                    }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "予定を追加")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "calendar") {
                MonthCalendar(
                    month = viewedMonth,
                    markedDates = markedDates,
                    selectedDate = selectedDate,
                    onMonthChange = { viewedMonth = it },
                    onDateClick = { date ->
                        selectedDate = if (selectedDate == date) null else date
                    }
                )
            }

            item(key = "list_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val date = selectedDate
                    Text(
                        text = if (date != null) {
                            "${date.monthValue}月${date.dayOfMonth}日の予定 (${filteredItems.size}件)"
                        } else {
                            "これからの予定 (${futureSchedules.size}件)"
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (date != null) {
                        TextButton(onClick = { selectedDate = null }) { Text("すべて表示") }
                    }
                }
            }

            if (filteredItems.isEmpty()) {
                item {
                    Text(
                        "予定はありません。右下の＋から追加できます。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredItems, key = { it.scheduleId }) { item ->
                    ScheduleRow(
                        item = item,
                        onClick = { onOpenClient(item.clientId) },
                        onEdit = { scheduleToEdit = item },
                        onDelete = { scheduleToDelete = item }
                    )
                }
            }

            if (selectedDate != null && selectedDayTodos.isNotEmpty()) {
                item(key = "todo_header") {
                    Text(
                        "この日が期限のToDo (${selectedDayTodos.size}件)",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(selectedDayTodos, key = { "todo-${it.todoId}" }) { t ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenClient(t.clientId) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(t.task, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "${t.clientName}・担当 ${t.assignee}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.completeTodo(t.todoId) }) { Text("完了") }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ScheduleFormDialog(
            clients = clients,
            editing = null,
            onDismiss = { showAddDialog = false },
            onConfirm = { clientId, millis, hasTime, title, participants, note, phase, url, loc ->
                viewModel.addSchedule(clientId, millis, hasTime, title, note, participants, phase, url, loc)
                showAddDialog = false
            }
        )
    }

    scheduleToEdit?.let { item ->
        ScheduleFormDialog(
            clients = clients,
            editing = item,
            onDismiss = { scheduleToEdit = null },
            onConfirm = { _, millis, hasTime, title, participants, note, phase, url, loc ->
                viewModel.updateSchedule(item.scheduleId, millis, hasTime, title, note, participants, phase, url, loc)
                scheduleToEdit = null
            }
        )
    }

    scheduleToDelete?.let { item ->
        ConfirmDialog(
            title = "予定を削除",
            text = "「${item.clientName}」の予定「${item.title}」を削除します。" +
                if (item.fromAi) "商談の「次回打ち合わせ」も未設定に戻ります。" else "",
            onDismiss = { scheduleToDelete = null },
            onConfirm = {
                viewModel.deleteSchedule(item.scheduleId)
                scheduleToDelete = null
            }
        )
    }
}

@Composable
private fun ScheduleRow(
    item: UpcomingItem,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    relativeDateTimeLabel(item.start, item.allDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.title.ifBlank { "打ち合わせ" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.phase != null) {
                        Spacer(Modifier.width(6.dp))
                        DealPhaseChip(phase = item.phase)
                    }
                }
                Text(
                    item.clientName + (if (item.participants.isNotBlank()) " ・ ${item.participants}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                item.location?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        "📍 $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                item.meetingUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    Text(
                        url,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri()))
                            }
                        }
                    )
                }
                if (item.note.isNotBlank()) {
                    Text(
                        item.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
            }
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("カレンダーに追加") },
                        onClick = {
                            menuExpanded = false
                            CalendarIntent.add(
                                context = context,
                                title = item.title.ifBlank { "打ち合わせ" },
                                start = item.start,
                                allDay = item.allDay,
                                description = listOfNotNull(
                                    item.participants.takeIf { it.isNotBlank() }?.let { "参加者: $it" },
                                    item.meetingUrl?.takeIf { it.isNotBlank() },
                                    item.note.takeIf { it.isNotBlank() }
                                ).joinToString("\n"),
                                location = item.location.orEmpty()
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("編集") },
                        onClick = { menuExpanded = false; onEdit() }
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

/** 月カレンダー。予定がある日に丸印、土曜は青・日曜と祝日は赤。タップで一覧を絞り込む。 */
@Composable
private fun MonthCalendar(
    month: YearMonth,
    markedDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    onMonthChange: (YearMonth) -> Unit,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val holidays = remember(month) { JapaneseHolidays.holidaysInMonth(month.year, month.monthValue) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "前の月")
                }
                Text(
                    "${month.year}年${month.monthValue}月",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "次の月")
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    // dow: 0=日曜, 1=月曜, ..., 6=土曜(DayOfWeek は月曜=1...日曜=7)
                    val label = DayOfWeek.of(if (dow == 0) 7 else dow)
                        .getDisplayName(TextStyle.SHORT, Locale.JAPAN)
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (dow) {
                            0 -> MaterialTheme.colorScheme.error
                            6 -> SATURDAY_COLOR
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            val firstDay = month.atDay(1)
            // 日曜始まり: 月曜=1...土曜=6、日曜=7 なので mod 7 で日曜=0 に揃える
            val leadingBlanks = firstDay.dayOfWeek.value % 7
            val daysInMonth = month.lengthOfMonth()
            val rows = (leadingBlanks + daysInMonth + 6) / 7

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNum = row * 7 + col - leadingBlanks + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.4f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNum in 1..daysInMonth) {
                                val date = month.atDay(dayNum)
                                val isSelected = date == selectedDate
                                val hasMark = date in markedDates
                                val isSunday = date.dayOfWeek == DayOfWeek.SUNDAY
                                val isSaturday = date.dayOfWeek == DayOfWeek.SATURDAY
                                val isHoliday = date in holidays
                                val numberColor = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    isSunday || isHoliday -> MaterialTheme.colorScheme.error
                                    isSaturday -> SATURDAY_COLOR
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { onDateClick(date) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = numberColor
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 2.dp)
                                            .size(4.dp)
                                            .then(
                                                if (hasMark) {
                                                    Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleFormDialog(
    clients: List<Pair<Long, String>>,
    editing: UpcomingItem?,
    onDismiss: () -> Unit,
    onConfirm: (
        clientId: Long, startAtMillis: Long, hasTime: Boolean,
        title: String, participants: String, note: String, phase: DealPhase?,
        meetingUrl: String?, location: String?
    ) -> Unit
) {
    var clientId by remember { mutableStateOf(editing?.clientId ?: clients.firstOrNull()?.first) }
    var dateTime by remember {
        mutableStateOf(editing?.start ?: LocalDate.now().plusDays(1).atTime(10, 0))
    }
    var hasTime by remember { mutableStateOf(editing?.allDay == false) }
    var title by remember { mutableStateOf(editing?.title?.takeIf { it.isNotBlank() } ?: "打ち合わせ") }
    var participants by remember { mutableStateOf(editing?.participants.orEmpty()) }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    var meetingUrl by remember { mutableStateOf(editing?.meetingUrl.orEmpty()) }
    var location by remember { mutableStateOf(editing?.location.orEmpty()) }
    var phase by remember { mutableStateOf(editing?.phase) }
    var showPicker by remember { mutableStateOf(false) }

    val phaseOptions = buildList<Pair<DealPhase?, String>> {
        add(null to "設定なし")
        DealPhase.entries.forEach { add(it to it.label) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "予定を追加" else "予定を編集") },
        text = {
            if (clients.isEmpty()) {
                Text("クライアントがいません。先にクライアントを追加してください。")
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LabeledDropdownField(
                        label = "クライアント",
                        options = clients,
                        selected = clientId ?: clients.first().first,
                        onSelect = { clientId = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { showPicker = true }) {
                        Text(
                            "日時: ${dateTime.monthValue}月${dateTime.dayOfMonth}日" +
                                if (hasTime) " %02d:%02d".format(dateTime.hour, dateTime.minute) else "（終日）"
                        )
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("内容・タイトル") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = participants,
                        onValueChange = { participants = it },
                        label = { Text("参加者") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = meetingUrl,
                        onValueChange = { meetingUrl = it },
                        label = { Text("会議URL（Zoom/Meet 等）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("場所") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("メモ") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LabeledDropdownField(
                        label = "進捗ラベル",
                        options = phaseOptions,
                        selected = phase,
                        onSelect = { phase = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clientId?.let {
                        onConfirm(
                            it, NextMeetingTime.toMillis(dateTime), hasTime,
                            title, participants, note, phase,
                            meetingUrl.trim().ifBlank { null }, location.trim().ifBlank { null }
                        )
                    }
                },
                enabled = clients.isNotEmpty() && clientId != null
            ) { Text(if (editing == null) "追加" else "保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )

    if (showPicker) {
        NextMeetingDateTimeDialog(
            initial = dateTime,
            initialHasTime = hasTime,
            onDismiss = { showPicker = false },
            onConfirm = { dt, withTime ->
                dateTime = dt
                hasTime = withTime
                showPicker = false
            }
        )
    }
}
