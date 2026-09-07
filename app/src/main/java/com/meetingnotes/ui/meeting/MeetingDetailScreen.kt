package com.meetingnotes.ui.meeting

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Drafts
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.meetingnotes.ads.BannerAdFormat
import com.meetingnotes.ads.BannerAdView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.data.MeetingRepository
import android.widget.Toast
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.export.CsvExporter
import com.meetingnotes.export.ExcelExporter
import com.meetingnotes.export.IcsExporter
import com.meetingnotes.export.MarkdownExporter
import com.meetingnotes.export.PdfExporter
import com.meetingnotes.export.SaveFileHelper
import com.meetingnotes.export.ShareFileHelper
import com.meetingnotes.export.Watermark
import com.meetingnotes.export.WatermarkPosition
import com.meetingnotes.export.WordExporter
import com.meetingnotes.billing.ProAccess
import com.meetingnotes.ui.common.ConfirmDialog
import com.meetingnotes.ui.common.DealPhaseChip
import com.meetingnotes.ui.common.DealPhasePickerDialog
import com.meetingnotes.ui.common.ProGate
import com.meetingnotes.ui.common.ProPaywallDialog
import com.meetingnotes.ui.common.TextInputDialog
import com.meetingnotes.ui.common.effectivePhase
import com.meetingnotes.ui.theme.OnProGold
import com.meetingnotes.ui.theme.ProGold
import com.meetingnotes.ui.common.NextMeetingDateTimeDialog
import com.meetingnotes.ui.common.meetingSummarySections
import com.meetingnotes.data.model.NextMeetingTime
import com.meetingnotes.util.CalendarIntent
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val meetingDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

/** 商談の管理番号。録音開始日時を並べた数字(例: 202609071200)。保存はせず recordedAt から都度生成する。 */
private val meetingNoFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmm")

