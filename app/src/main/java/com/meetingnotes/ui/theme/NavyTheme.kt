package com.meetingnotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * アプリ全体のカスタム配色(2026-09-12〜)。M3 標準の紫パレット(引数なしの
 * `lightColorScheme()`/`darkColorScheme()`)を廃止し、`primary` 系ロールだけをネイビーに置き換える。
 *
 * `secondary`/`tertiary`/`error` 系は M3 baseline のまま(CLAUDE.md の「紫を使う場所を絞る」方針の
 * 続き。`DealPhaseChip` 以外のチップ等、一部に紫が残ることは既存方針どおり許容)。
 * `surfaceTint` も primary に揃え、`tonalElevation` を使うカードに紫みが乗らないようにする。
 */
private val NavyLight = Color(0xFF1B2C4B)
private val OnNavyLight = Color(0xFFFFFFFF)
private val NavyContainerLight = Color(0xFFD9E2F5)
private val OnNavyContainerLight = Color(0xFF0E1A30)

private val NavyDark = Color(0xFF8AB4F8)
private val OnNavyDark = Color(0xFF0E1A30)
private val NavyContainerDark = Color(0xFF223862)
private val OnNavyContainerDark = Color(0xFFD9E2F5)

fun navyLightColorScheme(): ColorScheme = lightColorScheme(
    primary = NavyLight,
    onPrimary = OnNavyLight,
    primaryContainer = NavyContainerLight,
    onPrimaryContainer = OnNavyContainerLight,
    inversePrimary = NavyDark,
    surfaceTint = NavyLight
)

fun navyDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = NavyDark,
    onPrimary = OnNavyDark,
    primaryContainer = NavyContainerDark,
    onPrimaryContainer = OnNavyContainerDark,
    inversePrimary = NavyLight,
    surfaceTint = NavyDark
)
