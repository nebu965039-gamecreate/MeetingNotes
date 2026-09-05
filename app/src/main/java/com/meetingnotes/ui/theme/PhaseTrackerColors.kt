package com.meetingnotes.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ホーム画面「進行中のフェーズ」トラッカーの状態色(黄→緑)。
 * 意味を持つ状態色のためテーマに関わらず不変(ダークテーマでも同じ配色)。
 */
data class PhaseTrackerColor(val dot: Color, val badgeBackground: Color, val badgeText: Color)

object PhaseTrackerColors {
    val Hearing = PhaseTrackerColor(Color(0xFFF5C518), Color(0xFFFCF3D0), Color(0xFF7A5C00))
    val Proposal = PhaseTrackerColor(Color(0xFFD4E157), Color(0xFFEFF6D6), Color(0xFF56660F))
    val Quoted = PhaseTrackerColor(Color(0xFF8BC34A), Color(0xFFE3F2DA), Color(0xFF33691E))
    val Considering = PhaseTrackerColor(Color(0xFF16A34A), Color(0xFFDCF0E3), Color(0xFF1B5E20))
}
