package com.meetingnotes.ui.recording

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.meetingnotes.ui.MeetingViewModel
import com.meetingnotes.ui.RecordingPhase

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
    val activity = LocalContext.current as Activity

    var permissionDenied by remember { mutableStateOf(false) }
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

    LaunchedEffect(clientId) {
        val hasPermission = ContextCompat.checkSelfPermission(
            activity, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.beginRecordingFlow(clientId)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val handleCancel: () -> Unit = {
        viewModel.cancelRecordingFlow()
        onCancel()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("録音") },
                navigationIcon = {
                    IconButton(onClick = handleCancel) {
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
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "録音中...",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

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

        Button(
            onClick = onStop,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(LocalContentColor.current, shape = RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text("停止", style = MaterialTheme.typography.titleLarge)
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
            Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth()) {
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
