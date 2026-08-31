package com.meetingnotes.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.meetingnotes.BuildConfig

/**
 * 無料ユーザー向けバナー広告(仕様書7章)。ホーム画面(クライアント一覧)・要約結果画面に配置し、
 * 録音中・要約中の画面には表示しない。
 * 画面幅に高さを最適化する「アダプティブバナー」を使用(固定サイズの標準バナーよりeCPMが
 * 平均15〜20%高いとGoogleが案内しているため)。
 * 広告ユニットIDは BuildConfig 経由(release=本番 / debug=Google公式テストID)。
 */
@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, screenWidthDp))
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() }
    )
}
