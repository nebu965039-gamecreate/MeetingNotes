package com.meetingnotes.ui.recording

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.core.content.ContextCompat
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.meetingnotes.data.remote.AnthropicClient
import com.meetingnotes.ui.MeetingViewModel
import com.meetingnotes.ui.RecordingPhase
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    viewModel: MeetingViewModel,
    clientId: Long,
    onSubmitted: () -> Unit,
    onCancel: () -> Unit
) {
    val phase by viewModel.recordingPhase.collectAsState()
    val countdownSeconds by viewModel.countdownSeconds.collectAsState()
    val liveTranscript by viewModel.liveTranscript.collectAsState()
    val audioLevel by viewModel.audioLevel.collectAsState()
    val editableTranscript by viewModel.editableTranscript.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val creditBalance by viewModel.creditBalance.collectAsState()
    val isRewardedAdLoaded by viewModel.isRewardedAdLoaded.collectAsState()
    val recordingElapsedMs by viewModel.recordingElapsedMs.collectAsState()
    val activity = LocalActivity.current as Activity

    var permissionDenied by remember { mutableStateOf(false) }
    var flowStarted by remember { mutableStateOf(false) }
    var showDraftDialog by remember { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionDenied = false
            viewModel.beginRecordingFlow(clientId)
        } else {
            permissionDenied = true
        }
    }

    val startFreshRecording: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.beginRecordingFlow(clientId)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(clientId) {
        if (flowStarted) return@LaunchedEffect
        if (viewModel.pendingDraftForClient(clientId)) {
            showDraftDialog = true
        } else {
            flowStarted = true
            startFreshRecording()
        }
    }

    val handleCancel: () -> Unit = {
        viewModel.cancelRecordingFlow()
        onCancel()
    }

    val isActivelyRecording = phase == RecordingPhase.Countdown ||
        phase == RecordingPhase.Recording ||
        phase == RecordingPhase.Stopping

    BackHandler(enabled = isActivelyRecording && !showDraftDialog) { showStopConfirm = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("録音") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isActivelyRecording) showStopConfirm = true else handleCancel()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        if (permissionDenied) {
            PermissionDeniedContent(
                onRetry = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
            return@Scaffold
        }

        when (phase) {
            RecordingPhase.Countdown -> CountdownContent(
                secondsLeft = countdownSeconds,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            RecordingPhase.Recording -> RecordingContent(
                liveTranscript = liveTranscript,
                audioLevel = audioLevel,
                errorMessage = errorMessage,
                elapsedMs = recordingElapsedMs,
                onStop = { viewModel.requestStopRecording() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            RecordingPhase.Stopping -> StoppingContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            )

            RecordingPhase.Editing -> EditingContent(
                editableTranscript = editableTranscript,
                errorMessage = errorMessage,
                creditBalance = creditBalance,
                isRewardedAdLoaded = isRewardedAdLoaded,
                onTranscriptChange = { viewModel.updateEditableTranscript(it) },
                onRevert = { viewModel.revertToOriginalTranscript() },
                onSubmit = {
                    viewModel.submitForSummary(activity)
                    onSubmitted()
                },
                onWatchAd = { viewModel.watchRewardedAd(activity) },
                onCancel = handleCancel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
            )
        }
    }

    if (showDraftDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("前回の録音が途中です") },
            text = { Text("停止済みの文字起こしが保存されています。続きを編集しますか?") },
            confirmButton = {
                TextButton(onClick = {
                    showDraftDialog = false
                    flowStarted = true
                    viewModel.restoreDraft()
                }) { Text("続きを編集する") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDraftDialog = false
                    flowStarted = true
                    viewModel.discardDraft()
                    startFreshRecording()
                }) { Text("破棄して新しく録音") }
            }
        )
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("録音を中止しますか?") },
            text = { Text("ここまでの文字起こしは下書きとして保存され、あとで一覧から再開できます。") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    handleCancel()
                }) { Text("中止する") }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) { Text("録音を続ける") }
            }
        )
    }
}

/** 1回の要約の上限(約60〜80分)に近づいてきたら、録音中の画面で区切りを促す目安。 */
private const val RECORDING_LENGTH_WARNING_MS = 50 * 60 * 1000L

private fun formatElapsed(elapsedMs: Long): String {
    val totalSeconds = elapsedMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun PermissionDeniedContent(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "録音にはマイクの許可が必要です。",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = "設定アプリからこのアプリのマイク権限を許可してください。",
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        Button(onClick = onRetry) {
            Text("もう一度許可をリクエスト")
        }
    }
}