private fun meetingNo(recordedAt: Long): String =
    Instant.ofEpochMilli(recordedAt).atZone(ZoneId.systemDefault()).format(meetingNoFormatter)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingDetailScreen(
    repository: MeetingRepository,
    meetingId: Long,
    onBack: () -> Unit,
    onMeetingDeleted: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: MeetingDetailViewModel = viewModel(
        factory = MeetingDetailViewModel.factory(application, repository, meetingId)
    )
    val meeting by viewModel.meeting.collectAsState()
    val todos by viewModel.todos.collectAsState()
    val followupState by viewModel.followupState.collectAsState()
    val clientName by viewModel.clientName.collectAsState()
    var showFollowup by remember { mutableStateOf(false) }
    var showNextMeetingPicker by remember { mutableStateOf(false) }
    var exportAction by remember { mutableStateOf<ExportAction?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPhasePicker by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    // 形式ごとに MIME が異なるため CreateDocument は汎用指定。拡張子はファイル名で伝わる。
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val file = pendingSaveFile
        if (uri != null && file != null) {
            SaveFileHelper.copyToUri(context, file, uri)
        }
        pendingSaveFile = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meeting?.title ?: "商談詳細") },
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
                            text = { Text("タイトルを変更") },
                            onClick = {
                                menuExpanded = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("削除") },
                            onClick = {
                                menuExpanded = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        val current = meeting
        if (current == null) {
            Text(text = "読み込み中...", modifier = Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val startedAt = Instant.ofEpochMilli(current.recordedAt).atZone(ZoneId.systemDefault())
                val endedAtText = current.endedAt?.let {
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "No. ${meetingNo(current.recordedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "録音: ${startedAt.format(meetingDateTimeFormatter)}" +
                                (endedAtText?.let { " 〜 $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    DealPhaseChip(
                        phase = current.effectivePhase(),
                        onClick = { showPhasePicker = true }
                    )
                }
            }

            meetingSummarySections(
                summaryText = current.summary,
                decisions = current.decisions,
                concerns = current.concerns
            )

            item { Text(text = "ToDo", style = MaterialTheme.typography.titleMedium) }
            if (todos.isEmpty()) {
                item { Text("(なし)") }
            } else {
                items(todos, key = { it.id }) { todo ->
                    TodoRow(todo = todo, onToggle = { viewModel.toggleTodo(todo) })
                }
            }

            item {
                val parsed = NextMeetingTime.parse(current.nextMeetingDate)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("次回打ち合わせ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        current.nextMeetingDate
                            ?: current.nextMeetingOriginalText
                            ?: "(未定)"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showNextMeetingPicker = true }) {
                            Text(if (parsed != null) "日程を変更" else "日程を設定")
                        }
                        if (parsed != null) {
                            TextButton(onClick = {
                                CalendarIntent.add(
                                    context = context,
                                    title = "${clientName ?: ""}との打ち合わせ".trim().ifEmpty { current.title },
                                    start = parsed.start,
                                    allDay = parsed.allDay,
                                    description = "商談メモ「${current.title}」の次回打ち合わせ"
                                )
                            }) { Text("カレンダーに追加") }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        showFollowup = true
                        viewModel.openFollowup()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Drafts, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("フォローアップの下書き")
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BannerAdView(format = BannerAdFormat.MediumRectangle)
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "議事録エクスポート", style = MaterialTheme.typography.titleMedium)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { exportAction = ExportAction.SHARE },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("共有する")
                        }
                        Button(
                            onClick = { exportAction = ExportAction.SAVE },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("デバイスに保存")
                        }
                    }
                }
            }
        }
    }

    exportAction?.let { action ->
        fun deliver(file: File, mimeType: String, shareTitle: String) {
            when (action) {
                ExportAction.SHARE -> ShareFileHelper.shareFile(context, file, mimeType, shareTitle)
                ExportAction.SAVE -> {
                    pendingSaveFile = file
                    saveLauncher.launch(file.name)
                }
            }
        }
        ExportOptionsDialog(
            action = action,
            icsAvailable = IcsExporter.hasUsableDate(meeting?.nextMeetingDate),
            onDismiss = { exportAction = null },
            onExport = { format, watermark, password ->
                exportAction = null
                when (format) {
                    ExportFormat.PDF -> viewModel.exportPdf(
                        watermark = watermark,
                        password = password,
                        onReady = { deliver(it, PdfExporter.MIME_TYPE, "商談メモをPDFで共有") },
                        onError = { message -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
                    )
                    ExportFormat.WORD -> viewModel.exportWord {
                        deliver(it, WordExporter.MIME_TYPE, "商談メモをWordで共有")
                    }
                    ExportFormat.MARKDOWN -> viewModel.exportMarkdown {
                        deliver(it, MarkdownExporter.MIME_TYPE, "商談メモをMarkdownで共有")
                    }
                    ExportFormat.EXCEL -> viewModel.exportExcel {
                        deliver(it, ExcelExporter.MIME_TYPE, "商談メモをExcelで共有")
                    }
                    ExportFormat.CSV -> viewModel.exportCsv {
                        deliver(it, CsvExporter.MIME_TYPE, "ToDo を CSV で共有")
                    }
                    ExportFormat.ICS -> viewModel.exportIcs(
                        onReady = { deliver(it, IcsExporter.MIME_TYPE, "次回打ち合わせをカレンダーに追加") },
                        onNoDate = {
                            Toast.makeText(
                                context,
                                "次回打ち合わせの日時が未定のため、カレンダー用ファイルを作成できません。",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        )
    }

    if (showRenameDialog) {
        TextInputDialog(
            title = "商談タイトルを変更",
            label = "タイトル",
            initialValue = meeting?.title.orEmpty(),
            confirmLabel = "変更",
            onDismiss = { showRenameDialog = false },
            onConfirm = { title ->
                viewModel.renameTitle(title)
                showRenameDialog = false
            }
        )
    }

    if (showNextMeetingPicker) {
        val parsedNext = NextMeetingTime.parse(meeting?.nextMeetingDate)
        NextMeetingDateTimeDialog(
            initial = parsedNext?.start,
            initialHasTime = parsedNext?.allDay == false,
            onDismiss = { showNextMeetingPicker = false },
            onConfirm = { dateTime, hasTime ->
                viewModel.setNextMeetingDate(NextMeetingTime.toIso(dateTime, includeTime = hasTime))
                showNextMeetingPicker = false
            }
        )
    }

    if (showPhasePicker) {
        DealPhasePickerDialog(
            current = meeting?.effectivePhase(),
            onDismiss = { showPhasePicker = false },
            onSelect = { phase ->
                viewModel.setPhase(phase)
                showPhasePicker = false
            }
        )
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = "商談を削除",
            text = "「${meeting?.title}」を削除します。元に戻せません。",
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                viewModel.deleteMeeting(onMeetingDeleted)
            }
        )
    }

    if (showFollowup) {
        FollowupDialog(
            state = followupState,
            onGenerate = { viewModel.generateFollowup() },
            onShare = { text ->
                ShareFileHelper.sharePlainText(context, text, "フォローアップを共有")
            },
            onDismiss = {
                showFollowup = false
                viewModel.clearFollowup()
            }
        )
    }
}

@Composable
private fun FollowupDialog(
    state: FollowupState,
    onGenerate: () -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    fun copy(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("フォローアップ", text))
        Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("フォローアップの下書き") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (state) {
                    FollowupState.Idle -> Unit
                    FollowupState.Loading -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("下書きを作成しています…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    FollowupState.Empty -> {
                        Text(
                            "この商談には下書きがありません" +
                                "(以前に録音した商談、またはサーバー更新前の商談)。" +
                                "1回だけ作成できます。作成後は再作成できません。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
                            Text("下書きを作成する")
                        }
                    }
                    is FollowupState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onGenerate) { Text("再試行") }
                    }
                    is FollowupState.Ready -> {
                        Text(
                            "商談内容から自動生成した下書きです。必要に応じて編集してお使いください。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SelectionContainer {
                            Text(state.text, style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { copy(state.text) }) { Text("コピー") }
                            TextButton(onClick = { onShare(state.text) }) { Text("共有") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } }
    )
}

private enum class ExportAction { SHARE, SAVE }
private enum class ExportFormat { PDF, WORD, MARKDOWN, EXCEL, CSV, ICS }

private data class FormatGroup(val heading: String, val items: List<Pair<ExportFormat, String>>)

private val formatGroups = listOf(
    FormatGroup(
        "議事録ぜんぶ",
        listOf(
            ExportFormat.PDF to "PDF",
            ExportFormat.WORD to "Word",
            ExportFormat.MARKDOWN to "Markdown"
        )
    ),
    FormatGroup(
        "ToDoリストだけ",
        listOf(
            ExportFormat.EXCEL to "Excel",
            ExportFormat.CSV to "CSV"
        )
    ),
    FormatGroup(
        "次回打ち合わせ",
        listOf(ExportFormat.ICS to "カレンダー(.ics)")
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportOptionsDialog(
    action: ExportAction,
    icsAvailable: Boolean,
    onDismiss: () -> Unit,
    onExport: (ExportFormat, Watermark?, String?) -> Unit
) {
    val groups = if (icsAvailable) formatGroups else formatGroups.filter { it.items.none { i -> i.first == ExportFormat.ICS } }
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var paywallFeature by remember { mutableStateOf<String?>(null) }
    var watermarkEnabled by remember { mutableStateOf(false) }
    var watermarkText by remember { mutableStateOf("SAMPLE") }
    var position by remember { mutableStateOf(WatermarkPosition.CENTER) }
    var positionMenuExpanded by remember { mutableStateOf(false) }
    var passwordEnabled by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    // Pro 未加入で透かしを外せない場合は常に透かしあり。それ以外はトグルに従う。
    val watermarkForced = format == ExportFormat.PDF && ProAccess.shouldLock
    val watermarkActive = format == ExportFormat.PDF && (watermarkForced || watermarkEnabled)

    val positionOptions = listOf(
        WatermarkPosition.CENTER to "中央(斜め)",
        WatermarkPosition.TOP_LEFT to "左上",
        WatermarkPosition.TOP_RIGHT to "右上",
        WatermarkPosition.BOTTOM_LEFT to "左下",
        WatermarkPosition.BOTTOM_RIGHT to "右下"
    )

    fun currentWatermark(): Watermark? =
        if (watermarkActive)
            Watermark(text = watermarkText.ifBlank { "SAMPLE" }, position = position)
        else null

    // パスワード保護は Pro 限定。未加入では設定できない(常に null)。
    val passwordActive = format == ExportFormat.PDF && passwordEnabled && !ProAccess.shouldLock
    fun currentPassword(): String? = if (passwordActive && password.isNotBlank()) password else null
    val canExport = !passwordActive || password.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (action == ExportAction.SHARE) "共有する" else "デバイスに保存") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groups.forEach { group ->
                    Text(group.heading, style = MaterialTheme.typography.labelMedium)
                    group.items.chunked(2).forEach { rowItems ->
                        val rowHasLocked =
                            ProAccess.shouldLock && rowItems.any { it.first != ExportFormat.PDF }
                        Row(
                            // ロック中はバッジが上にはみ出すぶんの余白を確保(PDFと高さを揃える)
                            modifier = if (rowHasLocked) Modifier.padding(top = 12.dp) else Modifier,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (fmt, label) ->
                                val proLocked = fmt != ExportFormat.PDF && ProAccess.shouldLock
                                ProGate(
                                    locked = proLocked,
                                    onLockedTap = { paywallFeature = "$label 形式での書き出し" }
                                ) {
                                    FilterChip(
                                        selected = format == fmt,
                                        enabled = !proLocked,
                                        onClick = { format = fmt },
                                        label = { Text(label) },
                                        border = if (proLocked) {
                                            BorderStroke(2.dp, ProGold)
                                        } else {
                                            FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = format == fmt
                                            )
                                        },
                                        colors = if (proLocked) {
                                            FilterChipDefaults.filterChipColors(
                                                disabledContainerColor = ProGold.copy(alpha = 0.18f),
                                                disabledLabelColor = OnProGold.copy(alpha = 0.75f)
                                            )
                                        } else {
                                            FilterChipDefaults.filterChipColors()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                when (format) {
                    ExportFormat.EXCEL, ExportFormat.CSV -> Text(
                        "ToDo 一覧(タスク / 担当 / 期限 / 完了)だけを書き出します。" +
                            if (format == ExportFormat.CSV) "タスク管理ツールへの取り込み向けです。" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                    ExportFormat.ICS -> Text(
                        "「次回打ち合わせ」の日時をカレンダーアプリに登録できます。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> {}
                }

                if (format == ExportFormat.PDF) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (watermarkForced) {
                        ProGate(
                            locked = true,
                            onLockedTap = { paywallFeature = "透かしなしでの書き出し" },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                        ) {
                            Surface(
                                color = ProGold.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, ProGold),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("透かしを入れる", color = OnProGold.copy(alpha = 0.8f))
                                    Switch(checked = true, onCheckedChange = {}, enabled = false)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("透かしを入れる")
                            Switch(
                                checked = watermarkEnabled,
                                onCheckedChange = { watermarkEnabled = it }
                            )
                        }
                    }
                }

                if (watermarkActive) {
                    OutlinedTextField(
                        value = watermarkText,
                        onValueChange = { watermarkText = it },
                        label = { Text("透かし文字") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("位置", style = MaterialTheme.typography.bodyMedium)
                    ExposedDropdownMenuBox(
                        expanded = positionMenuExpanded,
                        onExpandedChange = { positionMenuExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = positionOptions.first { it.first == position }.second,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = positionMenuExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = positionMenuExpanded,
                            onDismissRequest = { positionMenuExpanded = false }
                        ) {
                            positionOptions.forEach { (value, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        position = value
                                        positionMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    WatermarkPositionPreview(
                        position = position,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (format == ExportFormat.PDF) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    ProGate(
                        locked = ProAccess.shouldLock,
                        onLockedTap = { paywallFeature = "PDFのパスワード保護" },
                        modifier = if (ProAccess.shouldLock) {
                            Modifier.fillMaxWidth().padding(top = 12.dp)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("パスワードを設定する")
                            Switch(
                                checked = passwordEnabled && !ProAccess.shouldLock,
                                enabled = !ProAccess.shouldLock,
                                onCheckedChange = { passwordEnabled = it }
                            )
                        }
                    }

                    if (passwordActive) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("パスワード") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            supportingText = if (password.isBlank()) {
                                { Text("パスワードを入力してください") }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "このパスワードを知っている人だけがPDFを開けます。アプリ側には保存されないため、共有先に別途伝えてください。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onExport(format, currentWatermark(), currentPassword()) },
                enabled = canExport
            ) {
                Text(if (action == ExportAction.SHARE) "共有する" else "保存する")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )

    paywallFeature?.let { feature ->
        ProPaywallDialog(featureName = feature, onDismiss = { paywallFeature = null })
    }
}

/** 透かしの配置イメージをページの縮小プレビューで示す。 */
@Composable
private fun WatermarkPositionPreview(position: WatermarkPosition, modifier: Modifier = Modifier) {
    val alignment = when (position) {
        WatermarkPosition.CENTER -> Alignment.Center
        WatermarkPosition.TOP_LEFT -> Alignment.TopStart
        WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
        WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
        WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
    }

    Column {
        Text("配置プレビュー", style = MaterialTheme.typography.labelSmall)
        Box(
            modifier = modifier
                .size(width = 90.dp, height = 127.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Text(
                text = "SAMPLE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier
                    .align(alignment)
                    .padding(6.dp)
                    .then(
                        if (position == WatermarkPosition.CENTER) {
                            Modifier.graphicsLayer(rotationZ = -30f)
                        } else {
                            Modifier
                        }
                    )
            )
        }
    }
}

@Composable
private fun TodoRow(todo: TodoEntity, onToggle: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = todo.isDone, onCheckedChange = { onToggle() })
        Text(
            text = "${todo.task}(担当: ${todo.assignee} / 期限: ${todo.deadline})",
            textDecoration = if (todo.isDone) TextDecoration.LineThrough else TextDecoration.None
        )
    }
}
