package com.meetingnotes.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 「分析」タブ・「売上」タブの棒グラフ/横棒の色。
 * アプリ全体が M3 紫(primary)で埋もれるのを避けるための専用色。
 * タブ(と一部カード)ごとに色相を変え、行き来したときに色で区別できるようにする。
 * ライト/ダークで明度を入れ替える。
 */
object AnalyticsChartColors {
    /** 活動タブ・売上タブ = ティール。 */
    fun activity(darkTheme: Boolean): Color =
        if (darkTheme) Color(0xFF4FD1C5) else Color(0xFF0D9488)

    /** 顧客タブ = インディゴ。 */
    fun customers(darkTheme: Boolean): Color =
        if (darkTheme) Color(0xFFA5B4FC) else Color(0xFF4F46E5)

    /** フォロータブ「ToDo の消化」= アンバー。 */
    fun followTodo(darkTheme: Boolean): Color =
        if (darkTheme) Color(0xFFE5C37D) else Color(0xFFC2740C)

    /** フォロータブ「フォローアップ」= バイオレット。 */
    fun followEmail(darkTheme: Boolean): Color =
        if (darkTheme) Color(0xFFC4B5FD) else Color(0xFF7C3AED)

    /**
     * 棒グラフ本体の色(活動・顧客タブの月次推移、売上タブの月次成約額)= 青。
     * 「棒グラフの色は青色にしてほしい」というフィードバックを受けて統一(2026-09-17。旧: 活動と同じティール)。
     */
    fun bar(darkTheme: Boolean): Color =
        if (darkTheme) Color(0xFF82B1FF) else Color(0xFF1565C0)
}
