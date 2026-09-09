package com.meetingnotes.ui.pipeline

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.meetingnotes.ads.BannerAdView
import com.meetingnotes.data.MeetingRepository
import com.meetingnotes.data.model.DealPhase
import com.meetingnotes.ui.common.DealPhaseChip
import com.meetingnotes.ui.common.DealPhasePickerDialog
import com.meetingnotes.util.formatMoney

/** 進捗ファネル順の「隣」を出すための並び(ON_HOLD は矢印移動の対象外)。 */
private val FUNNEL = listOf(
    DealPhase.FIRST_CONTACT, DealPhase.HEARING, DealPhase.PROPOSAL,
    DealPhase.QUOTED, DealPhase.CONSIDERING
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipelineScreen(repository: MeetingRepository, onBack: () -> Unit, onOpenClient: (Long) -> Unit) {
    val viewModel: PipelineViewModel = viewModel(factory = PipelineViewModel.factory(repository))
    val columns by viewModel.columns.collectAsState()
    var phasePickFor by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("パイプライン") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        },
        bottomBar = { BannerAdView(Modifier.navigationBarsPadding()) }
    ) { padding ->
        val empty = columns.all { it.cards.isEmpty() }
        if (empty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "進行中の案件がありません。クライアント画面の「案件を管理」から案件を追加し、進捗フェーズを設定すると、ここに並びます。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                columns.forEach { col ->
                    PhaseColumn(
                        column = col,
                        onOpenClient = onOpenClient,
                        onMove = { projectId, phase -> viewModel.movePhase(projectId, phase) },
                        onPick = { phasePickFor = it }
                    )
                }
            }
        }
    }

    phasePickFor?.let { projectId ->
        val card = columns.flatMap { it.cards }.firstOrNull { it.project.id == projectId }
        DealPhasePickerDialog(
            current = card?.let { DealPhase.fromWire(it.project.phase) },
            onDismiss = { phasePickFor = null },
            onSelect = {
                viewModel.movePhase(projectId, it)
                phasePickFor = null
            }
        )
    }
}

@Composable
private fun PhaseColumn(
    column: PipelineColumn,
    onOpenClient: (Long) -> Unit,
    onMove: (projectId: Long, phase: DealPhase) -> Unit,
    onPick: (projectId: Long) -> Unit
) {
    Column(
        modifier = Modifier
            .width(268.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DealPhaseChip(phase = column.phase)
            Spacer(Modifier.width(8.dp))
            Text(
                "${column.cards.size}件",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(column.cards, key = { it.project.id }) { card ->
                PipelineCardView(card, onOpenClient, onMove, onPick)
            }
        }
    }
}

@Composable
private fun PipelineCardView(
    card: PipelineCard,
    onOpenClient: (Long) -> Unit,
    onMove: (projectId: Long, phase: DealPhase) -> Unit,
    onPick: (projectId: Long) -> Unit
) {
    val phase = DealPhase.fromWire(card.project.phase)
    val idx = FUNNEL.indexOf(phase)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                card.project.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                card.clientName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            val prob = card.project.probability ?: phase?.defaultProbability
            val meta = buildList {
                card.project.estimatedAmount?.let { add(formatMoney(it, card.project.currency)) }
                if (prob != null) add("確度 ${prob}%")
            }.joinToString(" ・ ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (idx > 0) onMove(card.project.id, FUNNEL[idx - 1]) },
                    enabled = idx > 0,
                    modifier = Modifier.size(32.dp)
                ) { Icon(Icons.Filled.ChevronLeft, contentDescription = "前のフェーズへ") }
                IconButton(
                    onClick = { if (idx in 0 until FUNNEL.lastIndex) onMove(card.project.id, FUNNEL[idx + 1]) },
                    enabled = idx in 0 until FUNNEL.lastIndex,
                    modifier = Modifier.size(32.dp)
                ) { Icon(Icons.Filled.ChevronRight, contentDescription = "次のフェーズへ") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onPick(card.project.id) }) {
                    Text("変更", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onOpenClient(card.clientId) }) {
                    Text("開く", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
