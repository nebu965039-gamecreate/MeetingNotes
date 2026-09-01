package com.meetingnotes.ui.meeting

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import com.meetingnotes.ads.BannerAdFormat
import com.meetingnotes.ads.BannerAdView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.TodoEntity
import com.meetingnotes.export.PdfExporter
import com.meetingnotes.export.SaveFileHelper
import com.meetingnotes.export.ShareFileHelper
import com.meetingnotes.export.Watermark
import com.meetingnotes.export.WatermarkPosition
import com.meetingnotes.export.WordExporter
import com.meetingnotes.ui.common.ConfirmDialog
import com.meetingnotes.ui.common.TextInputDialog
import com.meetingnotes.ui.common.meetingSummarySections
import com.meetingnotes.ui.common.nextMeetingSection
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val meetingDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")

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
    var showPdfDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }

    val pdfSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(PdfExporter.MIME_TYPE)
    ) { uri ->
        val file = pendingSaveFile
        if (uri != null && file != null) {
            SaveFileHelper.copyToUri(context, file, uri)
        }
        pendingSaveFile = null
    }

    val wordSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(WordExporter.MIME_TYPE)
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
                Text(
                    text = "録音: ${startedAt.format(meetingDateTimeFormatter)}" +
                        (endedAtText?.let { " 〜 $it" } ?: ""),
                    style = MaterialTheme.typography.bodySmall
                )
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

            nextMeetingSection(current.nextMeetingDate ?: current.nextMeetingOriginalText ?: "(未定)")

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    BannerAdView(format = BannerAdFormat.MediumRectangle)
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "議事録エクスポート", style = MaterialTheme.typography.titleMedium)

                    Button(onClick = { showPdfDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("PDF出力")
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.exportWord { file ->
                                    ShareFileHelper.shareFile(context, file, WordExporter.MIME_TYPE, "商談メモをWordで共有")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Wordで共有")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.exportWord { file ->
                                    pendingSaveFile = file
                                    wordSaveLauncher.launch("meeting_${current.id}.docx")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Wordを保存")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.buildPlainTextSummary()?.let { text ->
                                ShareFileHelper.sharePlainText(context, text, "商談メモをメールで共有")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("メール文面で共有")
                    }
                }
            }
        }
    }

    if (showPdfDialog) {
        PdfExportOptionsDialog(
            onDismiss = { showPdfDialog = false },
            onShare = { watermark ->
                showPdfDialog = false
                viewModel.exportPdf(watermark) { file ->
                    ShareFileHelper.shareFile(context, file, PdfExporter.MIME_TYPE, "商談メモをPDFで共有")
                }
            },
            onSave = { watermark ->
                showPdfDialog = false
                viewModel.exportPdf(watermark) { file ->
                    pendingSaveFile = file
                    pdfSaveLauncher.launch(file.name)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfExportOptionsDialog(
    onDismiss: () -> Unit,
    onShare: (Watermark?) -> Unit,
    onSave: (Watermark?) -> Unit
) {
    var watermarkEnabled by remember { mutableStateOf(true) }
    var watermarkText by remember { mutableStateOf("SAMPLE") }
    var position by remember { mutableStateOf(WatermarkPosition.CENTER) }
    var positionMenuExpanded by remember { mutableStateOf(false) }

    val positionOptions = listOf(
        WatermarkPosition.CENTER to "中央(斜め)",
        WatermarkPosition.TOP_LEFT to "左上",
        WatermarkPosition.TOP_RIGHT to "右上",
        WatermarkPosition.BOTTOM_LEFT to "左下",
        WatermarkPosition.BOTTOM_RIGHT to "右下"
    )

    fun currentWatermark(): Watermark? =
        if (watermarkEnabled) Watermark(text = watermarkText.ifBlank { "SAMPLE" }, position = position) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PDF出力オプション") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("透かしを入れる(無料プラン相当)")
                    Switch(checked = watermarkEnabled, onCheckedChange = { watermarkEnabled = it })
                }

                if (watermarkEnabled) {
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
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onShare(currentWatermark()) }) {
                    Text("共有")
                }
                TextButton(onClick = { onSave(currentWatermark()) }) {
                    Text("保存して終了")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
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
