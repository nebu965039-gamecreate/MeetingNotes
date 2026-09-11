package com.meetingnotes.ui.client

import com.meetingnotes.data.local.ClientProjectEntity
import com.meetingnotes.data.model.DealPhase

/**
 * クライアントの「現在の状態」を **案件フェーズ** から決めるロジック(2026-09-11、案件フェーズを正とする)。
 * - 進行中の案件があれば、その中で最もファネルが先のフェーズ
 * - 進行中が無く成約案件があれば `WON`
 * - それも無く失注案件だけなら `LOST`
 * - 案件が1つも無ければ null(= ステータス未確定)
 *
 * 純粋関数。単体テスト対象。
 */
object ClientDealPhaseRules {

    fun of(projects: List<ClientProjectEntity>): DealPhase? {
        if (projects.isEmpty()) return null
        val phases = projects.mapNotNull { DealPhase.fromWire(it.phase) }
        val active = phases.filter { it.isActive }
        if (active.isNotEmpty()) return active.maxByOrNull { it.funnelRank }
        if (phases.any { it == DealPhase.WON }) return DealPhase.WON
        if (phases.any { it == DealPhase.LOST }) return DealPhase.LOST
        return null
    }

    /** クライアントID → 代表フェーズ。案件が無い / フェーズ未設定のクライアントは入らない。 */
    fun byClient(allProjects: List<ClientProjectEntity>): Map<Long, DealPhase> =
        allProjects.groupBy { it.clientId }
            .mapNotNull { (clientId, list) -> of(list)?.let { clientId to it } }
            .toMap()
}
