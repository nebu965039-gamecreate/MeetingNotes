package com.meetingnotes.ui.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.LaunchedEffect
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.common.LabeledDropdownField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientEditScreen(
    repository: MeetingRepository,
    clientId: Long,
    onBack: () -> Unit
) {
    val viewModel: ClientInfoViewModel =
        viewModel(factory = ClientInfoViewModel.factory(repository, clientId))
    val client by viewModel.client.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val groups by viewModel.groups.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var memo by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf<Long?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(client) {
        val c = client ?: return@LaunchedEffect
        if (!loaded) {
            name = c.name
            email = c.email.orEmpty()
            phone = c.phone.orEmpty()
            memo = c.memo.orEmpty()
            groupId = c.groupId
            loaded = true
        }
    }

    var newContactName by remember { mutableStateOf("") }
    var newContactNote by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("クライアント情報を編集") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveInfo(name, email, phone, memo, groupId)
                            onBack()
                        },
                        enabled = name.isNotBlank()
                    ) { Text("保存") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("クライアント名") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            LabeledDropdownField(
                label = "グループ",
                options = listOf<Pair<Long?, String>>(null to "未分類") + groups.map { it.id to it.name },
                selected = groupId,
                onSelect = { groupId = it },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it },
                label = { Text("メールアドレス（任意）") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it },
                label = { Text("電話番号（任意）") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = memo, onValueChange = { memo = it },
                label = { Text("備考（任意）") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
            )

            Text("担当者", style = MaterialTheme.typography.titleMedium)
            contacts.forEach { contact ->
                ContactEditRow(
                    initialName = contact.name,
                    initialNote = contact.note.orEmpty(),
                    onSave = { n, note -> viewModel.updateContact(contact.id, n, note) },
                    onDelete = { viewModel.deleteContact(contact.id) }
                )
            }

            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("担当者を追加", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = newContactName, onValueChange = { newContactName = it },
                        label = { Text("氏名") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newContactNote, onValueChange = { newContactNote = it },
                        label = { Text("役職・部署・連絡先など（任意）") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            viewModel.addContact(newContactName, newContactNote)
                            newContactName = ""
                            newContactNote = ""
                        },
                        enabled = newContactName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("追加")
                    }
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}

@Composable
private fun ContactEditRow(
    initialName: String,
    initialNote: String,
    onSave: (name: String, note: String?) -> Unit,
    onDelete: () -> Unit
) {
    var n by remember(initialName) { mutableStateOf(initialName) }
    var note by remember(initialNote) { mutableStateOf(initialNote) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row {
                OutlinedTextField(
                    value = n, onValueChange = { n = it; onSave(it, note) },
                    label = { Text("氏名") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "削除")
                }
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it; onSave(n, it) },
                label = { Text("役職・部署・連絡先など（任意）") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
