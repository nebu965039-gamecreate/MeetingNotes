package com.meetingnotes.ui.theme

import androidx.compose.ui.graphics.Color
import com.meetingnotes.data.model.DealPhase

/**
 * ホームのダッシュボード(ドーナツ)・「進行中のフェーズ」ファネルで使うチャート塗り色。
 * `PhaseTagColors` と同系の色相で、面グラフ用に彩度を上げてある。ライト/ダークで明度を入れ替え。
 */
object PhaseChartColors {

    private val light: Map<DealPhase, Color> = mapOf(
        DealPhase.FIRST_CONTACT to Color(0xFF64748B),
        DealPhase.HEARING to Color(0xFFD97706),
        DealPhase.PROPOSAL to Color(0xFF2563EB),
        DealPhase.QUOTED to Color(0xFF0D9488),
        DealPhase.CONSIDERING to Color(0xFFEA580C),
        DealPhase.WON to Color(0xFF16A34A),
        DealPhase.ON_HOLD to Color(0xFF78716C),
        DealPhase.LOST to Color(0xFFE11D48),
    )

    private val dark: Map<DealPhase, Color> = mapOf(
        DealPhase.FIRST_CONTACT to Color(0xFF94A3B8),
        DealPhase.HEARING to Color(0xFFF0B64D),
        DealPhase.PROPOSAL to Color(0xFF6EA8FE),
        DealPhase.QUOTED to Color(0xFF4FD1C5),
        DealPhase.CONSIDERING to Color(0xFFFB923C),
        DealPhase.WON to Color(0xFF4ADE80),
        DealPhase.ON_HOLD to Color(0xFFA8A29E),
        DealPhase.LOST to Color(0xFFFB7185),
    )

    fun of(phase: DealPhase, darkTheme: Boolean): Color =
        (if (darkTheme) dark else light)[phase] ?: if (darkTheme) Color(0xFF9CA3AF) else Color(0xFF6B7280)
}
