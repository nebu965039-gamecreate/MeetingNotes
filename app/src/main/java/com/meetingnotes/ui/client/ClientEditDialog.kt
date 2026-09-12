package com.meetingnotes.ui.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.common.LabeledDropdownField

/** 流入経路のプリセット候補(自由入力も可)。 */
private val LEAD_SOURCES = listOf(
    "紹介", "Web検索", "SNS", "イベント・展示会", "既存顧客からの追加", "問い合わせフォーム", "広告", "その他"
)

/**
 * クライアント情報の編集モーダル。クライアント詳細画面「情報」タブの鉛筆FABから開く(2026-09-12、旧: 独立画面 `Routes.CLIENT_EDIT`)。
 * `ProjectFormDialog` 等と同じ AlertDialog + スクロール Column の様式に揃えている。
 */
@Composable
fun ClientEditDialog(
    repository: MeetingRepository,
    clientId: Long,
    onDismiss: () -> Unit
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
    var leadSource by remember { mutableStateOf("") }
    var referredBy by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(client) {
        val c = client ?: return@LaunchedEffect
        if (!loaded) {
            name = c.name
            email = c.email.orEmpty()
            phone = c.phone.orEmpty()
            memo = c.memo.orEmpty()
            groupId = c.groupId
            leadSource = c.leadSource.orEmpty()
            referredBy = c.referredBy.orEmpty()
            loaded = true
        }
    }

    var newContactName by remember { mutableStateOf("") }
    var newContactNote by remember { mutableStateOf("") }
    var newContactEmail by remember { mutableStateOf("") }
    var newContactPhone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("クライアント情報を編集") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                    modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp)
                )

                LabeledDropdownField(
                    label = "流入経路（任意）",
                    options = listOf<Pair<String?, String>>(null to "未設定") + LEAD_SOURCES.map { it to it },
                    selected = LEAD_SOURCES.firstOrNull { it == leadSource },
                    onSelect = { leadSource = it.orEmpty() },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = leadSource, onValueChange = { leadSource = it },
                    label = { Text("流入経路（自由記述可）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = referredBy, onValueChange = { referredBy = it },
                    label = { Text("紹介元・紹介者（任意）") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("担当者", style = MaterialTheme.typography.titleMedium)
                contacts.forEach { contact ->
                    ContactEditRow(
                        contact = contact,
                        onSave = { n, note, cEmail, cPhone ->
                            viewModel.updateContact(contact.id, n, note, cEmail, cPhone)
                        },
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
                            label = { Text("役職・部署など（任意）") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newContactEmail, onValueChange = { newContactEmail = it },
                            label = { Text("メールアドレス（任意）") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newContactPhone, onValueChange = { newContactPhone = it },
                            label = { Text("電話番号（任意）") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(
                            onClick = {
                                viewModel.addContact(newContactName, newContactNote, newContactEmail, newContactPhone)
                                newContactName = ""
                                newContactNote = ""
                                newContactEmail = ""
                                newContactPhone = ""
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    viewModel.saveInfo(
                        name, email, phone, memo, groupId,
                        leadSource.trim().ifBlank { null },
                        referredBy.trim().ifBlank { null }
                    )
                    onDismiss()
                },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } }
    )
}

@Composable
private fun ContactEditRow(
    contact: com.meetingnotes.data.local.ClientContactEntity,
    onSave: (name: String, note: String?, email: String?, phone: String?) -> Unit,
    onDelete: () -> Unit
) {
    var n by remember(contact.id) { mutableStateOf(contact.name) }
    var note by remember(contact.id) { mutableStateOf(contact.note.orEmpty()) }
    var email by remember(contact.id) { mutableStateOf(contact.email.orEmpty()) }
    var phone by remember(contact.id) { mutableStateOf(contact.phone.orEmpty()) }
    fun save() = onSave(n, note, email, phone)

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row {
                OutlinedTextField(
                    value = n, onValueChange = { n = it; save() },
                    label = { Text("氏名") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "削除")
                }
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it; save() },
                label = { Text("役職・部署など（任意）") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = email, onValueChange = { email = it; save() },
                label = { Text("メールアドレス（任意）") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone, onValueChange = { phone = it; save() },
                label = { Text("電話番号（任意）") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
