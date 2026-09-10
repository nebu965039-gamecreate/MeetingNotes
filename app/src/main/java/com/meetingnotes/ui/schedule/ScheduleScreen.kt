package com.meetingnotes.ui.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
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
import com.meetingnotes.ui.common.AppIcons
import com.meetingnotes.ui.common.TabTopBar
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.data.model.NextMeetingTime
import com.meetingnotes.ui.client.UpcomingItem
import com.meetingnotes.ui.client.UpcomingRow
import com.meetingnotes.ui.common.ConfirmDialog
import com.meetingnotes.ui.common.DealPhaseChip
import com.meetingnotes.ui.common.LabeledDropdownField
import com.meetingnotes.ui.common.NextMeetingDateTimeDialog
import com.meetingnotes.ui.theme.CreateActionBlue
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
    onOpenMeeting: (Long) -> Unit = {},
    onHome: () -> Unit = {}
) {
    val viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.factory(repository))
    val allSchedules by viewModel.schedules.collectAsState()
    val clients by viewModel.clients.collectAsState()
    val dueTodos by viewModel.dueTodos.collectAsState()
    val latestMeetingByClient by viewModel.latestMeetingByClient.collectAsState()

    var viewedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var scheduleToView by remember { mutableStateOf<UpcomingItem?>(null) }
    var scheduleToEdit by remember { mutableStateOf<UpcomingItem?>(null) }
    var scheduleToDelete by remember { mutableStateOf<UpcomingItem?>(null) }

    val today = LocalDate.now()
    val futureSchedules = allSchedules.filter { !it.start.toLocalDate().isBefore(today) }
    val todaySchedules = allSchedules.filter { it.start.toLocalDate() == today }
    val upcomingSchedules = allSchedules.filter { it.start.toLocalDate().isAfter(today) }

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
            TabTopBar(
                icon = Icons.Filled.CalendarMonth,
                title = "予定表",
                onHome = onHome,
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            AppIcons.CalendarPlus,
                            contentDescription = "予定を追加",
                            tint = CreateActionBlue
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
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

            val date = selectedDate
            if (date != null) {
                // 日付を選択中: その日だけを表示(過去も)。
                item(key = "list_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${date.monthValue}月${date.dayOfMonth}日の予定 (${filteredItems.size}件)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        TextButton(onClick = { selectedDate = null }) { Text("すべて表示") }
                    }
                }
                item(key = "sel_list") {
                    ScheduleListPanel(
                        items = filteredItems,
                        emptyText = "予定はありません。右上の＋から追加できます。",
                        onOpen = { scheduleToView = it }
                    )
                }
            } else {
                // 未選択: 本日の予定 → これからの予定 の2枠。
                item(key = "today_header") {
                    SectionRule("本日の予定 ・ ${todaySchedules.size}件")
                }
                item(key = "today_list") {
                    ScheduleListPanel(
                        items = todaySchedules,
                        emptyText = "本日の予定はありません。",
                        onOpen = { scheduleToView = it }
                    )
                }
                item(key = "upcoming_header") {
                    SectionRule(
                        "これからの予定 ・ ${upcomingSchedules.size}件",
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item(key = "upcoming_list") {
                    ScheduleListPanel(
                        items = upcomingSchedules,
                        emptyText = "予定はありません。右上の＋から追加できます。",
                        onOpen = { scheduleToView = it }
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

    scheduleToView?.let { item ->
        ScheduleDetailDialog(
            item = item,
            latestMeetingId = latestMeetingByClient[item.clientId],
            onOpenClient = { scheduleToView = null; onOpenClient(item.clientId) },
            onOpenMeeting = { mid -> scheduleToView = null; onOpenMeeting(mid) },
            onEdit = { scheduleToView = null; scheduleToEdit = item },
            onDelete = { scheduleToView = null; scheduleToDelete = item },
            onDismiss = { scheduleToView = null }
        )
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

/**
 * 予定の詳細ダイアログ。予定表の行タップ / ホームの「直近の予定」から開く。
 * [onEdit] / [onDelete] を渡さない(null)と編集・削除ボタンを出さない(ホームでは閲覧のみ)。
 */
@Composable
fun ScheduleDetailDialog(
    item: UpcomingItem,
    latestMeetingId: Long?,
    onOpenClient: () -> Unit,
    onOpenMeeting: (Long) -> Unit,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val d = item.start
    val dow = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.JAPAN)
    val whenStr = "${d.year}年${d.monthValue}月${d.dayOfMonth}日($dow) " +
        if (item.allDay) "終日" else "%02d:%02d".format(d.hour, d.minute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title.ifBlank { "打ち合わせ" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (item.phase != null) {
                    Spacer(Modifier.width(6.dp))
                    DealPhaseChip(phase = item.phase)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailField("日時", whenStr)
                DetailField("クライアント", item.clientName, onClick = onOpenClient)
                DetailField("参加者", item.participants.ifBlank { null })
                DetailField("場所", item.location?.takeIf { it.isNotBlank() })
                DetailField("会議URL", item.meetingUrl?.takeIf { it.isNotBlank() }, maxLines = 1, onClick = {
                    item.meetingUrl?.let { url ->
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())
                            )
                        }
                    }
                })
                DetailField("メモ", item.note.ifBlank { null }, maxLines = 4)

                HorizontalDivider(modifier = Modifier.padding(top = 2.dp))

                if (latestMeetingId != null) {
                    DialogLinkRow("前回の会議（アーカイブ）を開く") { onOpenMeeting(latestMeetingId) }
                }
                DialogLinkRow("カレンダーアプリに追加") {
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
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
        dismissButton = {
            if (onEdit != null || onDelete != null) {
                Row {
                    onEdit?.let { TextButton(onClick = it) { Text("編集") } }
                    onDelete?.let { TextButton(onClick = it) { Text("削除") } }
                }
            }
        }
    )
}

/**
 * 予定詳細の1項目。ラベルを上、値を下に積む(幅の折り返し対策)。
 * [value] が null なら目立たない色で「なし」。[onClick] があれば値はリンク表示。
 */
@Composable
private fun DetailField(
    label: String,
    value: String?,
    maxLines: Int = 2,
    onClick: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (value == null) {
            Text(
                "なし",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (onClick != null) FontWeight.Medium else null,
                color = if (onClick != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (onClick != null) TextDecoration.Underline else null,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            )
        }
    }
}

@Composable
private fun DialogLinkRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/**
 * 予定の一覧パネル。ホームの「直近の予定」(`UpcomingBoard`)と同じ見た目
 * (`surfaceContainer` の角丸パネル + `UpcomingRow` + 区切り線)。
 */
@Composable
private fun ScheduleListPanel(
    items: List<UpcomingItem>,
    emptyText: String,
    onOpen: (UpcomingItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp)
    ) {
        if (items.isEmpty()) {
            Text(
                emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 14.dp)
            )
        } else {
            items.forEachIndexed { index, item ->
                UpcomingRow(item = item, onClick = { onOpen(item) })
                if (index != items.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** 「——— 見出し ———」の中央ラベル区切り(本日 / これからの予定 の仕切り)。 */
@Composable
private fun SectionRule(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
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
    val gridLine = MaterialTheme.colorScheme.outlineVariant
    val todayCornerColor = MaterialTheme.colorScheme.error
    val markColor = MaterialTheme.colorScheme.primary

    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline)
    ) {
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

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
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
            HorizontalDivider(color = gridLine)

            val firstDay = month.atDay(1)
            // 日曜始まり: 月曜=1...土曜=6、日曜=7 なので mod 7 で日曜=0 に揃える
            val leadingBlanks = firstDay.dayOfWeek.value % 7
            val daysInMonth = month.lengthOfMonth()
            val rows = (leadingBlanks + daysInMonth + 6) / 7

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNum = row * 7 + col - leadingBlanks + 1
                        val valid = dayNum in 1..daysInMonth
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.4f)
                                .border(0.5.dp, gridLine)
                                .then(
                                    if (valid) Modifier.clickable { onDateClick(month.atDay(dayNum)) }
                                    else Modifier
                                )
                        ) {
                            if (valid) {
                                val date = month.atDay(dayNum)
                                val isSelected = date == selectedDate
                                val isToday = date == LocalDate.now()
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

                                // 本日: 左上角を赤い三角で塗る(丸枠は廃止)。
                                if (isToday && !isSelected) {
                                    androidx.compose.foundation.Canvas(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .size(12.dp)
                                    ) {
                                        drawPath(
                                            androidx.compose.ui.graphics.Path().apply {
                                                moveTo(0f, 0f)
                                                lineTo(size.width, 0f)
                                                lineTo(0f, size.height)
                                                close()
                                            },
                                            color = todayCornerColor
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .offset(y = (-3).dp)
                                        .size(24.dp)
                                        .then(
                                            if (isSelected)
                                                Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else null,
                                        color = numberColor
                                    )
                                }

                                // 予定/期限がある日: 数字から離して下寄せの点。
                                if (hasMark) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 4.dp)
                                            .size(5.dp)
                                            .background(markColor, CircleShape)
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
