package com.meetingnotes.ui.home

import com.meetingnotes.data.local.ClientEntity
import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.model.DealPhase

/** ホーム「動いていない案件」の1行。 */
data class StaleDeal(
    val projectId: Long,
    val projectName: String,
    val clientId: Long,
    val clientName: String,
    val phase: DealPhase?,
    val daysSincePhaseChange: Int
)

/**
 * 進行中(`DealPhase.isActive`)なのに一定日数フェーズが動いていない案件を抽出する純粋関数。
 * フェーズ未設定・成約・失注は対象外。停滞日数の降順。
 */
object StaleDealRules {

    const val STALE_DAYS = 21
    private const val DAY_MS = 86_400_000L

    fun compute(
        projects: List<ClientProjectEntity>,
        clients: List<ClientEntity>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<StaleDeal> {
        val nameById = clients.associate { it.id to it.name }
        return projects.mapNotNull { p ->
            val phase = DealPhase.fromWire(p.phase)
            if (phase?.isActive != true) return@mapNotNull null
            val since = p.phaseChangedAt ?: p.createdAt
            val days = ((nowMillis - since) / DAY_MS).toInt()
            if (days < STALE_DAYS) return@mapNotNull null
            StaleDeal(
                projectId = p.id,
                projectName = p.name,
                clientId = p.clientId,
                clientName = nameById[p.clientId] ?: "(不明なクライアント)",
                phase = phase,
                daysSincePhaseChange = days
            )
        }.sortedByDescending { it.daysSincePhaseChange }
    }
}
