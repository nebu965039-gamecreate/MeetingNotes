package com.meetingnotes.ui.result

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.model.MeetingSummary
import com.meetingnotes.ui.MeetingViewModel
import com.meetingnotes.ui.SummaryUiState
import com.meetingnotes.ui.common.meetingSummarySections
import com.meetingnotes.ui.common.nextMeetingSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    viewModel: MeetingViewModel,
    onSaved: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.summaryState.collectAsState()
    val activity = LocalActivity.current as Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("要約結果") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        bottomBar = {
            if (state !is SummaryUiState.Loading) {
                BannerAdView()
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            when (val current = state) {
                is SummaryUiState.Idle -> Text("要約はまだ実行されていません。")

                is SummaryUiState.Loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text("要約中...")
                }

                is SummaryUiState.Error -> Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(text = "要約に失敗しました: ${current.message}", color = MaterialTheme.colorScheme.error)
                    Button(onClick = { viewModel.submitForSummary(activity) }) {
                        Text("再試行")
                    }
                }

                is SummaryUiState.Success -> SummaryContent(
                    summary = current.summary,
                    defaultTitle = viewModel.defaultMeetingTitle(),
                    onSave = { title -> viewModel.saveMeeting(title, onSaved) }
                )
            }
        }
    }
}

@Composable
private fun SummaryContent(summary: MeetingSummary, defaultTitle: String, onSave: (String) -> Unit) {
    var title by remember { mutableStateOf(defaultTitle) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("タイトル") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        meetingSummarySections(
            summaryText = summary.summary,
            decisions = summary.decisions.map { it.content },
            concerns = summary.concerns.map { it.content }
        )

        item { Text(text = "ToDo", style = MaterialTheme.typography.titleMedium) }
        if (summary.todos.isEmpty()) {
            item { Text("(なし)") }
        } else {
            items(summary.todos) {
                Text(text = "・${it.task}(担当: ${it.assignee} / 期限: ${it.deadline})")
            }
        }

        nextMeetingSection(summary.nextMeeting.date ?: summary.nextMeeting.originalText ?: "(未定)")

        item {
            Button(onClick = { onSave(title) }, modifier = Modifier.fillMaxWidth()) {
                Text("保存してクライアント画面に戻る")
            }
        }
    }
}
