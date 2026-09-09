package com.meetingnotes.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 「分析」タブ・「売上」タブの棒グラフ/横棒の基準色。
 * アプリ全体が M3 紫(primary)で埋もれるのを避けるための専用色(ティール)。
 * `PhaseChartColors` の「見積提示」と同系。ライト/ダークで明度を入れ替える。
 */
object AnalyticsChartColors {
    fun bar(darkTheme: Boolean): Color =
        if (darkTheme) Color(0xFF4FD1C5) else Color(0xFF0D9488)
}
