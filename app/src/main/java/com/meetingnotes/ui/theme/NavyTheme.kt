package com.meetingnotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * アプリ全体のカスタム配色(2026-09-12〜)。M3 標準の紫パレット(引数なしの
 * `lightColorScheme()`/`darkColorScheme()`)を廃止し、`primary` 系ロールをネイビーに、
 * 中立面(`background`/`surface`/`surfaceContainer*`/`outline*`)も M3 baseline の紫みを帯びた
 * 既定値から、ニュートラルなグレー系(デザイン案のページ地`--page`/カード地`--card`相当)に置き換える。
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

// ニュートラル面(デザイン案の --page / --card / --card-br / --ink / --muted / --hair 相当)。
private val PageLight = Color(0xFFEFF1F5)
private val CardLight = Color(0xFFFFFFFF)
private val CardBorderLight = Color(0xFFE3E8EE)
private val InkLight = Color(0xFF1B2436)
private val MutedLight = Color(0xFF6B7482)

private val PageDark = Color(0xFF0F1420)
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
    background = PageLight,
    onBackground = InkLight,
    surface = CardLight,
    onSurface = InkLight,
    surfaceVariant = PageLight,
    onSurfaceVariant = MutedLight,
    outline = MutedLight,
    outlineVariant = CardBorderLight,
    surfaceContainerLowest = PageLight,
    surfaceContainerLow = PageLight,
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
    background = PageDark,
    onBackground = InkDark,
    surface = CardDark,
    onSurface = InkDark,
    surfaceVariant = PageDark,
    onSurfaceVariant = MutedDark,
    outline = MutedDark,
    outlineVariant = CardBorderDark,
    surfaceContainerLowest = PageDark,
    surfaceContainerLow = PageDark,
    surfaceContainer = CardDark,
    surfaceContainerHigh = CardDark,
    surfaceContainerHighest = CardBorderDark,
    inverseSurface = InkDark,
    inverseOnSurface = CardDark
)
