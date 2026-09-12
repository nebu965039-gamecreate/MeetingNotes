package com.meetingnotes.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ToDo 行(`ui/common/TodoRow.kt`)の期限表示の強調色(2026-09-14)。
 * 期限切れは M3 の `error`(テーマ追従)をそのまま使うため、ここでは
 * 「3日以内」用の黄色だけを定義する。意味を持つ状態色なので色相は不変、ライト/ダークで明度だけ入れ替える。
 */
object TodoDueSoonColor {
    private val light = Color(0xFF9A6700) // 白背景での可読性を優先した黄色寄りのダークアンバー
    private val dark = Color(0xFFE3B341)
    fun of(darkTheme: Boolean): Color = if (darkTheme) dark else light
}
