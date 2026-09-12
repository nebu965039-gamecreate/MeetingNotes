package com.meetingnotes.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * ヘッダー(`TabTopBar`/`ClientDetailScreen`)・フォルダ型タブのストリップ地(`FolderTabRow`)・
 * ホームのダッシュボードカードに使う「常に濃いネイビーの帯」(2026-09-12〜)。
 * M3 の `primary`(ライト/ダークで明度反転する ColorScheme ロール)とは別物で、
 * ライト/ダーク共通の単色([CreateActionAmber] と同じ設計方針)。
 */
val BrandNavy = Color(0xFF1B2C4B)
val OnBrandNavy = Color(0xFFEEF2F8)
val OnBrandNavyDim = Color(0xFFA9B6CD)

/** ネイビー地に乗せる、ホームボタンの円のような「薄い白丸」の背景。 */
val OnBrandNavyMuted = Color(0x1FFFFFFF)
