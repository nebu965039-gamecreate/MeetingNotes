package com.meetingnotes.ui.theme

import androidx.compose.ui.graphics.Color
import com.meetingnotes.data.model.DealPhase

/**
 * 商談フェーズタグ(`DealPhaseChip`)の配色。
 * アプリ全体が M3 の紫系のため、フェーズが埋もれないよう各段階に別系統の色を割り当てる。
 * 意味を持つ状態色なのでテーマに関わらず色相は不変。ライト/ダークで明度だけ入れ替える。
 */
data class PhaseTagColor(val container: Color, val content: Color)

object PhaseTagColors {

    private val lightUnset = PhaseTagColor(Color(0xFFECEAEF), Color(0xFF5B5568))
    private val darkUnset = PhaseTagColor(Color(0xFF3A3742), Color(0xFFC9C4D0))

    private val light = mapOf(
        DealPhase.FIRST_CONTACT to PhaseTagColor(Color(0xFFE2E8F0), Color(0xFF334155)), // スレート
        DealPhase.HEARING to PhaseTagColor(Color(0xFFFEF0C7), Color(0xFF854D0E)),        // 琥珀
        DealPhase.PROPOSAL to PhaseTagColor(Color(0xFFDBEAFE), Color(0xFF1E40AF)),       // 青
        DealPhase.QUOTED to PhaseTagColor(Color(0xFFCCFBF1), Color(0xFF115E59)),         // ティール
        DealPhase.CONSIDERING to PhaseTagColor(Color(0xFFFFEDD5), Color(0xFF9A3412)),    // オレンジ
        DealPhase.WON to PhaseTagColor(Color(0xFFDCFCE7), Color(0xFF166534)),            // 緑
        DealPhase.ON_HOLD to PhaseTagColor(Color(0xFFEDE9E3), Color(0xFF57534E)),        // 温グレー
        DealPhase.LOST to PhaseTagColor(Color(0xFFFFE4E6), Color(0xFF9F1239)),           // ローズ
    )

    private val dark = mapOf(
        DealPhase.FIRST_CONTACT to PhaseTagColor(Color(0xFF33414F), Color(0xFFCBD5E1)),
        DealPhase.HEARING to PhaseTagColor(Color(0xFF4A3A12), Color(0xFFFCE29B)),
        DealPhase.PROPOSAL to PhaseTagColor(Color(0xFF1E355C), Color(0xFFB6D0F5)),
        DealPhase.QUOTED to PhaseTagColor(Color(0xFF12433E), Color(0xFF9DEBDE)),
        DealPhase.CONSIDERING to PhaseTagColor(Color(0xFF4E2A12), Color(0xFFF9C99B)),
        DealPhase.WON to PhaseTagColor(Color(0xFF17402A), Color(0xFF9FE3B8)),
        DealPhase.ON_HOLD to PhaseTagColor(Color(0xFF3B3733), Color(0xFFD8D2CB)),
        DealPhase.LOST to PhaseTagColor(Color(0xFF4C1F2B), Color(0xFFF6AEBD)),
    )

    fun of(phase: DealPhase?, darkTheme: Boolean): PhaseTagColor {
        val table = if (darkTheme) dark else light
        val fallback = if (darkTheme) darkUnset else lightUnset
        return phase?.let { table[it] } ?: fallback
    }
}