@Composable
private fun CountdownContent(secondsLeft: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = secondsLeft.toString(),
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "まもなく録音を開始します",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun RecordingContent(
    liveTranscript: String,
    audioLevel: Float,
    errorMessage: String?,
    elapsedMs: Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "録音中... ${formatElapsed(elapsedMs)}",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (elapsedMs >= RECORDING_LENGTH_WARNING_MS) {
            Text(
                text = "長時間の録音になっています。1回の要約には上限があるため、" +
                    "区切りのよいところで一度停止することをおすすめします。",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        AudioLevelVisualizer(level = audioLevel, modifier = Modifier.fillMaxWidth())

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            text = liveTranscript.ifBlank { "(話し始めると文字起こしが表示されます)" },
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        SlideToStop(onStop = onStop)
    }
}

/**
 * 誤タップで録音が止まらないよう、つまみを右端までスライドして初めて停止する操作。
 */
@Composable
private fun SlideToStop(onStop: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val trackHeight = 64.dp
    val thumbSize = 56.dp
    val innerPadding = 4.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(trackHeight / 2))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        val maxOffset = with(density) {
            (maxWidth - thumbSize - innerPadding * 2).toPx().coerceAtLeast(1f)
        }
        var offsetX by remember { mutableFloatStateOf(0f) }
        val progress = (offsetX / maxOffset).coerceIn(0f, 1f)

        Text(
            text = "スライドして停止  →",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.graphicsLayer { alpha = 1f - progress }
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .padding(innerPadding)
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(0f, maxOffset)
                    },
                    onDragStopped = {
                        if (offsetX >= maxOffset * 0.8f) {
                            offsetX = maxOffset
                            onStop()
                        } else {
                            Animatable(offsetX).animateTo(0f) { offsetX = value }
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(MaterialTheme.colorScheme.onPrimary, RoundedCornerShape(2.dp))
            )
        }
    }
}

/** SpeechRecognizerのonRmsChanged(dBレベル)を簡易的なスペクトラム風バーで可視化する。 */
@Composable
private fun AudioLevelVisualizer(level: Float, modifier: Modifier = Modifier) {
    val barCount = 24
    val samples = remember { mutableStateListOf<Float>().apply { repeat(barCount) { add(0.05f) } } }

    LaunchedEffect(level) {
        val normalized = (level / 10f).coerceIn(0.05f, 1f)
        samples.add(normalized)
        if (samples.size > barCount) samples.removeAt(0)
    }

    val barColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier.height(64.dp)
    ) {
        val barWidth = size.width / (barCount * 1.6f)
        val gap = barWidth * 0.6f
        samples.forEachIndexed { index, value ->
            val barHeight = size.height * value
            val x = index * (barWidth + gap)
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

@Composable
private fun StoppingContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("停止中...", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun EditingContent(
    editableTranscript: String,
    errorMessage: String?,
    creditBalance: Int,
    isRewardedAdLoaded: Boolean,
    onTranscriptChange: (String) -> Unit,
    onRevert: () -> Unit,
    onSubmit: () -> Unit,
    onWatchAd: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "録音を停止しました。内容を確認・修正してください。",
            style = MaterialTheme.typography.titleMedium
        )

        errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }

        OutlinedTextField(
            value = editableTranscript,
            onValueChange = onTranscriptChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            label = { Text("文字起こしテキスト(誤字修正可)") }
        )

        val isOverLimit = editableTranscript.length > AnthropicClient.MAX_TRANSCRIPT_CHARS
        Text(
            text = "文字数: ${editableTranscript.length} / ${AnthropicClient.MAX_TRANSCRIPT_CHARS}",
            style = MaterialTheme.typography.bodySmall,
            color = if (isOverLimit) MaterialTheme.colorScheme.error else LocalContentColor.current
        )
        if (isOverLimit) {
            Text(
                text = AnthropicClient.TRANSCRIPT_TOO_LONG_MESSAGE,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onRevert) {
                Text("元に戻す")
            }
        }

        Text(text = "残りクレジット: ${creditBalance}回")

        if (creditBalance > 0) {
            Button(onClick = onSubmit, enabled = !isOverLimit, modifier = Modifier.fillMaxWidth()) {
                Text("この内容で要約する")
            }
        } else {
            Button(
                onClick = onWatchAd,
                enabled = isRewardedAdLoaded,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRewardedAdLoaded) "広告を見てクレジットを獲得" else "広告を準備中...")
            }
        }

        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("キャンセル")
        }
    }
}
