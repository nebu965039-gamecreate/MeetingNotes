package com.meetingnotes.ui.client

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.ui.common.DealPhaseChip

/**
 * クライアント情報の閲覧表示。クライアント詳細画面の「情報」タブで使う。
 * 編集は [onEdit](→ `ClientEditScreen`)へ。
 */
@Composable
fun ClientInfoContent(
    repository: MeetingRepository,
    clientId: Long,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: ClientInfoViewModel =
        viewModel(factory = ClientInfoViewModel.factory(repository, clientId))
    val client by viewModel.client.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val phase by viewModel.latestPhase.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val projects by viewModel.projects.collectAsState()

    val c = client
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            FilledTonalButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("情報を編集")
            }
        }

        ElevatedCard {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoRow("クライアント名", c?.name)
                HorizontalDivider()
                InfoRow("グループ", groups.firstOrNull { it.id == c?.groupId }?.name ?: "未分類")
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "現在のステータス",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(120.dp)
                    )
                    if (phase != null) DealPhaseChip(phase = phase)
                    else Text("—", style = MaterialTheme.typography.bodyLarge)
                }
                HorizontalDivider()
                InfoRow("メールアドレス", c?.email)
                HorizontalDivider()
                InfoRow("電話番号", c?.phone)
            }
        }

        if (projects.isNotEmpty()) {
            Text("案件", style = MaterialTheme.typography.titleMedium)
            projects.forEach { p ->
                val cur = com.meetingnotes.util.Currency.of(p.currency)
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            com.meetingnotes.data.model.DealPhase.fromWire(p.phase)?.let {
                                Spacer(Modifier.width(6.dp))
                                DealPhaseChip(phase = it)
                            }
                        }
                        Text(
                            "見積 " + com.meetingnotes.util.formatMoney(p.estimatedAmount, cur) +
                                " ・ 成約 " + com.meetingnotes.util.formatMoney(p.wonAmount, cur),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            // 通貨ごとの合計
            projects.groupBy { it.currency }.forEach { (code, list) ->
                val cur = com.meetingnotes.util.Currency.of(code)
                val est = list.mapNotNull { it.estimatedAmount }.sum()
                val won = list.mapNotNull { it.wonAmount }.sum()
                Text(
                    "合計 (${cur.code}): 見積 ${com.meetingnotes.util.formatMoney(est, cur)} ・ 成約 ${com.meetingnotes.util.formatMoney(won, cur)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text("担当者", style = MaterialTheme.typography.titleMedium)
        if (contacts.isEmpty()) {
            Text(
                "登録されていません。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            contacts.forEach { contact ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            contact.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        contact.note?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SelectionContainer {
                            Column {
                                contact.email?.takeIf { it.isNotBlank() }?.let {
                                    Text("✉ $it", style = MaterialTheme.typography.bodyMedium)
                                }
                                contact.phone?.takeIf { it.isNotBlank() }?.let {
                                    Text("☎ $it", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }

        Text("備考", style = MaterialTheme.typography.titleMedium)
        SelectionContainer {
            Text(
                c?.memo?.takeIf { it.isNotBlank() } ?: "（なし）",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.width(1.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp)
        )
        SelectionContainer {
            Text(
                value?.takeIf { it.isNotBlank() } ?: "—",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
