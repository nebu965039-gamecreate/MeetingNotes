package com.meetingnotes.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.model.NextMeetingTime
import com.meetingnotes.ui.client.UpcomingItem
import com.meetingnotes.ui.common.ConfirmDialog
import com.meetingnotes.ui.common.DealPhaseChip
import com.meetingnotes.ui.common.LabeledDropdownField
import com.meetingnotes.ui.common.NextMeetingDatePickerDialog
import com.meetingnotes.ui.common.relativeDateTimeLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    repository: MeetingRepository,
    onBack: () -> Unit,
    onOpenMeeting: (Long) -> Unit
) {
    val viewModel: ScheduleViewModel = viewModel(factory = ScheduleViewModel.factory(repository))
    val items by viewModel.upcoming.collectAsState()
    val schedulableClients by viewModel.schedulableClients.collectAsState()

    var viewedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var meetingToReschedule by remember { mutableStateOf<UpcomingItem?>(null) }
    var meetingToClear by remember { mutableStateOf<UpcomingItem?>(null) }

    val markedDates = remember(items) { items.map { it.start.toLocalDate() }.toSet() }
    val filteredItems = selectedDate?.let { d -> items.filter { it.start.toLocalDate() == d } } ?: items

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "予定表",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
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
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "予定を追加")
            }
        },
        bottomBar = { BannerAdView(Modifier.navigationBarsPadding()) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            "予定一覧 (全${items.size}件)"
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
                        "次回打ち合わせが設定されている商談はありません。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredItems, key = { it.meetingId }) { item ->
                    ScheduleRow(
                        item = item,
                        onClick = { onOpenMeeting(item.meetingId) },
                        onReschedule = { meetingToReschedule = item },
                        onDelete = { meetingToClear = item }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddScheduleDialog(
            clients = schedulableClients,
            onDismiss = { showAddDialog = false },
            onConfirm = { clientId, date ->
                viewModel.scheduleForClient(clientId, NextMeetingTime.toIso(date.atStartOfDay(), includeTime = false))
                showAddDialog = false
            }
        )
    }

    meetingToReschedule?.let { item ->
        NextMeetingDatePickerDialog(
            initialDate = item.start.toLocalDate(),
            onDismiss = { meetingToReschedule = null },
            onConfirm = { date ->
                viewModel.rescheduleMeeting(item.meetingId, NextMeetingTime.toIso(date.atStartOfDay(), includeTime = false))
                meetingToReschedule = null
            }
        )
    }

    meetingToClear?.let { item ->
        ConfirmDialog(
            title = "予定を削除",
            text = "「${item.client.name}」の次回打ち合わせの予定を削除します。商談の記録は削除されません。",
            onDismiss = { meetingToClear = null },
            onConfirm = {
                viewModel.clearSchedule(item.meetingId)
                meetingToClear = null
            }
        )
    }
}

@Composable
private fun ScheduleRow(
    item: UpcomingItem,
    onClick: () -> Unit,
    onReschedule: () -> Unit,
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
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    relativeDateTimeLabel(item.start, item.allDay),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    item.client.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.phase != null) {
                    DealPhaseChip(phase = item.phase)
                    Spacer(Modifier.width(4.dp))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("日程を変更") },
                            onClick = {
                                menuExpanded = false
                                onReschedule()
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
}

/** シンプルな月カレンダー。予定がある日には下に丸印を付け、タップで一覧を絞り込む。 */
@Composable
private fun MonthCalendar(
    month: YearMonth,
    markedDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    onMonthChange: (YearMonth) -> Unit,
    onDateClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val firstDay = month.atDay(1)
            // 日曜始まり: 月曜=1...土曜=6、日曜=7 なので mod 7 で日曜=0 に揃える
            val leadingBlanks = firstDay.dayOfWeek.value % 7
            val daysInMonth = month.lengthOfMonth()
            val totalCells = leadingBlanks + daysInMonth
            val rows = (totalCells + 6) / 7

            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNum = cellIndex - leadingBlanks + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (dayNum in 1..daysInMonth) {
                                val date = month.atDay(dayNum)
                                val isSelected = date == selectedDate
                                val hasMark = date in markedDates
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { onDateClick(date) },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
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
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onPrimary
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
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
private fun AddScheduleDialog(
    clients: List<Pair<Long, String>>,
    onDismiss: () -> Unit,
    onConfirm: (clientId: Long, date: LocalDate) -> Unit
) {
    var clientId by remember { mutableStateOf(clients.firstOrNull()?.first) }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(1)) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("予定を追加") },
        text = {
            if (clients.isEmpty()) {
                Text("商談記録のあるクライアントがいません。まず録音してから設定できます。")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledDropdownField(
                        label = "クライアント",
                        options = clients,
                        selected = clientId ?: clients.first().first,
                        onSelect = { clientId = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { showDatePicker = true }) {
                        Text("日程: ${date.monthValue}月${date.dayOfMonth}日")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { clientId?.let { onConfirm(it, date) } },
                enabled = clients.isNotEmpty() && clientId != null
            ) { Text("追加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )

    if (showDatePicker) {
        NextMeetingDatePickerDialog(
            initialDate = date,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                date = picked
                showDatePicker = false
            }
        )
    }
}
