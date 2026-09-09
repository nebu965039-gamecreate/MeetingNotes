package com.meetingnotes.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.local.EmailTemplateEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailTemplatesScreen(repository: MeetingRepository, onBack: () -> Unit) {
    val viewModel: EmailTemplatesViewModel =
        viewModel(factory = EmailTemplatesViewModel.factory(repository))
    val templates by viewModel.templates.collectAsState()

    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<EmailTemplateEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("メール文面テンプレート") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Filled.Add, contentDescription = "テンプレートを追加")
            }
        },
        bottomBar = { BannerAdView(Modifier.navigationBarsPadding()) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "フォローアップの下書き画面から呼び出せます。本文に {クライアント名} / {日付} / {次回打ち合わせ} と書くと、使うときに差し込まれます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (templates.isEmpty()) {
                item {
                    Text(
                        "テンプレートはまだありません。右下の＋から追加してください。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                items(templates, key = { it.id }) { t ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { editing = t }
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(t.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                t.body,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        TemplateFormDialog(
            editing = null,
            onDismiss = { showForm = false },
            onSave = { name, body -> viewModel.add(name, body); showForm = false },
            onDelete = null
        )
    }
    editing?.let { t ->
        TemplateFormDialog(
            editing = t,
            onDismiss = { editing = null },
            onSave = { name, body -> viewModel.update(t.id, name, body); editing = null },
            onDelete = { viewModel.delete(t.id); editing = null }
        )
    }
}

@Composable
private fun TemplateFormDialog(
    editing: EmailTemplateEntity?,
    onDismiss: () -> Unit,
    onSave: (name: String, body: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var body by remember { mutableStateOf(editing?.body.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "テンプレートを追加" else "テンプレートを編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("テンプレート名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("本文") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, body) },
                enabled = name.isNotBlank() && body.isNotBlank()
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
}
