package com.meetingnotes.ui.theme

import androidx.compose.ui.graphics.Color
import com.meetingnotes.data.model.DealPhase

/**
 * 商談フェーズタグ(`DealPhaseChip`)の配色。
 * アプリ全体が M3 の紫系のため、フェーズが埋もれないよう各段階に別系統の色を割り当てる。
 * 意味を持つ状態色なのでテーマに関わらず不変(淡い塗り + 濃い文字、ダークでも同配色)。
 */
data class PhaseTagColor(val container: Color, val content: Color)

object PhaseTagColors {
    private val Unset = PhaseTagColor(Color(0xFFECEAEF), Color(0xFF5B5568))

    private val byPhase = mapOf(
        DealPhase.FIRST_CONTACT to PhaseTagColor(Color(0xFFE2E8F0), Color(0xFF334155)), // スレート
        DealPhase.HEARING to PhaseTagColor(Color(0xFFFEF0C7), Color(0xFF854D0E)),        // 琥珀
        DealPhase.PROPOSAL to PhaseTagColor(Color(0xFFDBEAFE), Color(0xFF1E40AF)),       // 青
        DealPhase.QUOTED to PhaseTagColor(Color(0xFFCCFBF1), Color(0xFF115E59)),         // ティール
        DealPhase.CONSIDERING to PhaseTagColor(Color(0xFFFFEDD5), Color(0xFF9A3412)),    // オレンジ
        DealPhase.WON to PhaseTagColor(Color(0xFFDCFCE7), Color(0xFF166534)),            // 緑
        DealPhase.ON_HOLD to PhaseTagColor(Color(0xFFEDE9E3), Color(0xFF57534E)),        // 温かいグレー
        DealPhase.LOST to PhaseTagColor(Color(0xFFFFE4E6), Color(0xFF9F1239)),           // ローズ
    )

    fun of(phase: DealPhase?): PhaseTagColor = phase?.let { byPhase[it] } ?: Unset
}
