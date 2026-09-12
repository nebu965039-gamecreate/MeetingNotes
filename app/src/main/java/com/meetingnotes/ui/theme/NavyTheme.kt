package com.meetingnotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * アプリ全体のカスタム配色(2026-09-12〜)。M3 標準の紫パレット(引数なしの
 * `lightColorScheme()`/`darkColorScheme()`)を廃止し、`primary` 系ロールをネイビーに、
 * 中立面(`background`/`surface`/`surfaceContainer*`/`outline*`)も M3 baseline の紫みを帯びた
 * 既定値から置き換える。
 *
 * ページ地(`background`/`surfaceVariant`/`surfaceContainerLowest`/`surfaceContainerLow`)は
 * `FolderTabDefaults.sheetColor`(ToDo/分析/クライアント詳細/予定表で使う淡いティール)と**同じ色**を
 * アプリ全体の共通背景として採用(2026-09-13〜。全画面で統一したいというフィードバックを受け、
 * 予定表だけの個別対応からテーマ全体の既定値に格上げ)。カード地(`surface`/`surfaceContainer`/
 * `surfaceContainerHigh`)は白のまま。
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

// ページ地(= FolderTabDefaults.sheetColor と同じ淡いティール)とカード地(白)。
private val SheetLight = Color(0xFFE3F0EE)
private val CardLight = Color(0xFFFFFFFF)
private val CardBorderLight = Color(0xFFE3E8EE)
private val InkLight = Color(0xFF1B2436)
private val MutedLight = Color(0xFF6B7482)

private val SheetDark = Color(0xFF17302D)
private val CardDark = Color(0xFF1A2233)
private val CardBorderDark = Color(0xFF2B3550)
private val InkDark = Color(0xFFE7EBF3)
private val MutedDark = Color(0xFF9AA4B4)

fun navyLightColorScheme(): ColorScheme = lightColorScheme(
    primary = NavyLight,
    onPrimary = OnNavyLight,
    primaryContainer = NavyContainerLight,
    onPrimaryContainer = OnNavyContainerLight,
    inversePrimary = NavyDark,
    surfaceTint = NavyLight,
    background = SheetLight,
    onBackground = InkLight,
    surface = CardLight,
    onSurface = InkLight,
    surfaceVariant = SheetLight,
    onSurfaceVariant = MutedLight,
    outline = MutedLight,
    outlineVariant = CardBorderLight,
    surfaceContainerLowest = SheetLight,
    surfaceContainerLow = SheetLight,
    surfaceContainer = CardLight,
    surfaceContainerHigh = CardLight,
    surfaceContainerHighest = CardBorderLight,
    inverseSurface = InkLight,
    inverseOnSurface = CardLight
)

fun navyDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = NavyDark,
    onPrimary = OnNavyDark,
    primaryContainer = NavyContainerDark,
    onPrimaryContainer = OnNavyContainerDark,
    inversePrimary = NavyLight,
    surfaceTint = NavyDark,
    background = SheetDark,
    onBackground = InkDark,
    surface = CardDark,
    onSurface = InkDark,
    surfaceVariant = SheetDark,
    onSurfaceVariant = MutedDark,
    outline = MutedDark,
    outlineVariant = CardBorderDark,
    surfaceContainerLowest = SheetDark,
    surfaceContainerLow = SheetDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardDark,
    surfaceContainerHighest = CardBorderDark,
    inverseSurface = InkDark,
    inverseOnSurface = CardDark
)
